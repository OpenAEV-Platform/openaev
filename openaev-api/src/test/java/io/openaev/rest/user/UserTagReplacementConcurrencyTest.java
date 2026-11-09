package io.openaev.rest.user;

import static io.openaev.helper.StreamHelper.iterableToSet;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.UserService;
import io.openaev.utils.fixtures.UserFixture;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Two independent transactions replacing the tags of the same user, against the real database. Rows
 * are committed (each transaction has its own connection), in a tenant seeded for the test and
 * removed after it. A replacement must start from the links the user holds once its row is locked,
 * not from the collection its transaction loaded earlier: two replacements that read the same set
 * would otherwise each delete only that set and leave the union of what they asked for.
 */
@DisplayName("Tag replacement of a user by concurrent transactions")
class UserTagReplacementConcurrencyTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private UserService userService;
  @Autowired private UserRepository userRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String userId;
  private String tagA;
  private String tagB;
  private String tagC;
  private String tagD;

  @BeforeEach
  void seedUserHoldingTagA() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "tag-concurrency-" + tenantId);
    tagA = seedTag("a");
    tagB = seedTag("b");
    tagC = seedTag("c");
    tagD = seedTag("d");
    userId = userRepository.save(UserFixture.getUserWithDefaultEmail()).getId();
    jdbc.update("INSERT INTO users_tenants (user_id, tenant_id) VALUES (?, ?)", userId, tenantId);
    jdbc.update("INSERT INTO users_tags (user_id, tag_id) VALUES (?, ?)", userId, tagA);
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM users_tags WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM tags WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
  }

  private String seedTag(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tag_color, tag_created_at, tag_updated_at, tenant_id)"
            + " VALUES (?, ?, '#123456', now(), now(), ?)",
        id,
        name + "-" + id,
        tenantId);
    return id;
  }

  private Set<String> linksOfTheUser() {
    return new HashSet<>(
        jdbc.queryForList("SELECT tag_id FROM users_tags WHERE user_id = ?", String.class, userId));
  }

  /**
   * One transaction of the request's scope that loads the user and its tags, calls {@code
   * afterLoad}, then replaces the tags with the given ones.
   */
  private Boolean replaceTags(Set<String> wantedIds, Runnable afterLoad) {
    return tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          User user = userRepository.findById(userId).orElseThrow();
          assertThat(user.getTags()).extracting(Tag::getId).as("loaded links").isNotEmpty();
          afterLoad.run();
          Set<Tag> wanted = iterableToSet(tagRepository.findAllById(wantedIds));
          userService.replaceTags(user, wanted);
          return Boolean.TRUE;
        });
  }

  @Nested
  @DisplayName("A replacement whose loaded tags went stale")
  class StaleTags {

    @ParameterizedTest(name = "wanted tag B: {0}, else none")
    @ValueSource(booleans = {true, false})
    @DisplayName("given_tagsReplacedAfterTheLoad_should_endWithExactlyTheWantedSet")
    void given_tagsReplacedAfterTheLoad_should_endWithExactlyTheWantedSet(boolean replace)
        throws Exception {
      // Arrange
      Set<String> wanted = replace ? Set.of(tagB) : Set.of();
      CountDownLatch loaded = new CountDownLatch(1);
      CountDownLatch otherCommitted = new CountDownLatch(1);
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try {
        // Act: the first transaction loads {A}, a second one replaces A with C and commits, then
        // the first one replaces its stale {A}.
        Future<Boolean> first =
            pool.submit(
                () ->
                    replaceTags(
                        wanted,
                        () -> {
                          loaded.countDown();
                          await(otherCommitted);
                        }));
        assertThat(loaded.await(30, SECONDS)).as("the first transaction loaded the user").isTrue();
        replaceTags(Set.of(tagC), () -> {});
        otherCommitted.countDown();
        first.get(30, SECONDS);
      } finally {
        otherCommitted.countDown();
        pool.shutdownNow();
      }

      // Assert
      assertThat(linksOfTheUser()).isEqualTo(wanted);
    }
  }

  @Nested
  @DisplayName("Two replacements overlapping")
  class Overlapping {

    @Test
    @DisplayName("given_twoReplacementsReadingTheSameTags_should_endWithOneOfTheTwoRequestedSets")
    void given_twoReplacementsReadingTheSameTags_should_endWithOneOfTheTwoRequestedSets()
        throws Exception {
      // Arrange
      CyclicBarrier bothLoaded = new CyclicBarrier(2);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        // Act: both load {A}, then one asks for {B} and the other for {C}.
        List<Future<Boolean>> both =
            List.of(
                pool.submit(() -> replaceTags(Set.of(tagB), () -> awaitBarrier(bothLoaded))),
                pool.submit(() -> replaceTags(Set.of(tagC), () -> awaitBarrier(bothLoaded))));
        for (Future<Boolean> one : both) {
          one.get(30, SECONDS);
        }
      } finally {
        pool.shutdownNow();
      }

      // Assert
      assertThat(linksOfTheUser()).isIn(Set.of(tagB), Set.of(tagC));
    }
  }

  @Nested
  @DisplayName("Tags added by a replacement")
  class AddedTags {

    private long statementsOfReplacementBy(Set<String> wantedIds) {
      Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
      return tenantTx.execute(
          TxCtx.forTenant(tenantId),
          () -> {
            User user = userRepository.findById(userId).orElseThrow();
            Set<Tag> wanted = iterableToSet(tagRepository.findAllById(wantedIds));
            stats.clear();
            userService.replaceTags(user, wanted);
            return stats.getPrepareStatementCount();
          });
    }

    @Test
    @DisplayName("given_severalTagsToAdd_should_insertThemWithTheStatementsOfASingleOne")
    void given_severalTagsToAdd_should_insertThemWithTheStatementsOfASingleOne() {
      // Arrange
      entityManagerFactory.unwrap(SessionFactory.class).getStatistics().setStatisticsEnabled(true);

      // Act
      long forOneAddition = statementsOfReplacementBy(Set.of(tagA, tagB));
      jdbc.update("DELETE FROM users_tags WHERE user_id = ? AND tag_id <> ?", userId, tagA);
      long forThreeAdditions = statementsOfReplacementBy(Set.of(tagA, tagB, tagC, tagD));

      // Assert
      assertThat(forThreeAdditions).isEqualTo(forOneAddition);
      assertThat(linksOfTheUser()).containsExactlyInAnyOrder(tagA, tagB, tagC, tagD);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(30, SECONDS)) {
        throw new IllegalStateException("the second transaction never committed");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(30, SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (Exception e) {
      throw new IllegalStateException("the other transaction never loaded the user", e);
    }
  }
}
