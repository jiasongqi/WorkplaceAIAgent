package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.dto.ImportResult;
import com.yupi.yuaiagent.export.DataExportService;
import com.yupi.yuaiagent.export.DataImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Export/Import application service.
 *
 * @author jsq
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportAppService {

    private final DataExportService exportService;
    private final DataImportService importService;

    public void exportAll(String userId, OutputStream out) throws IOException {
        exportService.exportUser(userId, out);
    }

    public ImportResult importData(String userId, MultipartFile file) throws IOException {
        DataImportService.ImportResult raw = importService.importFromZip(userId, file.getInputStream());
        ImportResult result = new ImportResult();
        result.setSessionsImported(raw.getSessionsImported());
        result.setSessionsSkipped(raw.getSessionsSkipped());
        result.setMessagesImported(raw.getMessagesImported());
        result.setFavoritesImported(raw.getFavoritesImported());
        return result;
    }
}
