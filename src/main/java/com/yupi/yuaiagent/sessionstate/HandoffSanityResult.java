package com.yupi.yuaiagent.sessionstate;

import lombok.Builder;

import java.util.List;

/**
 * ACK / NACK result of a handoff sanity check before the target agent runs.
 */
@Builder
public record HandoffSanityResult(
        boolean accepted,
        String reason,
        String suggestion,
        HandoffPacket repairedPacket,
        List<String> missingArtifactIds
) {
    public static HandoffSanityResult ack(HandoffPacket packet) {
        return HandoffSanityResult.builder()
                .accepted(true)
                .repairedPacket(packet)
                .missingArtifactIds(List.of())
                .build();
    }

    public static HandoffSanityResult nack(String reason, String suggestion, HandoffPacket repaired) {
        return HandoffSanityResult.builder()
                .accepted(false)
                .reason(reason)
                .suggestion(suggestion)
                .repairedPacket(repaired)
                .missingArtifactIds(List.of())
                .build();
    }

    public static HandoffSanityResult nackMissingArtifacts(String reason, String suggestion,
                                                          HandoffPacket repaired,
                                                          List<String> missing) {
        return HandoffSanityResult.builder()
                .accepted(false)
                .reason(reason)
                .suggestion(suggestion)
                .repairedPacket(repaired)
                .missingArtifactIds(missing != null ? missing : List.of())
                .build();
    }
}
