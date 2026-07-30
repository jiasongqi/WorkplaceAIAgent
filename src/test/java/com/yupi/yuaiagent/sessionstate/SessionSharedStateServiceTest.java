package com.yupi.yuaiagent.sessionstate;

import com.yupi.yuaiagent.agent.model.Appointment;
import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.repository.AppointmentRepository;
import com.yupi.yuaiagent.validation.InfoValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionSharedStateServiceTest {

    @Mock
    private SessionSharedStateStore store;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private InfoValidator infoValidator;
    @Mock
    private ArtifactShelf artifactShelf;

    private SessionSharedStateService service;
    private SessionSharedState memoryState;

    @BeforeEach
    void setUp() {
        memoryState = null;
        when(store.findByChatId(any())).thenAnswer(inv -> Optional.ofNullable(memoryState));
        when(store.save(any())).thenAnswer(inv -> {
            memoryState = inv.getArgument(0);
            return memoryState;
        });
        when(infoValidator.formatDateTime(any())).thenReturn("2026-07-29 15:00");
        HandoffProtocolService handoffProtocolService = new HandoffProtocolService(store, artifactShelf);
        service = new SessionSharedStateService(store, appointmentRepository, infoValidator, handoffProtocolService);
    }

    @AfterEach
    void tearDown() {
        HandoffScopeContext.clear();
    }

    @Test
    void upsertAppointmentAppearsInInjectionForAnyAgent() {
        Appointment appt = Appointment.builder()
                .appointmentId("3ab3953e-e63b-4752-a308-80d2706e5dc2")
                .chatId("chat-1")
                .name("kria")
                .contact("18104620109")
                .topic("职业方向梳理")
                .appointmentTime(LocalDateTime.of(2026, 7, 29, 15, 0))
                .status(Appointment.AppointmentStatus.CONFIRMED)
                .build();

        service.upsertAppointment("chat-1", "user-1", appt);
        HandoffSanityResult result = service.recordHandoffDetailed(
                "chat-1", "user-1", "CONSULTATION", "GENERAL",
                "用户问日程", "查询/确认预约日程", "trace-1");

        assertThat(result.accepted()).isTrue();
        assertThat(result.repairedPacket().getArtifacts().getAppointmentIds())
                .contains("3ab3953e-e63b-4752-a308-80d2706e5dc2");
        assertThat(HandoffScopeContext.isActive()).isTrue();

        String injection = service.buildPromptInjection("chat-1", "user-1");
        assertThat(injection).contains("会话共享状态");
        assertThat(injection).contains("不可被摘要覆盖");
        assertThat(injection).contains("3ab3953e-e63b-4752-a308-80d2706e5dc2");
        assertThat(injection).contains("kria");
        assertThat(injection).contains("Handoff Packet");
        assertThat(injection).contains("Mission.objective");
        assertThat(injection).contains("证据穿透");
        assertThat(injection).contains("raw{appointmentId=");
        assertThat(injection).contains("GENERAL");
    }

    @Test
    void hydratesFromAppointmentRepositoryWhenScratchpadEmpty() {
        when(appointmentRepository.findByChatId("chat-2")).thenReturn(List.of(
                Appointment.builder()
                        .appointmentId("id-9")
                        .chatId("chat-2")
                        .name("Ada")
                        .topic("谈薪")
                        .appointmentTime(LocalDateTime.of(2026, 8, 1, 10, 0))
                        .status(Appointment.AppointmentStatus.PENDING)
                        .build()
        ));

        String injection = service.buildPromptInjection("chat-2", "u2");
        assertThat(injection).contains("id-9");
        assertThat(injection).contains("Ada");
        assertThat(injection).contains("优先级");
    }

    @Test
    void hopTtlExceedsReturnsNack() {
        memoryState = SessionSharedState.builder()
                .chatId("chat-ttl")
                .userId("u")
                .hopCount(HandoffPacket.DEFAULT_MAX_HOPS)
                .agentChain(List.of("RESUME", "NEGOTIATION", "ESCAPE", "GENERAL", "RESUME"))
                .build();

        HandoffSanityResult result = service.recordHandoffDetailed(
                "chat-ttl", "u", "RESUME", "GENERAL", "再换一次", null, null);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo("hop_ttl_exceeded");
    }
}
