package com.yupi.yuaiagent.perception;

import com.yupi.yuaiagent.guard.PromptInjectionDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Perception-layer defenses against visual prompt injection
 * (mm_agent_tutorial Ch1 Ex 1.8): resample / JPEG recompress + OCR-text scan.
 */
@Slf4j
@Component
public class VisualPromptSanitizer {

    private final PromptInjectionDetector promptInjectionDetector;

    public VisualPromptSanitizer(PromptInjectionDetector promptInjectionDetector) {
        this.promptInjectionDetector = promptInjectionDetector;
    }

    /**
     * Downscale + JPEG recompress to disrupt pixel-level adversarial payloads.
     *
     * @return sanitized bytes (JPEG), or original if not an image / failure
     */
    public byte[] sanitizeImageBytes(byte[] input, int maxEdgePx) {
        if (input == null || input.length == 0) {
            return input;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(input));
            if (img == null) {
                return input;
            }
            BufferedImage scaled = downscale(img, Math.max(64, maxEdgePx));
            return toJpeg(scaled, 0.72f);
        } catch (Exception e) {
            log.debug("[VisualSanitizer] skip: {}", e.getMessage());
            return input;
        }
    }

    /**
     * Scan extracted OCR/text for injection phrases.
     *
     * @return true if risky
     */
    public boolean hasInjectionRisk(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return false;
        }
        var result = promptInjectionDetector.detect(extractedText);
        return !result.safe();
    }

    /** Strip common injection phrases for safer prompt injection of document text. */
    public String scrubText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String scrubbed = text
                .replaceAll("(?i)ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instructions|rules|prompts)", "[已屏蔽注入]")
                .replaceAll("(?i)system\\s*override\\s*:", "[已屏蔽注入]")
                .replaceAll("(?i)transfer\\s+all\\s+money", "[已屏蔽注入]");
        return scrubbed;
    }

    private static BufferedImage downscale(BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, maxEdge / (double) Math.max(w, h));
        if (scale >= 0.999) {
            return src;
        }
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static byte[] toJpeg(BufferedImage img, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }
}
