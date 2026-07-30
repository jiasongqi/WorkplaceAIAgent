package com.yupi.yuaiagent.hitl;

import com.yupi.yuaiagent.sessionstate.HandoffPacket;
import com.yupi.yuaiagent.sessionstate.SessionSharedState;
import com.yupi.yuaiagent.sessionstate.SessionSharedStateService;
import com.yupi.yuaiagent.sessionstate.SessionSharedStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HumanHandoffServiceTest {

    @TempDir
    Path tmp;

    @Mock
    private SessionSharedStateStore store;
    @Mock
    private HitlNotifyService notifyService;

    private SessionSharedState memoryState;
    private HumanHandoffService service;

    @BeforeEach
    void setUp() {
        memoryState = null;
        when(store.findByChatId(any())).thenAnswer(inv -> Optional.ofNullable(memoryState));
        when(store.save(any())).thenAnswer(inv -> {
            memoryState = inv.getArgument(0);
            return memoryState;
        });

        HitlProperties props = new HitlProperties();
        props.setHumanHandoffTtlSeconds(3600);
        SessionSharedStateService shared = new SessionSharedStateService(store, null, null, null);
        service = new HumanHandoffService(props, notifyService, shared);
        ReflectionTestUtils.setField(service, "storageDir", tmp.toString());
        service.init();
    }

    @Test
    void parkThenResumeHydratesSharedState() {
        HandoffPacket packet = HandoffPacket.builder()
                .meta(HandoffPacket.Meta.builder()
                        .handoffId("ho_park1")
                        .sourceAgent("RESUME")
                        .targetAgent("GENERAL")
                        .hopCount(6)
                        .build())
                .build();

        HumanHandoffTicket parked = service.park(
                "chat-h", "user-h", packet, "hop_ttl_exceeded", "跳数超限");

        assertThat(parked.getStatus()).isEqualTo(HumanHandoffTicket.Status.WAITING_FOR_HUMAN);
        assertThat(service.findWaitingByChatId("chat-h")).isPresent();
        assertThat(service.pendingMessage(parked)).contains("人工接管");

        HumanHandoffTicket resumed = service.resume(
                parked.getHandoffId(), "user-h", "用户补充：先按通用顾问回答");

        assertThat(resumed.getStatus()).isEqualTo(HumanHandoffTicket.Status.RESUMED);
        assertThat(resumed.getHumanInput()).contains("通用顾问");
        assertThat(service.findWaitingByChatId("chat-h")).isEmpty();
        assertThat(memoryState.getFacts().get("humanHandoffInput")).contains("通用顾问");
    }
}
