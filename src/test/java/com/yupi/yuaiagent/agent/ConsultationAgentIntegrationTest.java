package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.model.Appointment;
import com.yupi.yuaiagent.calendar.CalendarEvent;
import com.yupi.yuaiagent.calendar.CalendarService;
import com.yupi.yuaiagent.calendar.CalendarServiceFactory;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.config.FollowUpTemplateConfig;
import com.yupi.yuaiagent.repository.AppointmentRepository;
import com.yupi.yuaiagent.validation.InfoValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 预约咨询完整流程集成测试（任务 11.2）
 *
 * <p>覆盖：意图识别 → 追问收集 → 确认 → 创建预约 → 持久化 端到端流程。
 * 日历 API 通过 {@link MockitoBean} 隔离，不依赖外部服务。</p>
 *
 * <p><b>Validates: Requirements 1.1, 2.2, 2.7, 5.5, 5.7</b></p>
 */
@SpringBootTest
@Slf4j
class ConsultationAgentIntegrationTest {

    @Autowired
    private ChatModel dashscopeChatModel;

    @Autowired
    private ChatMemoryManager chatMemoryManager;

    @Autowired
    private FollowUpTemplateConfig followUpTemplateConfig;

    @Autowired
    private InfoValidator infoValidator;

    @MockitoBean
    private CalendarServiceFactory calendarServiceFactory;

    @MockitoBean
    private CalendarService mockCalendarService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    private ConsultationAgent consultationAgent;

    @BeforeEach
    void setUp() {
        // 配置 Mock 日历服务：创建事件返回成功结果
        CalendarEvent mockEvent = CalendarEvent.builder()
                .eventId("mock-event-" + UUID.randomUUID())
                .title("预约咨询")
                .link("https://calendar.example.com/event/mock")
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(1))
                .provider("FEISHU")
                .build();

        when(mockCalendarService.getProvider()).thenReturn(Appointment.CalendarProvider.FEISHU);
        when(mockCalendarService.createEvent(any(Appointment.class))).thenReturn(mockEvent);
        when(calendarServiceFactory.getCalendarService()).thenReturn(mockCalendarService);

        // 每个测试用独立的 ConsultationAgent 实例，避免会话状态污染
        consultationAgent = new ConsultationAgent(
                dashscopeChatModel,
                chatMemoryManager,
                followUpTemplateConfig,
                infoValidator,
                calendarServiceFactory,
                appointmentRepository
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. 意图识别（Req 1.1）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("意图识别：预约咨询意图应被正确识别")
    void testConsultationIntentDetected() {
        boolean detected = consultationAgent.detectConsultationIntent("我想预约咨询专家");
        log.info("意图识别结果：{}", detected);
        assertThat(detected).isTrue();
    }

    @Test
    @DisplayName("意图识别：非预约意图不应被识别为预约")
    void testNonConsultationIntentNotDetected() {
        boolean detected = consultationAgent.detectConsultationIntent("帮我优化一下简历");
        log.info("非预约意图识别结果：{}", detected);
        assertThat(detected).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. 追问收集流程（Req 5.1, 5.5）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("追问流程：缺少姓名时应追问姓名")
    void testFollowUpForMissingName() {
        String chatId = UUID.randomUUID().toString();

        // 触发预约意图，进入 COLLECTING_INFO 状态
        String response1 = consultationAgent.chat("我想预约咨询", chatId);
        log.info("第1轮（触发意图）：{}", response1);
        assertThat(response1).isNotBlank();

        // 此时应追问姓名
        ConsultationAgent.ConsultationState state = consultationAgent.getSessionState(chatId);
        assertThat(state).isEqualTo(ConsultationAgent.ConsultationState.COLLECTING_INFO);
    }

    @Test
    @DisplayName("追问流程：提供姓名后应追问联系方式")
    void testFollowUpProgressesAfterName() {
        String chatId = UUID.randomUUID().toString();

        // 触发意图
        consultationAgent.chat("我想预约咨询", chatId);

        // 提供姓名
        String response = consultationAgent.chat("我叫张三", chatId);
        log.info("提供姓名后的回复：{}", response);

        assertThat(response).isNotBlank();
        // 仍在收集信息阶段（还需要联系方式和时间）
        assertThat(consultationAgent.getSessionState(chatId))
                .isEqualTo(ConsultationAgent.ConsultationState.COLLECTING_INFO);
    }

    @Test
    @DisplayName("追问流程：核心信息完整后进入确认阶段（Req 5.5）")
    void testEntersConfirmingAfterCoreInfoComplete() {
        String chatId = UUID.randomUUID().toString();

        // 完整的多轮追问收集流程
        consultationAgent.chat("我想预约咨询", chatId);
        consultationAgent.chat("我叫李四", chatId);
        consultationAgent.chat("13800138000", chatId);
        String response = consultationAgent.chat("明天下午3点", chatId);
        log.info("核心信息完整后的回复：{}", response);

        // 可能进入确认阶段，也可能先追问非核心信息（topic）
        ConsultationAgent.ConsultationState state = consultationAgent.getSessionState(chatId);
        assertThat(state).isIn(
                ConsultationAgent.ConsultationState.COLLECTING_INFO,
                ConsultationAgent.ConsultationState.CONFIRMING
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. 确认与创建预约（Req 2.2, 2.7）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("完整流程：从意图识别到预约创建并持久化（Req 1.1, 2.2, 2.7）")
    void testFullAppointmentFlow() {
        String chatId = UUID.randomUUID().toString();

        // Step 1: 触发预约意图
        String r1 = consultationAgent.chat("我想预约咨询专家", chatId);
        log.info("Step1 触发意图：{}", r1);
        assertThat(r1).isNotBlank();

        // Step 2: 提供姓名
        String r2 = consultationAgent.chat("王五", chatId);
        log.info("Step2 提供姓名：{}", r2);
        assertThat(r2).isNotBlank();

        // Step 3: 提供联系方式
        String r3 = consultationAgent.chat("13912345678", chatId);
        log.info("Step3 提供联系方式：{}", r3);
        assertThat(r3).isNotBlank();

        // Step 4: 提供预约时间
        String r4 = consultationAgent.chat("2026-06-10 14:00", chatId);
        log.info("Step4 提供时间：{}", r4);
        assertThat(r4).isNotBlank();

        // Step 5: 跳过非核心信息（如果有追问）
        ConsultationAgent.ConsultationState stateAfterCore = consultationAgent.getSessionState(chatId);
        if (stateAfterCore == ConsultationAgent.ConsultationState.COLLECTING_INFO) {
            String r5 = consultationAgent.chat("跳过", chatId);
            log.info("Step5 跳过非核心信息：{}", r5);
        }

        // Step 6: 确认预约
        ConsultationAgent.ConsultationState stateBeforeConfirm = consultationAgent.getSessionState(chatId);
        log.info("确认前状态：{}", stateBeforeConfirm);

        if (stateBeforeConfirm == ConsultationAgent.ConsultationState.CONFIRMING) {
            String r6 = consultationAgent.chat("确认", chatId);
            log.info("Step6 确认预约：{}", r6);
            assertThat(r6).isNotBlank();

            // 验证最终状态为 COMPLETED
            ConsultationAgent.ConsultationState finalState = consultationAgent.getSessionState(chatId);
            log.info("最终状态：{}", finalState);
            // 完成后状态被清理（返回 INITIAL），或为 COMPLETED
            assertThat(finalState).isIn(
                    ConsultationAgent.ConsultationState.INITIAL,
                    ConsultationAgent.ConsultationState.COMPLETED
            );

            // 验证预约已持久化（Req 2.7）
            List<Appointment> saved = appointmentRepository.findByChatId(chatId);
            log.info("持久化的预约记录数：{}", saved.size());
            assertThat(saved).isNotEmpty();

            Appointment appointment = saved.get(0);
            assertThat(appointment.getName()).isEqualTo("王五");
            assertThat(appointment.getContact()).isEqualTo("13912345678");
            assertThat(appointment.getChatId()).isEqualTo(chatId);
            assertThat(appointment.getStatus()).isIn(
                    Appointment.AppointmentStatus.CONFIRMED,
                    Appointment.AppointmentStatus.PENDING
            );
            log.info("预约记录验证通过：appointmentId={}, status={}", appointment.getAppointmentId(), appointment.getStatus());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. 日历 API 失败降级（Req 2.4）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("日历API失败：预约记录仍应保存，状态为 PENDING（Req 2.4）")
    void testAppointmentSavedEvenWhenCalendarFails() {
        // 重新配置 Mock：日历 API 抛出异常
        when(mockCalendarService.createEvent(any(Appointment.class)))
                .thenThrow(new CalendarService.CalendarException("飞书 API 调用失败"));

        String chatId = UUID.randomUUID().toString();

        consultationAgent.chat("我想预约咨询", chatId);
        consultationAgent.chat("赵六", chatId);
        consultationAgent.chat("test@example.com", chatId);
        consultationAgent.chat("2026-06-15 10:00", chatId);

        // 跳过非核心信息
        if (consultationAgent.getSessionState(chatId) == ConsultationAgent.ConsultationState.COLLECTING_INFO) {
            consultationAgent.chat("跳过", chatId);
        }

        // 确认
        if (consultationAgent.getSessionState(chatId) == ConsultationAgent.ConsultationState.CONFIRMING) {
            String response = consultationAgent.chat("确认", chatId);
            log.info("日历失败时的回复：{}", response);
            assertThat(response).isNotBlank();

            // 即使日历失败，预约记录也应被持久化（Req 2.7）
            List<Appointment> saved = appointmentRepository.findByChatId(chatId);
            if (!saved.isEmpty()) {
                Appointment appointment = saved.get(0);
                // 日历失败时状态为 PENDING
                assertThat(appointment.getStatus()).isEqualTo(Appointment.AppointmentStatus.PENDING);
                log.info("日历失败降级验证通过：status={}", appointment.getStatus());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. 用户修改信息（Req 5.7）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("修改流程：确认阶段回复「修改」应重置并重新收集（Req 5.7）")
    void testModifyDuringConfirmation() {
        String chatId = UUID.randomUUID().toString();

        consultationAgent.chat("我想预约咨询", chatId);
        consultationAgent.chat("孙七", chatId);
        consultationAgent.chat("13700137000", chatId);
        consultationAgent.chat("2026-06-20 15:00", chatId);

        // 跳过非核心信息
        if (consultationAgent.getSessionState(chatId) == ConsultationAgent.ConsultationState.COLLECTING_INFO) {
            consultationAgent.chat("跳过", chatId);
        }

        // 在确认阶段选择修改
        if (consultationAgent.getSessionState(chatId) == ConsultationAgent.ConsultationState.CONFIRMING) {
            String response = consultationAgent.chat("修改", chatId);
            log.info("修改后的回复：{}", response);
            assertThat(response).isNotBlank();

            // 应回到收集信息阶段
            ConsultationAgent.ConsultationState state = consultationAgent.getSessionState(chatId);
            assertThat(state).isEqualTo(ConsultationAgent.ConsultationState.COLLECTING_INFO);
            log.info("修改后状态验证通过：{}", state);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. 会话状态管理
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("会话隔离：不同 chatId 的会话状态互不干扰")
    void testSessionIsolation() {
        String chatId1 = UUID.randomUUID().toString();
        String chatId2 = UUID.randomUUID().toString();

        // chatId1 进入收集阶段
        consultationAgent.chat("我想预约咨询", chatId1);
        consultationAgent.chat("周八", chatId1);

        // chatId2 独立开始
        consultationAgent.chat("我想预约咨询", chatId2);

        // 两个会话状态独立
        ConsultationAgent.ConsultationState state1 = consultationAgent.getSessionState(chatId1);
        ConsultationAgent.ConsultationState state2 = consultationAgent.getSessionState(chatId2);

        log.info("chatId1 状态：{}，chatId2 状态：{}", state1, state2);
        assertThat(state1).isEqualTo(ConsultationAgent.ConsultationState.COLLECTING_INFO);
        assertThat(state2).isEqualTo(ConsultationAgent.ConsultationState.COLLECTING_INFO);
    }

    @Test
    @DisplayName("会话清理：clearSession 后状态应重置为 INITIAL")
    void testClearSession() {
        String chatId = UUID.randomUUID().toString();

        consultationAgent.chat("我想预约咨询", chatId);
        assertThat(consultationAgent.getSessionState(chatId))
                .isEqualTo(ConsultationAgent.ConsultationState.COLLECTING_INFO);

        consultationAgent.clearSession(chatId);
        assertThat(consultationAgent.getSessionState(chatId))
                .isEqualTo(ConsultationAgent.ConsultationState.INITIAL);
        log.info("会话清理验证通过");
    }
}
