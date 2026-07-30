package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.registry.AgentDescriptor;
import com.yupi.yuaiagent.registry.AgentRegistry;
import com.yupi.yuaiagent.repository.entity.DigitalEmployeeEntity;
import com.yupi.yuaiagent.repository.entity.DigitalEmployeeVersionEntity;
import com.yupi.yuaiagent.repository.jpa.DigitalEmployeeJpaRepository;
import com.yupi.yuaiagent.repository.jpa.DigitalEmployeeVersionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DigitalEmployeeAppService {

    private final DigitalEmployeeJpaRepository employeeRepository;
    private final DigitalEmployeeVersionJpaRepository versionRepository;
    private final AgentRegistry agentRegistry;
    private final ExpertPackAppService expertPackAppService;

    public List<AgentDescriptor> listTemplates() {
        return listTemplates(null);
    }

    public List<AgentDescriptor> listTemplates(String userId) {
        java.util.Set<String> enabledAgents = expertPackAppService.getEnabledAgentCodes(userId);
        return agentRegistry.list().stream()
                .filter(AgentDescriptor::isEnabled)
                .filter(d -> enabledAgents.isEmpty() || enabledAgents.contains(d.getAgentCode()))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<DigitalEmployeeView> listMine(String userId) {
        return employeeRepository.findByOwnerUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public DigitalEmployeeView createFromTemplate(String userId, CreateRequest request) {
        AgentDescriptor template = agentRegistry.get(request.templateCode())
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + request.templateCode()));

        DigitalEmployeeEntity entity = new DigitalEmployeeEntity();
        entity.setEmployeeId(UUID.randomUUID().toString());
        entity.setOwnerUserId(userId);
        entity.setTemplateCode(template.getAgentCode());
        entity.setName(StringUtils.hasText(request.name()) ? request.name() : template.getDisplayName());
        entity.setPersona(StringUtils.hasText(request.persona())
                ? request.persona()
                : defaultPersona(template));
        entity.setSkillBindings(template.getSkillBindings() != null
                ? new ArrayList<>(template.getSkillBindings())
                : new ArrayList<>());
        entity.setStatus("ACTIVE");
        entity.setConfigVersion(1);
        entity.setActive(false);
        employeeRepository.save(entity);
        saveVersionSnapshot(entity);
        return toView(entity);
    }

    @Transactional
    public DigitalEmployeeView updateViaChat(String userId, String employeeId, UpdateRequest request) {
        DigitalEmployeeEntity entity = requireOwned(userId, employeeId);
        if (StringUtils.hasText(request.name())) {
            entity.setName(request.name().trim());
        }
        if (request.persona() != null) {
            entity.setPersona(request.persona());
        }
        if (request.skillBindings() != null) {
            entity.setSkillBindings(new ArrayList<>(request.skillBindings()));
        }
        entity.setConfigVersion(entity.getConfigVersion() + 1);
        employeeRepository.save(entity);
        saveVersionSnapshot(entity);
        return toView(entity);
    }

    @Transactional
    public DigitalEmployeeView rollback(String userId, String employeeId, int version) {
        DigitalEmployeeEntity entity = requireOwned(userId, employeeId);
        DigitalEmployeeVersionEntity snap = versionRepository
                .findByEmployeeIdAndConfigVersion(employeeId, version)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + version));
        entity.setPersona(snap.getPersona());
        entity.setSkillBindings(snap.getSkillBindings() != null
                ? new ArrayList<>(snap.getSkillBindings())
                : new ArrayList<>());
        entity.setConfigVersion(entity.getConfigVersion() + 1);
        employeeRepository.save(entity);
        saveVersionSnapshot(entity);
        return toView(entity);
    }

    @Transactional
    public DigitalEmployeeView activate(String userId, String employeeId) {
        DigitalEmployeeEntity entity = requireOwned(userId, employeeId);
        employeeRepository.deactivateAll(userId);
        employeeRepository.flush();
        entity.setActive(true);
        return toView(employeeRepository.save(entity));
    }

    public DigitalEmployeeView findActive(String userId) {
        return employeeRepository.findFirstByOwnerUserIdAndActiveTrue(userId)
                .map(this::toView)
                .orElse(null);
    }

    /**
     * Injection for active digital employee (Orchestrator context).
     */
    public String buildActiveInjection(String userId) {
        DigitalEmployeeView active = findActive(userId);
        if (active == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【当前委托数字员工】\n");
        sb.append("名称：").append(active.name()).append('\n');
        sb.append("模板：").append(active.templateCode()).append('\n');
        if (StringUtils.hasText(active.persona())) {
            sb.append("人设：").append(active.persona()).append('\n');
        }
        if (active.skillBindings() != null && !active.skillBindings().isEmpty()) {
            sb.append("技能绑定：").append(String.join(", ", active.skillBindings())).append('\n');
        }
        sb.append("请优先以该数字员工的专精视角回答；若用户在创建/管理数字员工，给出可执行步骤。\n");
        return sb.toString();
    }

    /**
     * Map template code to AgentIntent-ish routing hint for Orchestrator.
     */
    public String preferredAgentType(String userId) {
        DigitalEmployeeView active = findActive(userId);
        if (active == null) {
            return null;
        }
        return switch (active.templateCode()) {
            case "resume-agent" -> "RESUME";
            case "negotiation-agent" -> "NEGOTIATION";
            case "escape-agent" -> "ESCAPE";
            default -> "GENERAL";
        };
    }

    private DigitalEmployeeEntity requireOwned(String userId, String employeeId) {
        DigitalEmployeeEntity entity = employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("数字员工不存在"));
        if (!userId.equals(entity.getOwnerUserId())) {
            throw new IllegalArgumentException("无权操作该数字员工");
        }
        return entity;
    }

    private void saveVersionSnapshot(DigitalEmployeeEntity entity) {
        DigitalEmployeeVersionEntity version = new DigitalEmployeeVersionEntity();
        version.setEmployeeId(entity.getEmployeeId());
        version.setConfigVersion(entity.getConfigVersion());
        version.setPersona(entity.getPersona());
        version.setSkillBindings(entity.getSkillBindings() != null
                ? new ArrayList<>(entity.getSkillBindings())
                : new ArrayList<>());
        versionRepository.save(version);
    }

    private static String defaultPersona(AgentDescriptor template) {
        return "你是「" + template.getDisplayName() + "」。"
                + (StringUtils.hasText(template.getDescription()) ? template.getDescription() : "")
                + " 回答要专业、可执行，先结论后步骤。";
    }

    private DigitalEmployeeView toView(DigitalEmployeeEntity e) {
        return new DigitalEmployeeView(
                e.getEmployeeId(),
                e.getTemplateCode(),
                e.getName(),
                e.getPersona(),
                e.getSkillBindings(),
                e.getStatus(),
                e.getConfigVersion(),
                Boolean.TRUE.equals(e.getActive())
        );
    }

    public record CreateRequest(String templateCode, String name, String persona) {
    }

    public record UpdateRequest(String name, String persona, List<String> skillBindings) {
    }

    public record DigitalEmployeeView(
            String id,
            String templateCode,
            String name,
            String persona,
            List<String> skillBindings,
            String status,
            Integer configVersion,
            boolean active
    ) {
    }
}
