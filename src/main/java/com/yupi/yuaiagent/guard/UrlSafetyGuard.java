package com.yupi.yuaiagent.guard;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Shared SSRF guard for any tool that fetches remote URLs.
 * Blocks private/link-local/metadata endpoints and non-http(s) schemes.
 */
public final class UrlSafetyGuard {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "metadata.google.internal",
            "metadata.google.com",
            "instance-data"
    );

    private UrlSafetyGuard() {}

    public static boolean isSafeUrl(String urlStr) {
        if (urlStr == null || urlStr.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(urlStr.trim());
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            String schemeLower = scheme.toLowerCase(Locale.ROOT);
            if (!schemeLower.equals("http") && !schemeLower.equals("https")) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return false;
            }
            String hostLower = host.toLowerCase(Locale.ROOT);
            if (BLOCKED_HOSTS.contains(hostLower) || hostLower.endsWith(".internal")
                    || hostLower.endsWith(".local")) {
                return false;
            }
            // Literal IP checks + DNS resolution
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isBlockedAddress(addr)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String rejectMessage() {
        return "Error: URL is not allowed (internal/private/metadata addresses are blocked)";
    }

    static boolean isBlockedAddress(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = addr.getAddress();
        // IPv4 extras: 169.254.0.0/16 (already link-local), 100.64.0.0/10 CGNAT, 0.0.0.0/8
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            if (b0 == 0) return true;
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return true; // CGNAT
            if (b0 == 169 && b1 == 254) return true; // AWS/GCP metadata often via this
        }
        // IPv6 unique local fc00::/7
        if (bytes.length == 16) {
            int b0 = bytes[0] & 0xFF;
            if ((b0 & 0xFE) == 0xFC) return true;
        }
        return false;
    }
}
