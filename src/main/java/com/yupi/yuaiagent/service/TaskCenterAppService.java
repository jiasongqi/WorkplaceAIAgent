package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.hitl.HumanApprovalService;
import com.yupi.yuaiagent.workflow.runtime.WorkflowInstance;
import com.yupi.yuaiagent.workflow.runtime.WorkflowRepository;
import com.yupi.yuaiagent.workflow.runtime.WorkflowStatus;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskCenterAppService {

    @Resource
    private HumanApprovalService humanApprovalService;
    @Resource
    private WorkflowRepository workflowRepository;

    public List<Map<String, Object>> listMine(String userId) {
        List<Map<String, Object>> tasks = new ArrayList<>();

        for (HumanApprovalService.ApprovalRequest req : humanApprovalService.listPendingByUser(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", req.getApprovalId());
            m.put("type", "HITL");
            m.put("title", "待确认：" + req.getActionType());
            m.put("status", req.getStatus().name());
            m.put("detail", req.getSummary());
            m.put("chatId", req.getChatId());
            tasks.add(m);
        }

        for (WorkflowInstance instance : workflowRepository.findAll()) {
            if (userId != null && userId.equals(instance.getUserId())
                    && instance.getStatus() == WorkflowStatus.PAUSED) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", instance.getInstanceId());
                m.put("type", "WORKFLOW");
                m.put("title", "暂停中的工作流：" + instance.getWorkflowId());
                m.put("status", instance.getStatus().name());
                m.put("detail", "等待审批后可继续");
                m.put("chatId", instance.getChatId());
                tasks.add(m);
            }
        }
        return tasks;
    }
}
