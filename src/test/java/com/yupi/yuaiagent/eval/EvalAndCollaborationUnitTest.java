package com.yupi.yuaiagent.eval;

import com.yupi.yuaiagent.agent.collaboration.ExpertOpinion;
import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.agent.ResultAggregator;
import com.yupi.yuaiagent.agent.output.FormatterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Offline unit tests for eval scoring + debate merge (no Spring / no LLM).
 */
class EvalAndCollaborationUnitTest {

    @Test
    void routingSuite_keywordRouter_meetsGate() {
        EvalScorer scorer = new EvalScorer();
        String[][] cases = {
                {"帮我优化一下简历，突出项目经验", "RESUME"},
                {"年底想找老板谈薪，怎么准备", "NEGOTIATION"},
                {"准备离职了，交接清单怎么写", "ESCAPE"},
                {"我想预约一位职业顾问咨询", "CONSULTATION"},
                {"帮我准备明天的模拟面试", "GENERAL"},
        };
        int pass = 0;
        for (String[] c : cases) {
            EvalCase ec = EvalCase.builder()
                    .caseId(c[1])
                    .input(c[0])
                    .expectedIntent(c[1])
                    .scoringRule("ROUTING")
                    .passThreshold(1.0)
                    .build();
            EvalScorer.ScoreResult r = scorer.scoreRouting(ec);
            if (r.score() >= 1.0) pass++;
            else fail("routing miss: input=" + c[0] + " feedback=" + r.feedback());
        }
        assertEquals(cases.length, pass);
    }

    @Test
    void ambiguous_defersToNlu() {
        EvalScorer scorer = new EvalScorer();
        EvalCase ec = EvalCase.builder()
                .input("你好")
                .expectedIntent("NLU")
                .scoringRule("ROUTING")
                .passThreshold(1.0)
                .build();
        assertEquals(1.0, scorer.scoreRouting(ec).score());
    }

    @Test
    void debateMerge_picksLongestAsLeadVote() {
        ResultAggregator agg = new ResultAggregator(new FormatterRegistry(), null);
        List<ExpertOpinion> opinions = List.of(
                ExpertOpinion.ok(AgentIntent.RESUME, "短", 10),
                ExpertOpinion.ok(AgentIntent.NEGOTIATION, "这是一段更长的谈薪建议，包含市场数据和话术。", 20)
        );
        String merged = agg.synthesizeDebate("既要改简历又要谈薪", opinions);
        assertTrue(merged.contains("薪资谈判专家") || merged.contains("主投票"));
        assertTrue(merged.contains("多专家并行"));
    }

    @Test
    void keywordOverlap_scoresPartial() {
        double score = EvalScorer.keywordOverlap("简历 STAR 量化", "建议用STAR法则量化简历成果");
        assertTrue(score >= 0.5, "overlap=" + score);
    }
}
