package com.yupi.yuaiagent.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.artifact.adoption.ArtifactCitationExtractor;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactScope;
import com.yupi.yuaiagent.artifact.model.ArtifactStatus;
import com.yupi.yuaiagent.artifact.recall.ArtifactRecallService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ArtifactClosureTest {

    @Test
    void publisherAcceptsReusableStructuredArtifactAndPublishesIt() {
        ArtifactShelf shelf = mock(ArtifactShelf.class);
        ArtifactTypeCatalog catalog = ArtifactTypeCatalog.defaults();
        ArtifactPublishPolicy policy = new ArtifactPublishPolicy(catalog, new ObjectMapper());
        ArtifactPublisher publisher = new ArtifactPublisher(shelf, policy);
        Artifact draft = Artifact.builder()
                .userId("u1")
                .chatId("c1")
                .type("PROMOTION_PLAN")
                .title("晋升路径规划")
                .content("{\"summary\":\"目标是晋升高级工程师\",\"actionItems\":[\"完成架构项目\"]}")
                .scope(ArtifactScope.TASK)
                .build();
        when(shelf.put(any())).thenAnswer(invocation -> {
            Artifact artifact = invocation.getArgument(0);
            artifact.setArtifactId("a1");
            return ArtifactShelf.PutResult.ok(artifact);
        });
        when(shelf.findByDedupKey(any())).thenReturn(Optional.empty());

        ArtifactShelf.PutResult result = publisher.publish(draft, "trace-1");

        assertTrue(result.success());
        ArgumentCaptor<Artifact> captor = ArgumentCaptor.forClass(Artifact.class);
        verify(shelf).put(captor.capture());
        Artifact published = captor.getValue();
        assertEquals(ArtifactStatus.PUBLISHED, published.getStatus());
        assertTrue(published.isReusable());
        assertEquals("目标是晋升高级工程师", published.getSummary());
        assertTrue(published.getTargetAgents().contains("GENERAL"));
        assertNotNull(published.getDedupKey());
    }

    @Test
    void publisherRejectsProcessLogsAndInvalidJson() {
        ArtifactShelf shelf = mock(ArtifactShelf.class);
        ArtifactPublisher publisher = new ArtifactPublisher(
                shelf, new ArtifactPublishPolicy(ArtifactTypeCatalog.defaults(), new ObjectMapper()));

        Artifact processLog = Artifact.builder()
                .userId("u1").chatId("c1").type("AGENT_HANDOFF")
                .title("交接").content("{}").scope(ArtifactScope.TASK).build();
        Artifact invalid = Artifact.builder()
                .userId("u1").chatId("c1").type("PROMOTION_PLAN")
                .title("规划").content("not-json").scope(ArtifactScope.TASK).build();

        assertFalse(publisher.publish(processLog, null).success());
        assertFalse(publisher.publish(invalid, null).success());
        verifyNoInteractions(shelf);
    }

    @Test
    void publishPolicyCanonicalizesJsonForDeduplication() {
        ArtifactPublishPolicy policy = new ArtifactPublishPolicy(
                ArtifactTypeCatalog.defaults(), new ObjectMapper());
        Artifact first = Artifact.builder()
                .userId("u1").chatId("c1").producer("晋升规划师")
                .type("PROMOTION_PLAN").title("规划")
                .content("{\"summary\":\"晋升\",\"actionItems\":[\"A\"]}")
                .scope(ArtifactScope.TASK).build();
        Artifact reordered = Artifact.builder()
                .userId("u1").chatId("c1").producer("晋升规划师")
                .type("PROMOTION_PLAN").title("规划")
                .content("{ \"actionItems\" : [\"A\"], \"summary\" : \"晋升\" }")
                .scope(ArtifactScope.TASK).build();

        var firstDecision = policy.evaluate(first, null);
        var reorderedDecision = policy.evaluate(reordered, null);

        assertTrue(firstDecision.accepted());
        assertTrue(reorderedDecision.accepted());
        assertEquals(first.getDedupKey(), reordered.getDedupKey());
    }

    @Test
    void recallUsesBothScopesAndKeepsArtifactsReusable() {
        ArtifactShelf shelf = mock(ArtifactShelf.class);
        Artifact task = artifact("task", ArtifactScope.TASK, "GENERAL", "晋升行动计划");
        Artifact profile = artifact("profile", ArtifactScope.USER_PROFILE, "GENERAL", "技术负责人目标");
        when(shelf.query(any())).thenReturn(List.of(task), List.of(profile));
        ArtifactRecallService service = new ArtifactRecallService(
                shelf, ArtifactTypeCatalog.defaults(), 3, 500);

        var result = service.recall("u1", "c2", "GENERAL", "我想制定晋升行动计划");

        assertEquals(2, result.offeredArtifactIds().size());
        assertTrue(result.injectionText().contains("[A1]"));
        assertTrue(result.injectionText().length() <= 500);
        assertEquals(ArtifactStatus.PUBLISHED, task.getStatus());
        verify(shelf, never()).markConsumed(any());
        verify(shelf, times(2)).query(any());
    }

    @Test
    void citationExtractorReturnsAdoptedIdsAndRemovesMachineMarker() {
        ArtifactCitationExtractor extractor = new ArtifactCitationExtractor();

        var result = extractor.extract(
                "建议按季度推进。\n<!--artifact-used:[a1,a3,unknown]-->",
                List.of("a1", "a2", "a3"));

        assertEquals("建议按季度推进。", result.cleanText());
        assertEquals(List.of("a1", "a3"), result.adoptedArtifactIds());
    }

    private Artifact artifact(String id, ArtifactScope scope, String targetAgent, String summary) {
        return Artifact.builder()
                .artifactId(id)
                .userId("u1")
                .chatId(scope == ArtifactScope.TASK ? "c2" : null)
                .type("PROMOTION_PLAN")
                .title("晋升规划")
                .summary(summary)
                .content("{\"summary\":\"" + summary + "\"}")
                .scope(scope)
                .status(ArtifactStatus.PUBLISHED)
                .reusable(true)
                .targetAgents(List.of(targetAgent))
                .build();
    }
}
