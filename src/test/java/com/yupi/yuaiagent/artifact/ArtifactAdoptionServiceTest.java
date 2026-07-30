package com.yupi.yuaiagent.artifact;

import com.yupi.yuaiagent.artifact.adoption.ArtifactAdoption;
import com.yupi.yuaiagent.artifact.adoption.ArtifactAdoptionRepository;
import com.yupi.yuaiagent.artifact.adoption.ArtifactAdoptionService;
import com.yupi.yuaiagent.artifact.adoption.ArtifactAdoptionStage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ArtifactAdoptionServiceTest {

    @Test
    void adoptedLedgerIsIdempotentPerArtifactAndTurn() {
        ArtifactAdoptionRepository repository = mock(ArtifactAdoptionRepository.class);
        ArtifactAdoption existing = adoption("a1", "turn-1", ArtifactAdoptionStage.ADOPTED);
        when(repository.findByArtifactIdAndTurnIdAndStage(
                "a1", "turn-1", ArtifactAdoptionStage.ADOPTED))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArtifactAdoptionService service = new ArtifactAdoptionService(repository);

        service.recordAdopted(List.of("a1"), "GENERAL", "c1", "turn-1", 1.0, "citation");
        service.recordAdopted(List.of("a1"), "GENERAL", "c1", "turn-1", 1.0, "citation");

        verify(repository, times(1)).save(any());
    }

    @Test
    void emptyBatchCountsAvoidDatabaseCalls() {
        ArtifactAdoptionRepository repository = mock(ArtifactAdoptionRepository.class);
        ArtifactAdoptionService service = new ArtifactAdoptionService(repository);

        assertEquals(0, service.offeredCounts(List.of()).size());
        assertEquals(0, service.adoptedCounts(List.of()).size());
        verifyNoInteractions(repository);
    }

    private ArtifactAdoption adoption(String artifactId, String turnId, ArtifactAdoptionStage stage) {
        ArtifactAdoption adoption = new ArtifactAdoption();
        adoption.setArtifactId(artifactId);
        adoption.setTurnId(turnId);
        adoption.setStage(stage);
        adoption.setConsumerAgent("GENERAL");
        return adoption;
    }
}
