package com.yupi.yuaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileHandleStoreTest {

    @Test
    void registerAndReadChunk() {
        FileHandleStore store = new FileHandleStore();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            sb.append("line-").append(i).append("\n");
        }
        FileHandleStore.Handle h = store.register("big.txt", sb.toString());
        String chunk = store.readChunk(h.fileId(), 10, 5);
        assertTrue(chunk.contains("file_id=" + h.fileId()));
        assertTrue(chunk.contains("10|line-10"));
        assertTrue(chunk.contains("System Note") || chunk.contains("showing lines"));
    }
}
