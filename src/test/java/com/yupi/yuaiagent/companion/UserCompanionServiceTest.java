package com.yupi.yuaiagent.companion;

import com.yupi.yuaiagent.repository.entity.UserCompanionEntity;
import com.yupi.yuaiagent.repository.jpa.UserCompanionJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserCompanionServiceTest {

    @Test
    void updateMergesStylePreferencesWithoutDroppingPetSettings() {
        UserCompanionJpaRepository repository = mock(UserCompanionJpaRepository.class);
        UserCompanionEntity existing = new UserCompanionEntity();
        existing.setUserId("user-1");
        existing.setDisplayName("领航员");
        existing.setVersion(2);
        existing.setStylePrefs(new HashMap<>(Map.of(
                "tone", "简洁直接",
                "focus", "简历",
                "pet", Map.of("enabled", true, "motion", "full")
        )));
        when(repository.findByUserId("user-1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserCompanionService service = new UserCompanionService(repository);

        UserCompanionService.UserCompanionView updated = service.update(
                "user-1",
                new UserCompanionService.UpdateCompanionRequest(
                        null,
                        null,
                        Map.of("focus", "谈薪"),
                        null
                )
        );

        assertThat(updated.stylePrefs())
                .containsEntry("tone", "简洁直接")
                .containsEntry("focus", "谈薪");
        assertThat(petMap(updated))
                .containsEntry("enabled", true)
                .containsEntry("skin", "cat")
                .containsEntry("motion", "full")
                .containsEntry("bubbleLevel", "key");
        assertThat(worldMap(updated))
                .containsEntry("presence", "onChair")
                .containsEntry("chair", "wood")
                .containsEntry("rug", "plain")
                .containsEntry("gifts", List.of());
    }

    @Test
    void updateMergesPartialPetPreferencesWithoutDroppingSiblingSettings() {
        UserCompanionJpaRepository repository = mock(UserCompanionJpaRepository.class);
        UserCompanionEntity existing = new UserCompanionEntity();
        existing.setUserId("user-2");
        existing.setDisplayName("领航员");
        existing.setVersion(1);
        existing.setStylePrefs(new HashMap<>(Map.of(
                "pet", Map.of(
                        "enabled", true,
                        "skin", "pilot",
                        "motion", "full",
                        "bubbleLevel", "key"
                )
        )));
        when(repository.findByUserId("user-2")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserCompanionService service = new UserCompanionService(repository);

        UserCompanionService.UserCompanionView updated = service.update(
                "user-2",
                new UserCompanionService.UpdateCompanionRequest(
                        null,
                        null,
                        Map.of("pet", Map.of("motion", "reduced")),
                        null
                )
        );

        assertThat(petMap(updated))
                .containsEntry("enabled", true)
                .containsEntry("skin", "pilot")
                .containsEntry("motion", "reduced")
                .containsEntry("bubbleLevel", "key");
        assertThat(worldMap(updated)).containsEntry("presence", "onChair");
    }

    @Test
    void updateRejectsLockedSkinAndKeepsLaterWorldSlots() {
        UserCompanionJpaRepository repository = mock(UserCompanionJpaRepository.class);
        UserCompanionEntity existing = new UserCompanionEntity();
        existing.setUserId("user-3");
        existing.setDisplayName("领航员");
        existing.setVersion(1);
        existing.setStylePrefs(new HashMap<>(Map.of(
                "pet", Map.of(
                        "enabled", true,
                        "skin", "cat",
                        "motion", "full",
                        "bubbleLevel", "key",
                        "world", Map.of(
                                "presence", "onChair",
                                "chair", "wood",
                                "rug", "plain",
                                "gifts", List.of(Map.of("id", "yarn"))
                        )
                )
        )));
        when(repository.findByUserId("user-3")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserCompanionService service = new UserCompanionService(repository);

        UserCompanionService.UserCompanionView updated = service.update(
                "user-3",
                new UserCompanionService.UpdateCompanionRequest(
                        null,
                        null,
                        Map.of("pet", Map.of(
                                "skin", "panda",
                                "world", Map.of("presence", "away")
                        )),
                        null
                )
        );

        assertThat(petMap(updated)).containsEntry("skin", "cat");
        assertThat(worldMap(updated))
                .containsEntry("presence", "away")
                .containsEntry("gifts", List.of(Map.of("id", "yarn")));
    }

    @Test
    void defaultCompanionIncludesAccessiblePetPreferences() {
        UserCompanionJpaRepository repository = mock(UserCompanionJpaRepository.class);
        when(repository.findByUserId("user-2")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserCompanionService service = new UserCompanionService(repository);

        UserCompanionService.UserCompanionView companion = service.getOrClaim("user-2");

        assertThat(petMap(companion))
                .containsEntry("enabled", true)
                .containsEntry("skin", "cat")
                .containsEntry("motion", "full")
                .containsEntry("bubbleLevel", "key");
        assertThat(worldMap(companion))
                .containsEntry("presence", "onChair")
                .containsEntry("chair", "wood")
                .containsEntry("rug", "plain")
                .containsEntry("gifts", List.of());
    }

    @Test
    void updateCapsGiftInventoryAndDropsInvalidItems() {
        UserCompanionJpaRepository repository = mock(UserCompanionJpaRepository.class);
        UserCompanionEntity existing = new UserCompanionEntity();
        existing.setUserId("user-4");
        existing.setDisplayName("领航员");
        existing.setVersion(1);
        existing.setStylePrefs(new HashMap<>(Map.of("pet", Map.of("skin", "cat"))));
        when(repository.findByUserId("user-4")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserCompanionService service = new UserCompanionService(repository);

        List<Map<String, Object>> gifts = new ArrayList<>();
        gifts.add(Map.of("name", "no-id"));
        for (int i = 0; i < 60; i++) {
            gifts.add(Map.of("id", "gift-" + i));
        }
        gifts.add(Map.of("id", "gift-0"));

        UserCompanionService.UserCompanionView updated = service.update(
                "user-4",
                new UserCompanionService.UpdateCompanionRequest(
                        null,
                        null,
                        Map.of("pet", Map.of("world", Map.of("gifts", gifts))),
                        null
                )
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> saved = (List<Map<String, Object>>) worldMap(updated).get("gifts");
        assertThat(saved).hasSize(50);
        assertThat(saved.getFirst()).containsEntry("id", "gift-0");
        assertThat(saved.getLast()).containsEntry("id", "gift-49");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> petMap(UserCompanionService.UserCompanionView view) {
        return (Map<String, Object>) view.stylePrefs().get("pet");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> worldMap(UserCompanionService.UserCompanionView view) {
        return (Map<String, Object>) petMap(view).get("world");
    }
}
