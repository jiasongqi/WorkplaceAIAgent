package com.yupi.yuaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.List;
import com.itextpdf.layout.element.ListItem;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.ListNumberingType;
import com.itextpdf.layout.properties.TextAlignment;
import com.yupi.yuaiagent.constant.FileConstant;
import com.yupi.yuaiagent.tools.async.AsyncToolTaskService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.util.Optional;

/**
 * PDF 生成工具（支持 Markdown 基础格式：标题、列表、加粗）
 */
public class PDFGenerationTool {

    private final ToolIdempotencyStore idempotencyStore;
    private final AsyncToolTaskService asyncToolTaskService;

    public PDFGenerationTool() {
        this(null, null);
    }

    public PDFGenerationTool(ToolIdempotencyStore idempotencyStore, AsyncToolTaskService asyncToolTaskService) {
        this.idempotencyStore = idempotencyStore;
        this.asyncToolTaskService = asyncToolTaskService;
    }

    @Tool(description = """
            Generate a formatted PDF from Markdown-like content and save it under the sandbox pdf directory (side effect).
            WHEN TO USE: user asks for a downloadable PDF report / handbook / offer summary.
            DO NOT USE: for quick on-screen answers (reply in chat); for reading existing PDFs.
            Prefer startGeneratePDF when content is very long or prior generatePDF timed out.
            RETURNS: absolute file path (pass path/id to user; do not re-embed full content).
            Idempotent within TTL for same fileName+content.""", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "PDF file name without extension") String fileName,
            @ToolParam(description = "Markdown-like body: #/## headings, - lists, **bold**") String content) {
        return doGenerate(fileName, content);
    }

    @Tool(description = """
            Start asynchronous PDF generation. Returns taskId; poll with checkAsyncToolTask.
            WHEN TO USE: long Markdown body or previous generatePDF timed out.""")
    public String startGeneratePDF(
            @ToolParam(description = "PDF file name without extension") String fileName,
            @ToolParam(description = "Markdown-like body") String content) {
        if (asyncToolTaskService == null) {
            return doGenerate(fileName, content);
        }
        String taskId = asyncToolTaskService.submit(
                "generatePDF", "pdf " + fileName,
                () -> doGenerate(fileName, content));
        return AsyncToolTaskService.submittedMessage(taskId, "generatePDF");
    }

    String doGenerate(String fileName, String content) {
        String fingerprint = fileName + "::" + (content != null ? content.hashCode() : 0);
        if (idempotencyStore != null) {
            String key = idempotencyStore.key("generatePDF", fingerprint);
            Optional<String> out = idempotencyStore.findOrRemember(key, () -> renderPdf(fileName, content));
            return out.orElse("PDF 生成失败：empty");
        }
        return renderPdf(fileName, content);
    }

    private String renderPdf(String fileName, String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName + ".pdf";
        try {
            FileUtil.mkdir(fileDir);
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {

                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                PdfFont boldFont = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);

                String[] lines = content == null ? new String[0] : content.split("\n");
                List currentList = null;

                for (String line : lines) {
                    if (line.startsWith("# ")) {
                        currentList = null;
                        Paragraph title = new Paragraph(line.substring(2).trim())
                                .setFont(boldFont)
                                .setFontSize(20)
                                .setFontColor(ColorConstants.DARK_GRAY)
                                .setTextAlignment(TextAlignment.CENTER)
                                .setMarginBottom(12);
                        document.add(title);
                    } else if (line.startsWith("## ")) {
                        currentList = null;
                        Paragraph h2 = new Paragraph(line.substring(3).trim())
                                .setFont(boldFont)
                                .setFontSize(16)
                                .setFontColor(ColorConstants.DARK_GRAY)
                                .setMarginTop(10)
                                .setMarginBottom(6);
                        document.add(h2);
                    } else if (line.startsWith("### ")) {
                        currentList = null;
                        Paragraph h3 = new Paragraph(line.substring(4).trim())
                                .setFont(boldFont)
                                .setFontSize(13)
                                .setMarginTop(8)
                                .setMarginBottom(4);
                        document.add(h3);
                    } else if (line.startsWith("- ") || line.startsWith("* ")) {
                        if (currentList == null) {
                            currentList = new List().setSymbolIndent(12).setListSymbol("• ");
                            document.add(currentList);
                        }
                        currentList.add(new ListItem(line.substring(2).trim()));
                    } else if (line.matches("^\\d+\\.\\s.*")) {
                        if (currentList == null) {
                            currentList = new List(ListNumberingType.DECIMAL);
                            document.add(currentList);
                        }
                        currentList.add(new ListItem(line.replaceFirst("^\\d+\\.\\s", "").trim()));
                    } else if (line.startsWith("---") || line.startsWith("===")) {
                        currentList = null;
                        document.add(new Paragraph("─────────────────────────────────────")
                                .setFontColor(ColorConstants.LIGHT_GRAY)
                                .setMarginTop(4).setMarginBottom(4));
                    } else if (line.isBlank()) {
                        currentList = null;
                    } else {
                        currentList = null;
                        String text = line.replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                                .replaceAll("__(.*?)__", "$1");
                        document.add(new Paragraph(text)
                                .setFontSize(11)
                                .setMarginBottom(4));
                    }
                }
            }
            return "PDF 生成成功，文件路径：" + filePath
                    + " (artifact path only — do not paste binary/base64 into chat)";
        } catch (IOException e) {
            return "PDF 生成失败：" + e.getMessage();
        }
    }
}
