package io.openaev.database.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("InjectStatusRepository integration tests")
class InjectStatusRepositoryTest extends IntegrationTest {

  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectStatusRepository injectStatusRepository;
  @Autowired private ExecutionTraceRepository executionTraceRepository;

  @BeforeEach
  void beforeEach() {
    injectComposer.reset();
    injectStatusComposer.reset();
  }

  @Test
  @DisplayName("findInjectStatusWithGlobalExecutionTraces should return traces ordered by time")
  void given_globalExecutionTracesWithUnorderedInsert_should_returnTracesOrderedByTime() {
    // Arrange
    Inject inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .persist()
            .get();

    InjectStatus injectStatus = inject.getStatus().orElseThrow();
    ExecutionTrace lateTrace =
        new ExecutionTrace(
            injectStatus,
            ExecutionTraceStatus.INFO,
            List.of(),
            "late",
            ExecutionTraceAction.START,
            null,
            Instant.parse("2026-01-01T10:00:03Z"));
    ExecutionTrace earlyTrace =
        new ExecutionTrace(
            injectStatus,
            ExecutionTraceStatus.INFO,
            List.of(),
            "early",
            ExecutionTraceAction.START,
            null,
            Instant.parse("2026-01-01T10:00:01Z"));
    ExecutionTrace middleTrace =
        new ExecutionTrace(
            injectStatus,
            ExecutionTraceStatus.INFO,
            List.of(),
            "middle",
            ExecutionTraceAction.START,
            null,
            Instant.parse("2026-01-01T10:00:02Z"));

    executionTraceRepository.saveAll(List.of(lateTrace, earlyTrace, middleTrace));
    entityManager.flush();
    entityManager.clear();

    // Act
    InjectStatus foundStatus =
        injectStatusRepository
            .findInjectStatusWithGlobalExecutionTraces(inject.getId())
            .orElseThrow();

    // Assert
    assertThat(foundStatus.getTraces())
        .extracting(ExecutionTrace::getMessage)
        .containsExactly("early", "middle", "late");
  }

  @Test
  @DisplayName("findStatusNamesByInjectIds returns one pair per inject that has a status")
  void given_injectsWithAndWithoutStatus_should_returnOnlyTheOnesWithAStatus() {
    // Arrange: one executed inject, one pending, one with no status row at all.
    Inject executed =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(InjectStatusFixture.createSuccessStatus()))
            .persist()
            .get();
    Inject pending =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .persist()
            .get();
    Inject statusless = injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
    entityManager.flush();
    entityManager.clear();

    // Act
    List<Object[]> pairs =
        injectStatusRepository.findStatusNamesByInjectIds(
            List.of(executed.getId(), pending.getId(), statusless.getId()));

    // Assert: the statusless inject is absent rather than present with a null status.
    assertThat(pairs)
        .extracting(row -> row[0] + "=" + row[1])
        .containsExactlyInAnyOrder(executed.getId() + "=EXECUTED", pending.getId() + "=PENDING");
  }
}
