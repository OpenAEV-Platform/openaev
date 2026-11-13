package io.openaev.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.execution.ExecutionTraceScopeSeed.Tenant;
import io.openaev.scheduler.jobs.ExecutionTraceRetentionJob;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * With {@code execution_traces} outside the active tables, as in production today, the retention
 * job deletes the old traces of every tenant and a finder reads the trace of its inject. The armed
 * twin of this class is {@link ExecutionTraceScopedPathsTest}.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Execution traces with the table not armed: retention and readers")
class ExecutionTraceUnarmedParityTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    // The production list without execution_traces, whatever list the run passes
    registry.add(
        "openaev.tenant.active-tables",
        () ->
            ProductionActiveTables.names().stream()
                .filter(table -> !table.equals("execution_traces"))
                .collect(Collectors.joining(",")));
  }

  @Autowired private ExecutionTraceRepository executionTraceRepository;
  @Autowired private ExecutionTraceRetentionJob retentionJob;
  @Autowired private DataSource dataSource;

  private ExecutionTraceScopeSeed seed;
  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void seedTwoTenants() {
    seed = new ExecutionTraceScopeSeed(new JdbcTemplate(dataSource));
    tenantA = seed.seedTenant("a");
    tenantB = seed.seedTenant("b");
  }

  @AfterEach
  void sweep() {
    seed.sweep();
  }

  @Test
  @DisplayName(
      "The retention job deletes the old traces of both tenants and leaves the recent ones")
  void given_oldTracesInTwoTenants_should_deleteThemAll() throws Exception {
    // Arrange
    String oldOfA = seed.insertTrace(tenantA, null, "EXECUTION", "INFO", 100);
    String oldOfB = seed.insertTrace(tenantB, null, "EXECUTION", "INFO", 100);
    String recentOfA = seed.insertTrace(tenantA, null, "EXECUTION", "INFO", 5);
    ReflectionTestUtils.setField(retentionJob, "retentionDays", 30);

    try {
      // Act
      retentionJob.execute(null);

      // Assert
      assertThat(seed.traceExists(oldOfA)).isFalse();
      assertThat(seed.traceExists(oldOfB)).isFalse();
      assertThat(seed.traceExists(recentOfA)).isTrue();
    } finally {
      ReflectionTestUtils.setField(retentionJob, "retentionDays", 0);
    }
  }

  @Test
  @DisplayName("A finder returns the trace of its inject")
  void given_traceOfAnInject_should_beFoundByItsPlayer() {
    // Arrange
    seed.insertTrace(tenantA, null, "EXECUTION", "INFO", 5, tenantA.playerId());

    // Act
    List<ExecutionTrace> found =
        executionTraceRepository.findByInjectIdAndPlayerId(tenantA.injectId(), tenantA.playerId());

    // Assert
    assertThat(found).hasSize(1);
  }
}
