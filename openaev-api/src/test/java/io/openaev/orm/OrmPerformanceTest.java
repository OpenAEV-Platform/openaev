package io.openaev.orm;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reusable abstract base for ORM regression tests on entities whose collections must stay LAZY.
 *
 * <p>Subclasses only need to implement the seed/load contract. Two tests are provided
 * automatically:
 *
 * <ol>
 *   <li><b>Regression</b> — listing entities must NOT trigger collection loading (stays LAZY).
 *   <li><b>Query budget</b> — loading a single entity with its full graph must stay within a
 *       bounded query count.
 * </ol>
 */
@TestInstance(Lifecycle.PER_CLASS)
@Transactional
public abstract class OrmPerformanceTest extends IntegrationTest {

  @PersistenceUnit private EntityManagerFactory emf;

  // --- Infrastructure: Hibernate Statistics ---

  /** Enables Hibernate statistics, clears counters, and returns the fresh {@link Statistics}. */
  protected Statistics freshStatistics() {
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();
    return stats;
  }

  /** Returns the number of prepared statements executed since the statistics were cleared. */
  protected long queryCount(Statistics stats) {
    return stats.getPrepareStatementCount();
  }

  /** Flushes pending writes and clears the first-level (L1) cache. */
  protected void clearCache() {
    entityManager.flush();
    entityManager.clear();
  }

  // --- Contract to implement per entity ---

  /** Name of the entity under test (used in assertion messages). */
  protected abstract String entityName();

  /** JPA entity class of the root entity. */
  protected abstract Class<?> entityClass();

  /** Number of entities created by {@link #seed()}. */
  protected abstract int seedCount();

  /**
   * Seeds entities with nested collections, flushes + clears the L1 cache, and returns the
   * persisted IDs.
   */
  protected abstract List<UUID> seed();

  /**
   * Loads a single entity by ID and forces a full traversal of the object graph (triggering any
   * lazy loading). Returns the total number of nodes loaded (for data-integrity assertions).
   */
  protected abstract int loadFullGraph(UUID id);

  /**
   * Maximum number of SQL queries allowed for a full graph load. The default is deliberately loose:
   * the EAGER to-one relations hanging off the graph drag their own EAGER collections along, and
   * they dominate the count. Override to tighten per entity.
   */
  protected int queryBudget() {
    return 20;
  }

  // --- Provided tests ---

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Regression: listing entities does not trigger collection loading")
  void regression_listQuery_should_notLoadCollections() {
    seed();
    Statistics stats = freshStatistics();

    List<?> results =
        entityManager
            .createQuery("SELECT e FROM " + entityClass().getSimpleName() + " e", entityClass())
            .getResultList();
    long sqlCount = queryCount(stats);

    assertThat(results).hasSize(seedCount());
    assertThat(sqlCount)
        .as(
            "LAZY guard: listing %d %s entities must cost exactly one query, collections untouched"
                + " (got %d)",
            seedCount(), entityName(), sqlCount)
        .isEqualTo(1);
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Query budget: full graph load stays bounded")
  void queryBudget_fullGraphLoad_should_stayBounded() {
    List<UUID> ids = seed();
    Statistics stats = freshStatistics();

    int nodeCount = loadFullGraph(ids.getFirst());
    long sqlCount = queryCount(stats);

    assertThat(nodeCount)
        .as("%s full graph should contain at least one node", entityName())
        .isGreaterThan(0);

    assertThat(sqlCount)
        .as(
            "Query budget: %s full graph load must stay bounded (got %d queries)",
            entityName(), sqlCount)
        .isLessThanOrEqualTo(queryBudget());
  }
}
