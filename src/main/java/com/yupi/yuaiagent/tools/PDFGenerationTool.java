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
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.ListNumberingType;
import com.yupi.yuaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;

/**
 * PDF 生成工具（支持 Markdown 基础格式：标题、列表、加粗）
 */
public class PDFGenerationTool {

    @Tool(description = "Generate a formatted PDF file from Markdown-like content", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the PDF file to generate (without extension)") String fileName,
            @ToolParam(description = "Content in Markdown format to include in the PDF") String content) {
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

                // 解析 Markdown 内容并渲染
                String[] lines = content.split("\n");
                List currentList = null;

                for (String line : lines) {
                    if (line.startsWith("# ")) {
                        // H1 标题
                        currentList = null;
                        Paragraph title = new Paragraph(line.substring(2).trim())
                                .setFont(boldFont)
                                .setFontSize(20)
                                .setFontColor(ColorConstants.DARK_GRAY)
                                .setTextAlignment(TextAlignment.CENTER)
                                .setMarginBottom(12);
                        document.add(title);
                    } else if (line.startsWith("## ")) {
                        // H2 标题
                        currentList = null;
                        Paragraph h2 = new Paragraph(line.substring(3).trim())
                                .setFont(boldFont)
                                .setFontSize(16)
                                .setFontColor(ColorConstants.DARK_GRAY)
                                .setMarginTop(10)
                                .setMarginBottom(6);
                        document.add(h2);
                    } else if (line.startsWith("### ")) {
                        // H3 标题
                        currentList = null;
                        Paragraph h3 = new Paragraph(line.substring(4).trim())
                                .setFont(boldFont)
                                .setFontSize(13)
                                .setMarginTop(8)
                                .setMarginBottom(4);
                        document.add(h3);
                    } else if (line.startsWith("- ") || line.startsWith("* ")) {
                        // 无序列表
                        if (currentList == null) {
                            currentList = new List().setSymbolIndent(12).setListSymbol("• ");
                            document.add(currentList);
                        }
                        currentList.add(new ListItem(line.substring(2).trim()));
                    } else if (line.matches("^\\d+\\.\\s.*")) {
                        // 有序列表
                        if (currentList == null) {
                            currentList = new List(ListNumberingType.DECIMAL);
                            document.add(currentList);
                        }
                        currentList.add(new ListItem(line.replaceFirst("^\\d+\\.\\s", "").trim()));
                    } else if (line.startsWith("---") || line.startsWith("===")) {
                        // 分隔线
                        currentList = null;
                        document.add(new Paragraph("─────────────────────────────────────")
                                .setFontColor(ColorConstants.LIGHT_GRAY)
                                .setMarginTop(4).setMarginBottom(4));
                    } else if (line.isBlank()) {
                        currentList = null;
                    } else {
                        // 普通段落（处理 **加粗** 标记，简单去除标记符号）
                        currentList = null;
                        String text = line.replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                                .replaceAll("__(.*?)__", "$1");
                        document.add(new Paragraph(text)
                                .setFontSize(11)
                                .setMarginBottom(4));
                    }
                }
            }
            return "PDF 生成成功，文件路径：" + filePath;
        } catch (IOException e) {
            return "PDF 生成失败：" + e.getMessage();
        }
    }
}
