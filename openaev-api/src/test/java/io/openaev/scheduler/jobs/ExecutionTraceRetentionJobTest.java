package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExecutionTraceRetentionJobTest extends IntegrationTest {

  @Autowired private EntityManager entityManager;

  @Autowired private ExecutionTraceRepository executionTraceRepository;

  @Autowired private InjectComposer injectComposer;

  @Autowired private InjectStatusComposer injectStatusComposer;

  @Autowired private ExecutionTraceRetentionJob job;

  @Test
  void should_do_nothing_when_retention_is_disabled() throws JobExecutionException {
    // -- ARRANGE --
    String injectStatusId = persistInjectWithStatus();
    String oldTraceId = insertExecutionTrace(injectStatusId, 100);
    String recentTraceId = insertExecutionTrace(injectStatusId, 5);
    ReflectionTestUtils.setField(job, "retentionDays", 0);

    // -- ACT --
    job.execute(null);

    // -- ASSERT --
    assertThat(executionTraceRepository.existsById(oldTraceId)).isTrue();
    assertThat(executionTraceRepository.existsById(recentTraceId)).isTrue();
  }

  @Test
  void should_delete_old_traces_and_keep_recent_ones() throws JobExecutionException {
    // -- ARRANGE --
    String injectStatusId = persistInjectWithStatus();
    String oldTraceId = insertExecutionTrace(injectStatusId, 100);
    String recentTraceId = insertExecutionTrace(injectStatusId, 5);
    ReflectionTestUtils.setField(job, "retentionDays", 30);

    try {
      // -- ACT --
      job.execute(null);

      // -- ASSERT --
      assertThat(executionTraceRepository.existsById(oldTraceId)).isFalse();
      assertThat(executionTraceRepository.existsById(recentTraceId)).isTrue();
    } finally {
      ReflectionTestUtils.setField(job, "retentionDays", 0);
    }
  }

  // -- PRIVATE --

  private String persistInjectWithStatus() {
    Inject inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(InjectStatusFixture.createSuccessStatus()))
            .persist()
            .get();
    entityManager.flush();
    return inject.getStatus().orElseThrow().getId();
  }

  /**
   * Create an execution trace using a native query to bypass the automatic createdAt value and
   * manually control its age in days.
   */
  private String insertExecutionTrace(String injectStatusId, int ageInDays) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            """
                INSERT INTO execution_traces (
                  execution_trace_id,
                  execution_inject_status_id,
                  execution_message,
                  execution_action,
                  execution_status,
                  execution_time,
                  execution_created_at,
                  execution_updated_at
                ) VALUES (
                  :id,
                  :injectStatusId,
                  'retention test trace',
                  'COMPLETE',
                  'EXECUTED',
                  now() - make_interval(days => :ageInDays),
                  now() - make_interval(days => :ageInDays),
                  now() - make_interval(days => :ageInDays)
                )
            """)
        .setParameter("id", id)
        .setParameter("injectStatusId", injectStatusId)
        .setParameter("ageInDays", ageInDays)
        .executeUpdate();
    entityManager.flush();
    return id;
  }
}
