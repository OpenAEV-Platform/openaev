package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestQueryStats")
class RequestQueryStatsTest {

  @Test
  @DisplayName("accumulates totals across statements")
  void totals() {
    RequestQueryStats stats = new RequestQueryStats("GET /x");
    stats.record("select 1", 3);
    stats.record("select 1", 2);
    stats.record("update t set a=?", 5);

    assertThat(stats.totalQueries()).isEqualTo(3);
    assertThat(stats.totalMillis()).isEqualTo(10);
    assertThat(stats.distinctStatements()).isEqualTo(2);
  }

  @Test
  @DisplayName("flags statements repeated strictly above the threshold, most frequent first")
  void repeatedAboveThreshold() {
    RequestQueryStats stats = new RequestQueryStats("GET /x");
    for (int i = 0; i < 12; i++) {
      stats.record("select * from teams where exercise_id = ?", 1);
    }
    for (int i = 0; i < 5; i++) {
      stats.record("select * from users where team_id = ?", 1);
    }
    stats.record("select * from exercises where id = ?", 1);

    List<RequestQueryStats.StatementStat> repeated = stats.repeatedStatements(10);

    // Only the 12x statement is above the threshold of 10; the 5x and 1x ones are not.
    assertThat(repeated).hasSize(1);
    assertThat(repeated.get(0).count()).isEqualTo(12);
    assertThat(repeated.get(0).select()).isTrue();
    assertThat(repeated.get(0).sql()).contains("teams");
  }

  @Test
  @DisplayName("orders multiple offenders by descending count")
  void ordersOffenders() {
    RequestQueryStats stats = new RequestQueryStats("GET /x");
    for (int i = 0; i < 30; i++) {
      stats.record("select a", 1);
    }
    for (int i = 0; i < 15; i++) {
      stats.record("select b", 1);
    }

    List<RequestQueryStats.StatementStat> repeated = stats.repeatedStatements(10);

    assertThat(repeated).extracting(RequestQueryStats.StatementStat::count).containsExactly(30, 15);
  }

  @Test
  @DisplayName("detects non-select statements")
  void detectsWrites() {
    RequestQueryStats stats = new RequestQueryStats("POST /x");
    for (int i = 0; i < 20; i++) {
      stats.record("insert into audit (id) values (?)", 1);
    }

    RequestQueryStats.StatementStat stat = stats.repeatedStatements(10).get(0);
    assertThat(stat.select()).isFalse();
  }

  @Test
  @DisplayName("ignores null sql and clamps negative timings")
  void robustness() {
    RequestQueryStats stats = new RequestQueryStats("GET /x");
    stats.record(null, 5);
    stats.record("select 1", -10);

    assertThat(stats.totalQueries()).isEqualTo(1);
    assertThat(stats.totalMillis()).isEqualTo(0);
  }
}
