package com.yupi.yuaiagent.suggestion;

/**
 * A clickable next-step chip shown after an agent reply or on cold start.
 *
 * @param id      stable id for analytics
 * @param label   short chip text
 * @param message full message sent when the user clicks the chip
 */
public record SuggestedAction(String id, String label, String message) {
}
