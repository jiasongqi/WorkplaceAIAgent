package com.yupi.yuaiagent.pack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExpertPackPreferenceConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void staleVersionIsRejectedUnderContention() throws Exception {
        FileExpertPackPreferenceRepository repo = new FileExpertPackPreferenceRepository(
                tempDir.resolve("user-prefs.json"), new ObjectMapper());
        UserPackPreference first = repo.save(new UserPackPreference(
                "u1", PackPreferenceMode.EXPLICIT_PARTIAL, Map.of("p1", true), 0));
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger stale = new AtomicInteger();
        Thread t1 = new Thread(() -> {
            await(start);
            try {
                repo.save(new UserPackPreference("u1", PackPreferenceMode.EXPLICIT_PARTIAL, Map.of("p1", false), first.version()));
            } catch (IllegalStateException ex) {
                stale.incrementAndGet();
            }
        });
        Thread t2 = new Thread(() -> {
            await(start);
            try {
                repo.save(new UserPackPreference("u1", PackPreferenceMode.EXPLICIT_ALL_DISABLED, Map.of("p1", false), first.version()));
            } catch (IllegalStateException ex) {
                stale.incrementAndGet();
            }
        });
        t1.start();
        t2.start();
        start.countDown();
        t1.join();
        t2.join();
        assertThat(stale.get()).isEqualTo(1);
        assertThat(repo.find("u1").orElseThrow().version()).isEqualTo(2L);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
