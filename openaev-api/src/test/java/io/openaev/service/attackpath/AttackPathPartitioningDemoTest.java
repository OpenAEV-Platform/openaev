package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.ResultSet;
import java.sql.Statement;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Date-partitioning retention demo (issue 6647), kept deliberately light (design decision D2). It
 * shows, on a tiny standalone partitioned table, the two things partitioning by {@code executed_at}
 * actually buys: a date-scoped query prunes to the relevant partition, and retention is a metadata
 * {@code DETACH}/{@code DROP} rather than a mass {@code DELETE}.
 *
 * <p>The honest caveat is the point: partitioning by date does <b>not</b> speed the attack-path
 * read, which is keyed on {@code simulation_id}, not on a date range. So it is a retention tool,
 * not a latency tool — this demo exists to make that distinction concrete, not to argue for
 * adopting it.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("date-range partitioning: pruning and cheap retention (light demo)")
class AttackPathPartitioningDemoTest extends IntegrationTest {

  @Test
  void dateRangePartitioningPrunesAndDetachesCheaply() {
    long[] result =
        entityManager
            .unwrap(Session.class)
            .doReturningWork(
                connection -> {
                  try (Statement statement = connection.createStatement()) {
                    statement.execute(
                        "CREATE TABLE ap_demo_exec (id varchar(255) NOT NULL, executed_at timestamp"
                            + " NOT NULL) PARTITION BY RANGE (executed_at)");
                    statement.execute(
                        "CREATE TABLE ap_demo_exec_2026_01 PARTITION OF ap_demo_exec"
                            + " FOR VALUES FROM ('2026-01-01') TO ('2026-02-01')");
                    statement.execute(
                        "CREATE TABLE ap_demo_exec_2026_02 PARTITION OF ap_demo_exec"
                            + " FOR VALUES FROM ('2026-02-01') TO ('2026-03-01')");
                    statement.execute(
                        "INSERT INTO ap_demo_exec VALUES ('a', '2026-01-10'), ('b', '2026-01-20'),"
                            + " ('c', '2026-02-05')");

                    boolean pruned = pruned(statement);
                    long before = scalar(statement, "SELECT count(*) FROM ap_demo_exec");

                    // Retention: drop January by detaching its partition, not by DELETE.
                    statement.execute(
                        "ALTER TABLE ap_demo_exec DETACH PARTITION ap_demo_exec_2026_01");
                    statement.execute("DROP TABLE ap_demo_exec_2026_01");
                    long after = scalar(statement, "SELECT count(*) FROM ap_demo_exec");

                    return new long[] {before, after, pruned ? 1 : 0};
                  }
                });

    assertThat(result[0]).as("all three rows before retention").isEqualTo(3);
    assertThat(result[1]).as("only February survives after detaching January").isEqualTo(1);
    assertThat(result[2])
        .as("a February-scoped query prunes to the February partition")
        .isEqualTo(1);
  }

  private static boolean pruned(Statement statement) throws java.sql.SQLException {
    StringBuilder plan = new StringBuilder();
    try (ResultSet rows =
        statement.executeQuery(
            "EXPLAIN SELECT count(*) FROM ap_demo_exec"
                + " WHERE executed_at >= '2026-02-01' AND executed_at < '2026-03-01'")) {
      while (rows.next()) {
        plan.append(rows.getString(1)).append('\n');
      }
    }
    return plan.toString().contains("ap_demo_exec_2026_02")
        && !plan.toString().contains("ap_demo_exec_2026_01");
  }

  private static long scalar(Statement statement, String sql) throws java.sql.SQLException {
    try (ResultSet rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }
}
