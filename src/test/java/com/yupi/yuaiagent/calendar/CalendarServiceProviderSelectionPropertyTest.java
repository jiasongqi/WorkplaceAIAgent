package com.yupi.yuaiagent.calendar;

import com.yupi.yuaiagent.agent.model.Appointment;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 3: Calendar Service Provider Selection
 *
 * <p>For any configured CalendarProvider value (FEISHU or DINGTALK), the system SHALL use the
 * corresponding calendar service implementation when creating appointments.</p>
 *
 * <p>The selection contract is implemented by {@link CalendarServiceFactory}, which builds a
 * provider -> implementation map from each {@link CalendarService#getProvider()} and resolves
 * requests either by an explicit provider argument or by the {@code calendar.provider}
 * configuration string. These tests construct the factory with stub {@link CalendarService}
 * implementations and assert the selection mapping holds for every provider value.</p>
 *
 * <p><b>Validates: Requirements 2.5, 2.6</b></p>
 */
class CalendarServiceProviderSelectionPropertyTest {

    /**
     * Generator over the complete set of valid CalendarProvider values (FEISHU, DINGTALK).
     */
    @Provide
    Arbitrary<Appointment.CalendarProvider> providers() {
        return Arbitraries.of(Appointment.CalendarProvider.values());
    }

    /**
     * Selection mapping by explicit provider: for any provider, the factory returns the
     * implementation whose {@code getProvider()} matches the requested provider. The order in
     * which implementations are registered (varied by {@code reversed}) must not affect the
     * mapping.
     *
     * <p><b>Validates: Requirements 2.5, 2.6</b></p>
     */
    @Property
    void factoryReturnsImplementationMatchingRequestedProvider(
            @ForAll("providers") Appointment.CalendarProvider provider,
            @ForAll boolean reversed) {
        CalendarServiceFactory factory = new CalendarServiceFactory(buildStubServices(reversed));

        CalendarService selected = factory.getCalendarService(provider);

        assertThat(selected)
                .as("a service implementation must be selected for provider %s", provider)
                .isNotNull();
        assertThat(selected.getProvider())
                .as("selected implementation's provider must equal the requested provider")
                .isEqualTo(provider);
    }

    /**
     * Selection mapping by configuration string: for any provider, setting
     * {@code calendar.provider} to that provider's name causes the no-arg
     * {@code getCalendarService()} to resolve to the matching implementation. This mirrors the
     * runtime {@code @ConditionalOnProperty}/config-driven selection of FEISHU vs DINGTALK.
     *
     * <p><b>Validates: Requirements 2.5, 2.6</b></p>
     */
    @Property
    void factorySelectsImplementationByConfiguredProviderName(
            @ForAll("providers") Appointment.CalendarProvider provider,
            @ForAll boolean reversed) {
        CalendarServiceFactory factory = new CalendarServiceFactory(buildStubServices(reversed));
        ReflectionTestUtils.setField(factory, "providerName", provider.name());

        CalendarService selected = factory.getCalendarService();

        assertThat(selected.getProvider())
                .as("config '%s' must resolve to the matching implementation", provider.name())
                .isEqualTo(provider);
    }

    /**
     * End-to-end selection during appointment creation: the implementation chosen for a provider
     * is the one that actually creates the event, so the resulting event is tagged with that
     * provider. This verifies the corresponding implementation is used "when creating
     * appointments".
     *
     * <p><b>Validates: Requirements 2.5, 2.6</b></p>
     */
    @Property
    void selectedImplementationCreatesEventForThatProvider(
            @ForAll("providers") Appointment.CalendarProvider provider,
            @ForAll boolean reversed) {
        CalendarServiceFactory factory = new CalendarServiceFactory(buildStubServices(reversed));
        Appointment appointment = Appointment.builder()
                .name("张三")
                .contact("13800138000")
                .appointmentTime(LocalDateTime.now().plusDays(1))
                .build();

        CalendarEvent event = factory.getCalendarService(provider).createEvent(appointment);

        assertThat(event.getProvider())
                .as("event must be created by the implementation matching the configured provider")
                .isEqualTo(provider.name());
    }

    /**
     * Builds one stub implementation per provider so the factory has a complete provider set to
     * map. The list order is optionally reversed to demonstrate the mapping is order-independent.
     */
    private List<CalendarService> buildStubServices(boolean reversed) {
        List<CalendarService> services = new ArrayList<>();
        services.add(new StubCalendarService(Appointment.CalendarProvider.FEISHU));
        services.add(new StubCalendarService(Appointment.CalendarProvider.DINGTALK));
        if (reversed) {
            java.util.Collections.reverse(services);
        }
        return services;
    }

    /**
     * Minimal {@link CalendarService} stub that reports a fixed provider and tags created/updated
     * events with that provider, so selection can be observed without real API calls.
     */
    private static final class StubCalendarService implements CalendarService {

        private final Appointment.CalendarProvider provider;

        private StubCalendarService(Appointment.CalendarProvider provider) {
            this.provider = provider;
        }

        @Override
        public CalendarEvent createEvent(Appointment appointment) {
            return CalendarEvent.builder()
                    .eventId("evt-" + provider.name())
                    .provider(provider.name())
                    .build();
        }

        @Override
        public void cancelEvent(String eventId) {
            // no-op stub
        }

        @Override
        public CalendarEvent updateEvent(String eventId, Appointment appointment) {
            return CalendarEvent.builder()
                    .eventId(eventId)
                    .provider(provider.name())
                    .build();
        }

        @Override
        public boolean checkAvailability(LocalDateTime startTime, LocalDateTime endTime) {
            return true;
        }

        @Override
        public Appointment.CalendarProvider getProvider() {
            return provider;
        }
    }
}
