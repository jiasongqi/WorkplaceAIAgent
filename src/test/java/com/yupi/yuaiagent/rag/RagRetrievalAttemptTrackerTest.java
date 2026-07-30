package com.yupi.yuaiagent.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RagRetrievalAttemptTrackerTest {

    @Test
    void blocksAfterMaxEmptyRetries() {
        RagRetrievalAttemptTracker tracker = new RagRetrievalAttemptTracker(2);

        assertFalse(tracker.shouldBlock("chat-1"));
        tracker.recordEmpty("chat-1");
        assertFalse(tracker.shouldBlock("chat-1"));
        tracker.recordEmpty("chat-1");
        assertTrue(tracker.shouldBlock("chat-1"));
    }

    @Test
    void successResetsCounter() {
        RagRetrievalAttemptTracker tracker = new RagRetrievalAttemptTracker(2);
        tracker.recordEmpty("chat-2");
        tracker.recordEmpty("chat-2");
        assertTrue(tracker.shouldBlock("chat-2"));

        tracker.recordSuccess("chat-2");
        assertFalse(tracker.shouldBlock("chat-2"));
    }
}
