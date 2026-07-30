package com.yupi.yuaiagent.sessionstate;

import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.model.Artifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HandoffProtocolServiceTest {

    @Mock
    private SessionSharedStateStore store;
    @Mock
    private ArtifactShelf artifactShelf;

    private HandoffProtocolService service;
    private SessionSharedState memoryState;

    @BeforeEach
    void setUp() {
        memoryState = null;
        when(store.findByChatId(any())).thenAnswer(inv -> Optional.ofNullable(memoryState));
        when(store.save(any())).thenAnswer(inv -> {
            memoryState = inv.getArgument(0);
            return memoryState;
        });
        service = new HandoffProtocolService(store, artifactShelf);
    }

    @AfterEach
    void tearDown() {
        HandoffScopeContext.clear();
    }

    @Test
    void buildsFourQuadrantPacketAndInstallsScope() {
        HandoffSanityResult result = service.recordAndValidate(
                "c1", "u1", "CONSULTATION", "GENERAL",
                "看下我的日程", "查询/确认预约日程", "tr-9");

        assertThat(result.accepted()).isTrue();
        HandoffPacket p = result.repairedPacket();
        assertThat(p.getMeta().getSourceAgent()).isEqualTo("CONSULTATION");
        assertThat(p.getMeta().getTargetAgent()).isEqualTo("GENERAL");
        assertThat(p.getMeta().getHandoffId()).startsWith("ho_");
        assertThat(p.getMission().getObjective()).contains("日程");
        assertThat(p.getMission().getDefinitionOfDone()).isNotBlank();
        assertThat(p.getMission().getConstraints()).isNotEmpty();
        assertThat(p.getContext().getUserOriginalIntent()).contains("日程");
        assertThat(p.getScope()).contains("rag.query");
        assertThat(HandoffScopeContext.current()).contains("rag.query");
        assertThat(memoryState.getLastHandoffPacket()).isNotNull();
        assertThat(memoryState.getHopCount()).isEqualTo(1);
    }

    @Test
    void stripsHallucinatedArtifactIds() {
        when(artifactShelf.get("real_1")).thenReturn(Optional.of(Artifact.builder().artifactId("real_1").build()));
        when(artifactShelf.get("ghost")).thenReturn(Optional.empty());

        HandoffPacket packet = HandoffPacket.builder()
                .artifacts(HandoffPacket.Artifacts.builder()
                        .artifactIds(new ArrayList<>(List.of("real_1", "ghost")))
                        .build())
                .build();

        HandoffSanityResult result = service.sanitizeArtifacts(packet);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo("hallucinated_or_missing_artifacts");
        assertThat(result.missingArtifactIds()).containsExactly("ghost");
        assertThat(result.repairedPacket().getArtifacts().getArtifactIds()).containsExactly("real_1");
        assertThat(result.repairedPacket().getArtifacts().isValidated()).isFalse();
    }

    @Test
    void hopTtlBlocksFurtherSwitch() {
        memoryState = SessionSharedState.builder()
                .chatId("c-ttl")
                .hopCount(5)
                .agentChain(new ArrayList<>(List.of("A", "B", "C", "D", "E")))
                .build();

        HandoffSanityResult result = service.recordAndValidate(
                "c-ttl", "u", "E", "GENERAL", "again", null, null);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo("hop_ttl_exceeded");
        assertThat(service.buildNackRepairInjection(result)).contains("hop_ttl_exceeded");
    }
}
