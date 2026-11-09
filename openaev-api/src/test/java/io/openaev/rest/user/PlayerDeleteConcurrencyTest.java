package io.openaev.rest.user;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.tenants.TenantUserService;
import io.openaev.utils.fixtures.UserFixture;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Two deletes of one player, from two of its tenants at the same time, against the real database.
 * The rows are committed (each transaction has its own connection), in tenants seeded for the test
 * and removed after it. The player is deleted when no membership is left, so the check that decides
 * it must read the memberships once the other delete has finished: two deletes that each see the
 * other's membership would leave an account that belongs to no tenant.
 */
@DisplayName("Delete of one player from two tenants by concurrent transactions")
class PlayerDeleteConcurrencyTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  /** How long a delete waits for the other one between its detach and its check. */
  private static final int MEETING_SECONDS = 3;

  @Autowired private PlayerService playerService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;
  @Autowired private ApplicationEventMulticaster eventMulticaster;
  @MockitoSpyBean private TenantUserService tenantUserService;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String userId;
  private final List<BaseEvent> published = new CopyOnWriteArrayList<>();
  private SmartApplicationListener recorder;

  @BeforeEach
  void seedPlayerOfAAndB() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("a");
    tenantB = seedTenant("b");
    userId = userRepository.save(UserFixture.getUserWithDefaultEmail()).getId();
    jdbc.update("INSERT INTO users_tenants (user_id, tenant_id) VALUES (?, ?)", userId, tenantA);
    jdbc.update("INSERT INTO users_tenants (user_id, tenant_id) VALUES (?, ?)", userId, tenantB);
    recorder = recordingBaseEventsInto(published);
    eventMulticaster.addApplicationListener(recorder);
  }

  @AfterEach
  void cleanup() {
    eventMulticaster.removeApplicationListener(recorder);
    jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  private String seedTenant(String suffix) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "player-delete-concurrency-" + suffix + "-" + id);
    return id;
  }

  private static SmartApplicationListener recordingBaseEventsInto(List<BaseEvent> into) {
    return new SmartApplicationListener() {
      @Override
      public boolean supportsEventType(
          Class<? extends org.springframework.context.ApplicationEvent> type) {
        return PayloadApplicationEvent.class.isAssignableFrom(type);
      }

      @Override
      public void onApplicationEvent(org.springframework.context.ApplicationEvent event) {
        if (event instanceof PayloadApplicationEvent<?> payload
            && payload.getPayload() instanceof BaseEvent baseEvent) {
          into.add(baseEvent);
        }
      }
    };
  }

  /**
   * Makes each delete wait, once its membership is removed, for the other one to have removed its
   * own. When the other delete cannot get there (it is held on the row of the player), the wait
   * ends after {@link #MEETING_SECONDS} and the delete goes on, as it would after a slow request.
   */
  private void meetAfterTheDetach() {
    CyclicBarrier bothDetached = new CyclicBarrier(2);
    doAnswer(
            call -> {
              call.callRealMethod();
              try {
                bothDetached.await(MEETING_SECONDS, SECONDS);
              } catch (TimeoutException | java.util.concurrent.BrokenBarrierException e) {
                // the other delete did not come: it is waiting for this one to finish
              }
              return null;
            })
        .when(tenantUserService)
        .detach(any(TxCtx.class), anyString());
  }

  /** One request: a transaction of the tenant's scope, as the handler of the endpoint opens. */
  private Boolean deleteFrom(String tenantId) {
    TxCtx ctx = TxCtx.forTenant(tenantId);
    return tenantTx.execute(
        ctx,
        () -> {
          playerService.deletePlayerOfTenant(ctx, userId);
          return Boolean.TRUE;
        });
  }

  private int accounts() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM users WHERE user_id = ?", Integer.class, userId);
  }

  private int memberships() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM users_tenants WHERE user_id = ?", Integer.class, userId);
  }

  private long deletionsOfThePlayer() {
    return published.stream()
        .filter(event -> ModelBaseListener.DATA_DELETE.equals(event.getType()))
        .filter(event -> event.getInstance() instanceof User user && userId.equals(user.getId()))
        .count();
  }

  @Nested
  @DisplayName("A player of two tenants deleted from both at the same time")
  class FromBothTenants {

    @Test
    @DisplayName("given_deletesFromAAndBOverlapping_should_deleteTheAccountAndAnnounceItOnce")
    void given_deletesFromAAndBOverlapping_should_deleteTheAccountAndAnnounceItOnce()
        throws Exception {
      // Arrange
      meetAfterTheDetach();
      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        // Act
        List<Future<?>> both =
            List.of(pool.submit(() -> deleteFrom(tenantA)), pool.submit(() -> deleteFrom(tenantB)));
        for (Future<?> one : both) {
          one.get(60, SECONDS);
        }
      } finally {
        pool.shutdownNow();
      }

      // Assert
      assertThat(memberships()).as("memberships left").isZero();
      assertThat(accounts()).as("accounts left").isZero();
      assertThat(deletionsOfThePlayer()).as("deletions published").isEqualTo(1);
    }
  }
}
