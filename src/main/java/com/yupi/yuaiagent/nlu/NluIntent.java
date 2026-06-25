package com.yupi.yuaiagent.nlu;

import com.yupi.yuaiagent.agent.AgentIntent;

/**
 * Fine-grained intent taxonomy for NLU layer.
 * Maps 1:1 to downstream AgentIntent via toAgentIntent().
 *
 * @author jsq
 */
public enum NluIntent {

    // Resume-related
    RESUME_OPTIMIZE(AgentIntent.RESUME, "resume editing, resume polishing"),
    INTERVIEW_PREP(AgentIntent.RESUME, "interview preparation, mock interview"),
    JOB_CHANGE(AgentIntent.RESUME, "job switching, job hunting, offer evaluation"),
    OFFER_EVALUATE(AgentIntent.RESUME, "offer evaluation, comparing offers"),

    // Negotiation-related
    SALARY_ANALYZE(AgentIntent.NEGOTIATION, "salary analysis, compensation benchmarking"),
    SALARY_NEGOTIATE(AgentIntent.NEGOTIATION, "raise request, salary negotiation"),
    PERFORMANCE(AgentIntent.NEGOTIATION, "performance review, KPI, bonus"),

    // Escape-related
    LEAVE_PLAN(AgentIntent.ESCAPE, "resignation, layoff, offboarding"),
    LABOR_DISPUTE(AgentIntent.ESCAPE, "labor dispute, labor arbitration"),
    HANDOVER(AgentIntent.ESCAPE, "work handover, transition"),

    // Consultation
    CONSULTATION(AgentIntent.CONSULTATION, "booking expert consultation"),

    // Data query
    QUERY_DATA(AgentIntent.DATA_QUERY, "data query, metrics lookup, report viewing"),

    // Career general
    CAREER_GENERAL(AgentIntent.GENERAL, "career advice, workplace relationships, planning"),
    EMOTIONAL_SUPPORT(AgentIntent.GENERAL, "workplace stress, emotional support"),

    // Unknown
    UNKNOWN(AgentIntent.GENERAL, "");

    private final AgentIntent agentIntent;
    private final String description;

    NluIntent(AgentIntent agentIntent, String description) {
        this.agentIntent = agentIntent;
        this.description = description;
    }

    /** Map to the coarser AgentIntent for downstream routing. */
    public AgentIntent toAgentIntent() {
        return agentIntent;
    }

    public String getDescription() {
        return description;
    }
}
