package io.openaev.scheduler.jobs;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.specification.InjectSpecification;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tests for the recurring atomic testing job, closely modeled on {@link ScenarioExecutionJobTest}:
 * a due occurrence relaunches the atomic testing (duplicate + queue new + delete old, recurrence
 * carried over), an in-progress run dedups, and outdated recurrences self-clear.
 *
 * <p>Deliberately NOT {@code @Transactional}: the job opens its own transactions through {@code
 * TenantScopedTransaction}, whose {@code execute} refuses to run inside an active one. Everything
 * is committed, so each test sweeps its own rows in {@link #cleanup()} (relaunch duplicates are new
 * rows the composers never saw, hence the unique title prefix).
 */
@SpringBootTest
@TestInstance(PER_CLASS)
class AtomicTestingExecutionJobTest extends IntegrationTest {

  @Autowired private AtomicTestingExecutionJob job;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private InjectRepository injectRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private EndpointRepository endpointRepository;

  private final String titlePrefix = "AtomicTestingExecutionJobTest-" + UUID.randomUUID();

  @AfterEach
  void cleanup() {
    // Relaunch duplicates are new committed rows the composers never saw: sweep by title prefix.
    List<Inject> leftovers =
        injectRepository.findAll(InjectSpecification.isAtomicTesting()).stream()
            .filter(
                inject -> inject.getTitle() != null && inject.getTitle().startsWith(titlePrefix))
            .toList();
    injectRepository.deleteAll(leftovers);
    agentRepository.deleteAll(
        agentRepository.findAllById(
            agentComposer.generatedItems.stream().map(Agent::getId).toList()));
    endpointRepository.deleteAll(
        endpointRepository.findAllById(
            endpointComposer.generatedItems.stream().map(Asset::getId).toList()));
    injectComposer.reset();
    injectStatusComposer.reset();
    agentComposer.reset();
    endpointComposer.reset();
  }

  private Inject persistAtomicTesting(
      String recurrence,
      @Nullable Instant recurrenceStart,
      @Nullable Instant recurrenceEnd,
      @Nullable InjectStatus injectStatus) {
    Inject inject = InjectFixture.getDefaultInject();
    inject.setTitle(titlePrefix + " " + UUID.randomUUID());
    inject.setRecurrence(recurrence);
    inject.setRecurrenceStart(recurrenceStart);
    inject.setRecurrenceEnd(recurrenceEnd);
    InjectComposer.Composer composer =
        injectComposer
            .forInject(inject)
            .withEndpoint(
                endpointComposer
                    .forEndpoint(EndpointFixture.createEndpoint())
                    .withAgent(
                        agentComposer.forAgent(
                            AgentFixture.createDefaultAgentSession(
                                executorFixture.getDefaultExecutor()))));
    if (injectStatus != null) {
      composer = composer.withInjectStatus(injectStatusComposer.forInjectStatus(injectStatus));
    }
    return composer.persist().get();
  }

  private List<Inject> atomicTestingsWithRecurrence(String recurrence) {
    return injectRepository
        .findAll(InjectSpecification.isAtomicTesting().and(InjectSpecification.isRecurring()))
        .stream()
        .filter(inject -> recurrence.equals(inject.getRecurrence()))
        .toList();
  }

  /** Daily cron at now + the given amount of minutes (UTC). */
  private String dailyCronInMinutes(int minutes) {
    ZonedDateTime target = ZonedDateTime.now(ZoneId.of("UTC")).plusMinutes(minutes);
    return "0 " + target.getMinute() + " " + target.getHour() + " * * *";
  }

  @Test
  @DisplayName("Not relaunch a recurring atomic testing due in one hour")
  void given_cron_in_one_hour_should_not_relaunch() throws JobExecutionException {
    String cron = dailyCronInMinutes(60);
    Inject inject = persistAtomicTesting(cron, null, null, null);

    // -- EXECUTE --
    job.execute(null);

    // -- ASSERT: untouched --
    List<Inject> injects = atomicTestingsWithRecurrence(cron);
    assertThat(injects)
        .singleElement()
        .satisfies(i -> assertThat(i.getId()).isEqualTo(inject.getId()));
  }

  @Test
  @DisplayName("Relaunch a recurring atomic testing due now, carrying the recurrence over")
  void given_cron_in_one_minute_should_relaunch() throws JobExecutionException {
    String cron = dailyCronInMinutes(1);
    // Truncate to seconds: PostgreSQL timestamps keep microseconds, not nanoseconds.
    Instant recurrenceEnd = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Inject inject = persistAtomicTesting(cron, null, recurrenceEnd, null);
    String originalId = inject.getId();

    // -- EXECUTE --
    job.execute(null);

    // -- ASSERT: old inject replaced by a queued duplicate with the schedule carried over --
    assertThat(injectRepository.findById(originalId)).isEmpty();
    List<Inject> injects = atomicTestingsWithRecurrence(cron);
    assertThat(injects)
        .singleElement()
        .satisfies(
            relaunched -> {
              assertThat(relaunched.getId()).isNotEqualTo(originalId);
              assertThat(relaunched.getRecurrence()).isEqualTo(cron);
              assertThat(relaunched.getRecurrenceEnd()).isEqualTo(recurrenceEnd);
              assertThat(relaunched.getStatus())
                  .hasValueSatisfying(
                      status -> assertThat(status.getName()).isEqualTo(ExecutionStatus.QUEUING));
            });
  }

  @Test
  @DisplayName("Not relaunch twice within the same occurrence (queued run dedup)")
  void given_cron_in_one_minute_should_not_relaunch_twice() throws JobExecutionException {
    String cron = dailyCronInMinutes(1);
    persistAtomicTesting(cron, null, null, null);

    // -- EXECUTE --
    job.execute(null);
    String relaunchedId =
        atomicTestingsWithRecurrence(cron).stream().map(Inject::getId).findFirst().orElseThrow();

    // -- EXECUTE AGAIN: the relaunched inject is QUEUING, dedup must skip it --
    job.execute(null);

    // -- ASSERT: same inject, not relaunched a second time --
    assertThat(atomicTestingsWithRecurrence(cron))
        .singleElement()
        .satisfies(i -> assertThat(i.getId()).isEqualTo(relaunchedId));
  }

  @Test
  @DisplayName("Skip an atomic testing whose run is already in progress")
  void given_run_in_progress_should_not_relaunch() throws JobExecutionException {
    String cron = dailyCronInMinutes(1);
    Inject inject =
        persistAtomicTesting(cron, null, null, InjectStatusFixture.createPendingInjectStatus());

    // -- EXECUTE --
    job.execute(null);

    // -- ASSERT: untouched --
    List<Inject> injects = atomicTestingsWithRecurrence(cron);
    assertThat(injects)
        .singleElement()
        .satisfies(i -> assertThat(i.getId()).isEqualTo(inject.getId()));
  }

  @Test
  @DisplayName("Self-clear the recurrence when the end date has passed")
  void given_end_date_in_the_past_should_clear_recurrence() throws JobExecutionException {
    String cron = dailyCronInMinutes(1);
    Inject inject =
        persistAtomicTesting(
            cron,
            Instant.now().minus(10, ChronoUnit.DAYS),
            Instant.now().minus(1, ChronoUnit.DAYS),
            null);

    // -- EXECUTE --
    job.execute(null);

    // -- ASSERT: inject kept, schedule cleared, no relaunch --
    Inject reloaded = injectRepository.findById(inject.getId()).orElseThrow();
    assertThat(reloaded.getRecurrence()).isNull();
    assertThat(reloaded.getRecurrenceStart()).isNull();
    assertThat(reloaded.getRecurrenceEnd()).isNull();
    assertThat(atomicTestingsWithRecurrence(cron)).isEmpty();
  }

  @Test
  @DisplayName("Ignore a recurrence expression that cannot be handled")
  void given_unhandled_expression_should_do_nothing() throws JobExecutionException {
    String cron = "can not handle this expression!";
    Inject inject = persistAtomicTesting(cron, null, null, null);

    // -- EXECUTE --
    job.execute(null);

    // -- ASSERT: untouched --
    List<Inject> injects = atomicTestingsWithRecurrence(cron);
    assertThat(injects)
        .singleElement()
        .satisfies(i -> assertThat(i.getId()).isEqualTo(inject.getId()));
  }
}
