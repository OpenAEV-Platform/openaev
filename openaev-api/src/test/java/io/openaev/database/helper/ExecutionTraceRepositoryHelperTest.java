package io.openaev.database.helper;

import static io.openaev.utils.fixtures.ExecutionTraceFixture.createDefaultExecutionTrace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("ExecutionTraceRepositoryHelper integration tests")
class ExecutionTraceRepositoryHelperTest extends IntegrationTest {

  @Autowired private ExecutionTraceRepositoryHelper helper;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExecutionTraceRepository executionTraceRepository;
  @Autowired private InjectStatusRepository injectStatusRepository;
  @Autowired private InjectRepository injectRepository;

  @Test
  @DisplayName("saveExecutionTrace should persist trace in database")
  void given_validTrace_should_persistInDatabase() {
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

    ExecutionTrace trace = createDefaultExecutionTrace(List.of("asset-1", "asset-2"));
    trace.setInjectStatus(injectStatus);

    // Act
    String id = helper.saveExecutionTrace(trace);

    // Assert
    entityManager.flush();
    entityManager.clear();

    Optional<ExecutionTrace> persisted = executionTraceRepository.findById(id);
    assertThat(persisted).isPresent();
    assertThat(persisted.get().getMessage()).isEqualTo("Test execution completed");
    assertThat(persisted.get().getAction()).isEqualTo(ExecutionTraceAction.EXECUTION);
    assertThat(persisted.get().getStatus()).isEqualTo(ExecutionTraceStatus.SUCCESS);
    assertThat(persisted.get().getIdentifiers()).containsExactly("asset-1", "asset-2");
  }

  @Test
  @DisplayName("updateInjectStatus should update status name and end date")
  void given_existingStatus_should_updateNameAndEndDate() {
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
    Instant endDate = Instant.now();

    // Act
    helper.updateInjectStatus(injectStatus.getId(), "SUCCESS", endDate);

    // Assert
    entityManager.clear();

    Optional<InjectStatus> updated = injectStatusRepository.findById(injectStatus.getId());
    assertThat(updated).isPresent();
    assertThat(updated.get().getName()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(updated.get().getTrackingEndDate()).isNotNull();
  }

  @Test
  @DisplayName("updateInjectUpdateDate should update inject timestamp")
  void given_existingInject_should_updateTimestamp() {
    // Arrange
    Inject inject = injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
    Instant newTimestamp = Instant.now();

    // Act
    helper.updateInjectUpdateDate(inject.getId(), newTimestamp);

    // Assert
    entityManager.clear();

    Optional<Inject> updated = injectRepository.findById(inject.getId());
    assertThat(updated).isPresent();
    assertThat(updated.get().getUpdatedAt()).isNotNull();
  }
}
