package com.yupi.yuaiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pass-by-reference handles for large file contents (Ch3: pass ID, not full text).
 */
@Slf4j
@Component
public class FileHandleStore {

    public record Handle(String fileId, String fileName, String content, int totalChars) {
    }

    private final Map<String, Handle> byId = new ConcurrentHashMap<>();
    private final Map<String, String> nameToId = new ConcurrentHashMap<>();

    public Handle register(String fileName, String content) {
        String id = "file_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String safeName = fileName == null ? id : fileName;
        String body = content == null ? "" : content;
        Handle handle = new Handle(id, safeName, body, body.length());
        byId.put(id, handle);
        nameToId.put(safeName, id);
        log.debug("[FileHandle] registered id={} name={} chars={}", id, safeName, handle.totalChars());
        return handle;
    }

    public Optional<Handle> get(String fileIdOrName) {
        if (fileIdOrName == null || fileIdOrName.isBlank()) {
            return Optional.empty();
        }
        Handle h = byId.get(fileIdOrName);
        if (h != null) {
            return Optional.of(h);
        }
        String id = nameToId.get(fileIdOrName);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public String readChunk(String fileIdOrName, int startLine, int maxLines) {
        Optional<Handle> opt = get(fileIdOrName);
        if (opt.isEmpty()) {
            return "File handle not found: " + fileIdOrName + ". Use readFile first or pass a valid file_id.";
        }
        Handle h = opt.get();
        String[] lines = h.content().split("\\R", -1);
        int start = Math.max(0, startLine);
        int max = Math.max(1, Math.min(maxLines <= 0 ? 80 : maxLines, 200));
        if (start >= lines.length) {
            return "startLine " + start + " beyond end (totalLines=" + lines.length + ", file_id=" + h.fileId() + ")";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("file_id=").append(h.fileId())
                .append(" fileName=").append(h.fileName())
                .append(" totalLines=").append(lines.length)
                .append(" showing lines ").append(start).append("-")
                .append(Math.min(lines.length, start + max) - 1).append("\n");
        for (int i = start; i < lines.length && i < start + max; i++) {
            sb.append(i).append("|").append(lines[i]).append("\n");
        }
        if (start + max < lines.length) {
            sb.append("[System Note: more lines remain; call readFileChunk with startLine=")
                    .append(start + max).append("]");
        }
        return sb.toString();
    }
}
