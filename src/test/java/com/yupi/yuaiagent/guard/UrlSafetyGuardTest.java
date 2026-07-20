package com.yupi.yuaiagent.guard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlSafetyGuardTest {

    @Test
    void rejectsNullOrBlankUrl() {
        assertFalse(UrlSafetyGuard.isSafeUrl(null));
        assertFalse(UrlSafetyGuard.isSafeUrl(""));
        assertFalse(UrlSafetyGuard.isSafeUrl("   "));
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertFalse(UrlSafetyGuard.isSafeUrl("file:///etc/passwd"));
        assertFalse(UrlSafetyGuard.isSafeUrl("ftp://example.com/file"));
        assertFalse(UrlSafetyGuard.isSafeUrl("gopher://example.com"));
        assertFalse(UrlSafetyGuard.isSafeUrl("not a url"));
    }

    @Test
    void rejectsLoopbackAndLocalhost() {
        assertFalse(UrlSafetyGuard.isSafeUrl("http://localhost/"));
        assertFalse(UrlSafetyGuard.isSafeUrl("http://127.0.0.1/"));
        assertFalse(UrlSafetyGuard.isSafeUrl("https://127.0.0.1:8080/admin"));
    }

    @Test
    void rejectsPrivateAndLinkLocalAddresses() {
        assertFalse(UrlSafetyGuard.isSafeUrl("http://10.0.0.1/"));
        assertFalse(UrlSafetyGuard.isSafeUrl("http://192.168.1.1/"));
        assertFalse(UrlSafetyGuard.isSafeUrl("http://172.16.0.1/"));
        // Cloud metadata endpoint (AWS/GCP link-local)
        assertFalse(UrlSafetyGuard.isSafeUrl("http://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void rejectsMetadataAndInternalHostnames() {
        assertFalse(UrlSafetyGuard.isSafeUrl("http://metadata.google.internal/"));
        assertFalse(UrlSafetyGuard.isSafeUrl("http://foo.internal/"));
        assertFalse(UrlSafetyGuard.isSafeUrl("http://foo.local/"));
    }

    @Test
    void rejectsCgnatRange() {
        assertFalse(UrlSafetyGuard.isSafeUrl("http://100.64.0.1/"));
    }

    @Test
    void acceptsPublicHttpsUrl() {
        assertTrue(UrlSafetyGuard.isSafeUrl("https://www.example.com/page"));
    }

    @Test
    void acceptsPublicHttpUrl() {
        assertTrue(UrlSafetyGuard.isSafeUrl("http://www.example.com/"));
    }

    @Test
    void rejectMessageIsNonEmpty() {
        assertTrue(UrlSafetyGuard.rejectMessage() != null && !UrlSafetyGuard.rejectMessage().isBlank());
    }

    @Test
    void isBlockedAddressDetectsLoopbackAndPrivate() throws Exception {
        assertTrue(UrlSafetyGuard.isBlockedAddress(java.net.InetAddress.getByName("127.0.0.1")));
        assertTrue(UrlSafetyGuard.isBlockedAddress(java.net.InetAddress.getByName("10.1.2.3")));
        assertTrue(UrlSafetyGuard.isBlockedAddress(java.net.InetAddress.getByName("169.254.1.1")));
        assertFalse(UrlSafetyGuard.isBlockedAddress(java.net.InetAddress.getByName("8.8.8.8")));
    }
}
