package com.yupi.yuaiagent.perception;



import lombok.extern.slf4j.Slf4j;

import org.apache.pdfbox.Loader;

import org.apache.pdfbox.pdmodel.PDDocument;

import org.apache.pdfbox.text.PDFTextStripper;

import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;

import org.springframework.web.multipart.MultipartFile;



import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.util.LinkedHashMap;

import java.util.Locale;

import java.util.Map;



/**

 * Perception preprocessor: document bytes → semantic text + light structure.

 * Prefer cheap text extraction before any VLM (budget awareness).

 */

@Slf4j

@Service

public class DocumentPerceptionService {



    private final VisualPromptSanitizer visualPromptSanitizer;

    private final ResumeOfferStructurer resumeOfferStructurer;

    private final LongDocumentSummarizer longDocumentSummarizer;

    private final ImageCaptionService imageCaptionService;



    public DocumentPerceptionService(VisualPromptSanitizer visualPromptSanitizer,

                                     ResumeOfferStructurer resumeOfferStructurer,

                                     LongDocumentSummarizer longDocumentSummarizer,

                                     ImageCaptionService imageCaptionService) {

        this.visualPromptSanitizer = visualPromptSanitizer;

        this.resumeOfferStructurer = resumeOfferStructurer;

        this.longDocumentSummarizer = longDocumentSummarizer;

        this.imageCaptionService = imageCaptionService;

    }



    public PerceptionResult perceive(MultipartFile file, String hint) throws IOException {

        if (file == null || file.isEmpty()) {

            throw new IllegalArgumentException("文件不能为空");

        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";

        byte[] bytes = file.getBytes();

        return perceive(bytes, filename, hint);

    }



    public PerceptionResult perceive(byte[] bytes, String filename, String hint) throws IOException {

        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);

        String sourceType;

        String rawText;

        String notes = "";



        if (lower.endsWith(".pdf")) {

            sourceType = "pdf";

            rawText = extractPdfText(bytes);

            if (!StringUtils.hasText(rawText)) {

                notes = "PDF 无文字层（可能是扫描件）。当前未接 OCR；请上传可选中文本的 PDF，或粘贴正文。";

            }

        } else if (lower.endsWith(".docx")) {

            sourceType = "docx";

            rawText = extractDocxText(bytes);

            if (!StringUtils.hasText(rawText)) {

                notes = "Word 文档未提取到文字，请检查文件或另存为 .txt / PDF。";

            }

        } else if (lower.endsWith(".doc")) {

            sourceType = "doc";

            rawText = "";

            notes = "暂不支持旧版 .doc，请另存为 .docx / PDF / txt。";

        } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")

                || lower.endsWith(".webp") || lower.endsWith(".gif")) {

            sourceType = "image";

            visualPromptSanitizer.sanitizeImageBytes(bytes, 1280);

            rawText = "";

            notes = "图片已做重采样/压缩净化；已生成 caption 供检索。";

        } else if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")) {

            sourceType = "text";

            rawText = new String(bytes, StandardCharsets.UTF_8);

        } else {

            sourceType = "binary";

            rawText = new String(bytes, StandardCharsets.UTF_8);

            if (rawText.indexOf('\0') >= 0) {

                rawText = "";

                notes = "无法识别的二进制格式，未提取文本。";

            }

        }



        boolean risk = visualPromptSanitizer.hasInjectionRisk(rawText);

        String scrubbed = visualPromptSanitizer.scrubText(rawText);

        scrubbed = longDocumentSummarizer.summarizeIfNeeded(scrubbed);



        Map<String, String> fields = new LinkedHashMap<>(resumeOfferStructurer.structure(scrubbed, hint));

        if ("image".equals(sourceType)) {

            fields.put("imageCaption", imageCaptionService.caption(bytes, filename, hint));

        }



        double confidence = computeConfidence(sourceType, scrubbed, fields, notes);

        return new PerceptionResult(sourceType, filename, scrubbed, fields, confidence, notes, risk);

    }



    private static String extractPdfText(byte[] bytes) throws IOException {

        try (PDDocument doc = Loader.loadPDF(bytes)) {

            PDFTextStripper stripper = new PDFTextStripper();

            stripper.setSortByPosition(true);

            String text = stripper.getText(doc);

            return text != null ? text.trim() : "";

        }

    }



    private static String extractDocxText(byte[] bytes) throws IOException {

        try (var in = new java.io.ByteArrayInputStream(bytes);

             var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(in)) {

            var extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc);

            String text = extractor.getText();

            return text != null ? text.trim() : "";

        }

    }



    private static double computeConfidence(String sourceType, String text,

                                            Map<String, String> fields, String notes) {

        if (!StringUtils.hasText(text) && !"image".equals(sourceType)) {

            return "image".equals(sourceType) ? 0.15 : 0.2;

        }

        if ("image".equals(sourceType) && fields.containsKey("imageCaption")) {

            return 0.45;

        }

        double base = Math.min(0.9, 0.4 + (text != null ? text.length() : 0) / 4000.0);

        if (!fields.isEmpty()) {

            base = Math.min(0.95, base + 0.1 * fields.size());

        }

        if (notes != null && notes.contains("扫描件")) {

            base = Math.min(base, 0.35);

        }

        return Math.round(base * 100.0) / 100.0;

    }

}


