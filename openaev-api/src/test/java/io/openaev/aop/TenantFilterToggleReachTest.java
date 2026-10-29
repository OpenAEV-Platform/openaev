package io.openaev.aop;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.rest.inject.service.InjectService;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.hibernate.internal.FilterImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Where a call to {@code entityManager.unwrap(Session.class).disableFilter("tenantFilter")} lands,
 * on the threads background code runs on, and what undoes it. These are the facts the reasoned
 * baseline of {@code TenantFilterDisableArchTest} rests on: a v1 filter toggle is not a way to
 * declare a read cross-tenant.
 *
 * <p>Outside a transaction the shared {@code EntityManager} hands back itself as the {@code
 * Session}, and every call on it runs on a throwaway session closed right after, so a toggle never
 * reaches the next read. Inside the background primitive the session is new and carries no filter.
 * The filter is armed only by {@link HibernateFilterTransactionAspect} on entry to a method-level
 * {@code @Transactional}, which re-arms it after any earlier disable.
 *
 * <p>Every case runs on a bare thread, as a Quartz worker or a scheduler task does: no transaction,
 * no bound session, no ambient tenant. Not {@code @Transactional}, since the primitive refuses an
 * active transaction. The active-tables list is pinned so no shadow run changes what is measured.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@DisplayName("A v1 tenantFilter toggle: where it lands and what undoes it")
class TenantFilterToggleReachTest extends IntegrationTest {

  private static final String TENANT_FILTER = "tenantFilter";

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private InjectService injectService;

  private Session session() {
    return entityManager.unwrap(Session.class);
  }

  private static <T> T onBareThread(Supplier<T> work) throws InterruptedException {
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thread =
        new Thread(
            () -> {
              try {
                result.set(work.get());
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    thread.start();
    thread.join();
    if (failure.get() != null) {
      throw new AssertionError("the background work failed", failure.get());
    }
    return result.get();
  }

  @Nested
  @DisplayName("With no transaction open, as on a Quartz worker")
  class WithNoTransaction {

    @Test
    @DisplayName("given no transaction, should not carry a filter toggle to the next call")
    void given_noTransaction_should_notCarryAFilterToggleToTheNextCall() throws Exception {
      // Arrange: arming is the observable twin of disabling, both land on the same session
      Supplier<Filter> armThenRead =
          () -> {
            session()
                .enableFilter(TENANT_FILTER)
                .setParameter("tenantId", Tenant.DEFAULT_TENANT_UUID);
            return session().getEnabledFilter(TENANT_FILTER);
          };

      // Act
      Filter seenByTheNextCall = onBareThread(armThenRead);

      // Assert
      assertThat(seenByTheNextCall)
          .as("each call outside a transaction runs on its own throwaway session")
          .isNull();
    }
  }

  @Nested
  @DisplayName("Inside a transaction opened by the background primitive")
  class InsideThePrimitive {

    @Test
    @DisplayName("given a fresh primitive transaction, should start with no filter armed")
    void given_freshPrimitiveTransaction_should_startWithNoFilterArmed() throws Exception {
      // Act
      Filter atEntry =
          onBareThread(
              () ->
                  tenantTx.execute(
                      TxCtx.allTenants(), () -> session().getEnabledFilter(TENANT_FILTER)));

      // Assert
      assertThat(atEntry).as("a disable at this point has nothing to disable").isNull();
    }

    @Test
    @DisplayName("given a repository read, should not arm the filter")
    void given_repositoryRead_should_notArmTheFilter() throws Exception {
      // Act
      Filter afterTheRead =
          onBareThread(
              () ->
                  tenantTx.execute(
                      TxCtx.allTenants(),
                      () -> {
                        exerciseRepository.findAllShouldBeInRunningState(Instant.EPOCH);
                        return session().getEnabledFilter(TENANT_FILTER);
                      }));

      // Assert
      assertThat(afterTheRead)
          .as("the aspect matches a method-level @Transactional, which repository methods lack")
          .isNull();
    }

    @Test
    @DisplayName(
        "given a method-level @Transactional hop after a disable, should re-arm the filter on the default tenant")
    void given_transactionalHopAfterADisable_should_reArmTheFilterOnTheDefaultTenant()
        throws Exception {
      // Act
      Filter afterTheHop =
          onBareThread(
              () ->
                  tenantTx.execute(
                      TxCtx.allTenants(),
                      () -> {
                        session().disableFilter(TENANT_FILTER);
                        injectService.saveAll(List.of());
                        return session().getEnabledFilter(TENANT_FILTER);
                      }));

      // Assert: an earlier disable does not survive the hop it was meant to guard against
      assertThat(afterTheHop).isInstanceOf(FilterImpl.class);
      assertThat(((FilterImpl) afterTheHop).getParameter("tenantId"))
          .isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    }
  }
}
