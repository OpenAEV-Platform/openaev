package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.execution.ExecutionTraceScopeSeed;
import io.openaev.execution.ExecutionTraceScopeSeed.Tenant;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Not {@code @Transactional}: the job opens its own transactions under the all-tenants scope, and
 * the scoped primitive refuses to open inside an active one. The rows are committed through JDBC
 * and swept after each test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExecutionTraceRetentionJobTest extends IntegrationTest {

  @Autowired private ExecutionTraceRetentionJob job;

  @Autowired private DataSource dataSource;

  private ExecutionTraceScopeSeed seed;
  private Tenant tenant;

  @BeforeEach
  void seedATenant() {
    seed = new ExecutionTraceScopeSeed(new JdbcTemplate(dataSource));
    tenant = seed.seedTenant("retention");
  }

  @AfterEach
  void sweep() {
    seed.sweep();
  }

  @Test
  void should_do_nothing_when_retention_is_disabled() throws JobExecutionException {
    // -- ARRANGE --
    String oldTraceId = seed.insertTrace(tenant, null, "COMPLETE", "EXECUTED", 100);
    String recentTraceId = seed.insertTrace(tenant, null, "COMPLETE", "EXECUTED", 5);
    ReflectionTestUtils.setField(job, "retentionDays", 0);

    // -- ACT --
    job.execute(null);

    // -- ASSERT --
    assertThat(seed.traceExists(oldTraceId)).isTrue();
    assertThat(seed.traceExists(recentTraceId)).isTrue();
  }

  @Test
  void should_delete_old_traces_and_keep_recent_ones() throws JobExecutionException {
    // -- ARRANGE --
    String oldTraceId = seed.insertTrace(tenant, null, "COMPLETE", "EXECUTED", 100);
    String recentTraceId = seed.insertTrace(tenant, null, "COMPLETE", "EXECUTED", 5);
    ReflectionTestUtils.setField(job, "retentionDays", 30);

    try {
      // -- ACT --
      job.execute(null);

      // -- ASSERT --
      assertThat(seed.traceExists(oldTraceId)).isFalse();
      assertThat(seed.traceExists(recentTraceId)).isTrue();
    } finally {
      ReflectionTestUtils.setField(job, "retentionDays", 0);
    }
  }
}
