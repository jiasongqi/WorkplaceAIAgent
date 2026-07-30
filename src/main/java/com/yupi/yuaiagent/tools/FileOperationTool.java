package com.yupi.yuaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.yupi.yuaiagent.constant.FileConstant;
import com.yupi.yuaiagent.hitl.AgentRequestContext;
import com.yupi.yuaiagent.hitl.HumanApprovalService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * File ops with path traversal guard, HITL on write, pass-by-reference for large reads, and idempotent writes.
 */
public class FileOperationTool {

    private static final int INLINE_MAX_CHARS = 2000;

    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";
    private final HumanApprovalService approvalService;
    private final ToolIdempotencyStore idempotencyStore;
    private final FileHandleStore fileHandleStore;

    public FileOperationTool() {
        this(null, null, null);
    }

    public FileOperationTool(HumanApprovalService approvalService) {
        this(approvalService, null, null);
    }

    public FileOperationTool(HumanApprovalService approvalService,
                             ToolIdempotencyStore idempotencyStore,
                             FileHandleStore fileHandleStore) {
        this.approvalService = approvalService;
        this.idempotencyStore = idempotencyStore;
        this.fileHandleStore = fileHandleStore;
    }

    private Path resolveAndValidate(String fileName) {
        Path basePath = Paths.get(FILE_DIR).toAbsolutePath().normalize();
        Path resolved = basePath.resolve(fileName).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new SecurityException("非法文件路径，已被安全策略拦截：" + fileName);
        }
        return resolved;
    }

    @Tool(description = """
            Read a local sandbox file. Large files are registered as file_id and return a short preview \
            instead of dumping the full body into context (pass-by-reference).
            WHEN TO USE: need content of a previously written/downloaded file by name.
            DO NOT USE: pasting huge content into other tool args — use file_id + readFileChunk.
            For line-range reading use readFileChunk(fileIdOrName, startLine, maxLines).
            Read-only; safe to retry.""")
    public String readFile(@ToolParam(description = "File name under the sandbox file directory") String fileName) {
        try {
            Path filePath = resolveAndValidate(fileName);
            String content = FileUtil.readUtf8String(filePath.toFile());
            if (fileHandleStore != null && content.length() > INLINE_MAX_CHARS) {
                FileHandleStore.Handle handle = fileHandleStore.register(fileName, content);
                String preview = content.substring(0, Math.min(400, content.length()));
                return "File saved to context memory (file_id=" + handle.fileId()
                        + ", fileName=" + fileName + ", totalChars=" + handle.totalChars() + "). "
                        + "Use readFileChunk(fileIdOrName=\"" + handle.fileId()
                        + "\", startLine=0, maxLines=80) to read details.\nPreview:\n" + preview;
            }
            if (fileHandleStore != null) {
                fileHandleStore.register(fileName, content);
            }
            return content;
        } catch (SecurityException e) {
            return "拒绝读取：" + e.getMessage();
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = """
            Read a line range from a file_id (preferred) or file name previously registered by readFile/writeFile.
            WHEN TO USE: after readFile returned a file_id for a large file; never paste full documents into tool args.
            startLine is 0-based; maxLines default 80 (capped at 200).""")
    public String readFileChunk(
            @ToolParam(description = "file_id from a prior read/write, or the file name") String fileIdOrName,
            @ToolParam(description = "0-based start line") Integer startLine,
            @ToolParam(description = "Max lines to return (1-200, default 80)") Integer maxLines) {
        if (fileHandleStore == null) {
            return "File handle store unavailable; fall back to readFile(fileName).";
        }
        int start = startLine == null ? 0 : startLine;
        int max = maxLines == null ? 80 : maxLines;
        // If handle missing, try loading from disk by name
        if (fileHandleStore.get(fileIdOrName).isEmpty() && fileIdOrName != null && !fileIdOrName.startsWith("file_")) {
            try {
                Path filePath = resolveAndValidate(fileIdOrName);
                String content = FileUtil.readUtf8String(filePath.toFile());
                fileHandleStore.register(fileIdOrName, content);
            } catch (Exception ignored) {
                // fall through to store miss message
            }
        }
        return fileHandleStore.readChunk(fileIdOrName, start, max);
    }

    @Tool(description = """
            Write content to a local sandbox file (side effect). High-risk writes may require human approval — \
            if pending-approval is returned, obtain approvalId via HITL and retry with the same fileName/content and approvalId.
            WHEN TO USE: persist generated drafts, reports, or intermediate artifacts.
            DO NOT USE: for reading (use readFile/readFileChunk). Prefer shorter content; huge bodies pollute context.
            Idempotent within TTL for the same fileName+content fingerprint.""")
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName,
                            @ToolParam(description = "Content to write to the file") String content,
                            @ToolParam(description = "Approval ID from prior HITL approval, if any") String approvalId) {
        String payload = fileName + "::" + (content != null ? content.hashCode() : 0);
        if (idempotencyStore != null) {
            String key = idempotencyStore.key("writeFile", payload + "::" + (approvalId == null ? "" : approvalId));
            Optional<String> cached = idempotencyStore.find(key);
            if (cached.isPresent()) {
                return cached.get() + "\n[System Note: idempotent replay — write was not re-executed]";
            }
        }
        try {
            if (approvalService != null
                    && approvalService.requiresApproval(HumanApprovalService.ActionType.FILE_WRITE)) {
                boolean approved = approvalService.consumeIfApproved(
                        approvalId, HumanApprovalService.ActionType.FILE_WRITE, payload);
                if (!approved) {
                    AgentRequestContext.Holder ctx = AgentRequestContext.get();
                    String userId = ctx != null ? ctx.userId() : null;
                    String chatId = ctx != null ? ctx.chatId() : null;
                    HumanApprovalService.ApprovalRequest req = approvalService.requestApproval(
                            userId, chatId, HumanApprovalService.ActionType.FILE_WRITE,
                            "写入文件：" + fileName, payload);
                    return approvalService.pendingMessage(req);
                }
            }
            Path filePath = resolveAndValidate(fileName);
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath.toFile());
            String result = "File written successfully to: " + filePath;
            if (fileHandleStore != null) {
                FileHandleStore.Handle handle = fileHandleStore.register(fileName, content == null ? "" : content);
                result += " (file_id=" + handle.fileId() + ")";
            }
            if (idempotencyStore != null) {
                idempotencyStore.remember(
                        idempotencyStore.key("writeFile", payload + "::" + (approvalId == null ? "" : approvalId)),
                        result);
            }
            return result;
        } catch (SecurityException e) {
            return "拒绝写入：" + e.getMessage();
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }
}
