package com.yupi.yuaiagent.repository;

import com.yupi.yuaiagent.agent.model.Appointment;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 4: Appointment Persistence Round-Trip
 *
 * <p>For any valid Appointment entity, saving it to the store and then retrieving it SHALL
 * produce an equivalent entity with all fields preserved.</p>
 *
 * <p>The repository persists appointments to a JSON file under a configurable directory
 * ({@code appointment.storage.dir}, default {@code ./tmp/appointments}) and initializes via a
 * {@code @PostConstruct init()}. To exercise a genuine persistence round-trip (JSON
 * serialization + deserialization) — rather than the in-memory cache — each scenario:
 * <ol>
 *   <li>writes the appointment with a first repository instance, then</li>
 *   <li>reloads it from the same file with a second, freshly initialized repository instance.</li>
 * </ol>
 * Each scenario uses an isolated temporary directory that is cleaned up afterwards, so the test
 * never touches real data.</p>
 *
 * <p><b>Validates: Requirements 2.7</b></p>
 */
class AppointmentRepositoryPropertyTest {

    /**
     * Round-trip: a saved Appointment, reloaded from its persisted JSON file, equals the saved
     * record with every field preserved. Uses a second repository instance to ensure the data
     * survives serialization to and deserialization from disk (not just the in-memory cache).
     *
     * <p><b>Validates: Requirements 2.7</b></p>
     */
    @Property(tries = 200)
    void savedAppointmentIsPreservedAcrossPersistenceRoundTrip(
            @ForAll("appointments") Appointment appointment) throws IOException {
        Path storageDir = Files.createTempDirectory("appt-roundtrip-");
        try {
            // 1. Persist with a first repository instance.
            AppointmentRepository writer = newInitializedRepository(storageDir);
            Appointment saved = writer.save(appointment);
            String id = saved.getAppointmentId();

            // The repository SHALL assign an id and timestamps on save.
            assertThat(id).as("save must assign a non-empty appointmentId").isNotNull().isNotEmpty();
            assertThat(saved.getCreatedAt()).as("save must set createdAt").isNotNull();
            assertThat(saved.getUpdatedAt()).as("save must set updatedAt").isNotNull();

            // 2. Reload with a second repository instance that reads the persisted JSON file.
            AppointmentRepository reader = newInitializedRepository(storageDir);
            Optional<Appointment> reloaded = reader.findById(id);

            // 3. The round-trip SHALL yield an equivalent entity with all fields preserved.
            assertThat(reloaded)
                    .as("appointment %s must be retrievable after persistence round-trip", id)
                    .isPresent();

            Appointment result = reloaded.get();
            assertThat(result)
                    .as("reloaded appointment must equal the saved appointment in all fields")
                    .isEqualTo(saved);

            // Explicit per-field checks for the fields called out by Requirement 2.7.
            assertThat(result.getAppointmentId()).isEqualTo(saved.getAppointmentId());
            assertThat(result.getChatId()).isEqualTo(saved.getChatId());
            assertThat(result.getName()).isEqualTo(saved.getName());
            assertThat(result.getContact()).isEqualTo(saved.getContact());
            assertThat(result.getAppointmentTime()).isEqualTo(saved.getAppointmentTime());
            assertThat(result.getTopic()).isEqualTo(saved.getTopic());
            assertThat(result.getRemark()).isEqualTo(saved.getRemark());
            assertThat(result.getCalendarEventId()).isEqualTo(saved.getCalendarEventId());
            assertThat(result.getCalendarLink()).isEqualTo(saved.getCalendarLink());
            assertThat(result.getCalendarProvider()).isEqualTo(saved.getCalendarProvider());
            assertThat(result.getStatus()).isEqualTo(saved.getStatus());
            assertThat(result.getCreatedAt()).isEqualTo(saved.getCreatedAt());
            assertThat(result.getUpdatedAt()).isEqualTo(saved.getUpdatedAt());
        } finally {
            deleteRecursively(storageDir);
        }
    }

    /**
     * Builds an {@link AppointmentRepository} wired to an isolated temporary storage directory
     * (bypassing Spring's {@code @Value} injection via reflection) and runs its
     * {@code @PostConstruct} initializer so it loads from / writes to that directory only.
     */
    private AppointmentRepository newInitializedRepository(Path storageDir) {
        AppointmentRepository repository = new AppointmentRepository();
        ReflectionTestUtils.setField(repository, "storageDir", storageDir.toString());
        repository.init();
        return repository;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup of the temporary directory.
                }
            });
        }
    }

    // --- Generators -----------------------------------------------------------------------

    /**
     * Generates arbitrary, valid Appointment entities covering the full field set:
     * random name / contact / appointmentTime / topic / remark / calendarEventId / status,
     * plus chatId, calendarLink, calendarProvider and an optional pre-set appointmentId.
     * Nulls are injected so round-trip preservation of absent fields is also exercised.
     */
    @Provide
    Arbitrary<Appointment> appointments() {
        Arbitrary<String> texts = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 -_@.")
                .withCharRange('\u4e00', '\u9fff') // include CJK characters
                .ofMaxLength(40)
                .injectNull(0.1);

        // appointmentId: sometimes null/blank (repo auto-generates), sometimes a pre-set value.
        Arbitrary<String> ids = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(24));

        Arbitrary<LocalDateTime> dateTimes = Combinators.combine(
                        Arbitraries.integers().between(2000, 2100),
                        Arbitraries.integers().between(1, 12),
                        Arbitraries.integers().between(1, 28),
                        Arbitraries.integers().between(0, 23),
                        Arbitraries.integers().between(0, 59),
                        Arbitraries.integers().between(0, 59),
                        Arbitraries.integers().between(0, 999_999_999))
                .as(LocalDateTime::of)
                .injectNull(0.1);

        Arbitrary<Appointment.AppointmentStatus> statuses =
                Arbitraries.of(Appointment.AppointmentStatus.class).injectNull(0.1);
        Arbitrary<Appointment.CalendarProvider> providers =
                Arbitraries.of(Appointment.CalendarProvider.class).injectNull(0.1);

        // jqwik's Combinators.combine supports up to 8 arbitraries, so build the entity in two
        // stages: first the textual/time core into a partial builder, then the remaining fields.
        Arbitrary<Appointment.AppointmentBuilder> partial = Combinators.combine(
                        ids, texts, texts, texts, dateTimes, texts, texts, texts)
                .as((id, chatId, name, contact, time, topic, remark, eventId) ->
                        Appointment.builder()
                                .appointmentId(id)
                                .chatId(chatId)
                                .name(name)
                                .contact(contact)
                                .appointmentTime(time)
                                .topic(topic)
                                .remark(remark)
                                .calendarEventId(eventId));

        return Combinators.combine(partial, texts, providers, statuses)
                .as((builder, link, provider, status) -> builder
                        .calendarLink(link)
                        .calendarProvider(provider)
                        .status(status)
                        .build());
    }
}
