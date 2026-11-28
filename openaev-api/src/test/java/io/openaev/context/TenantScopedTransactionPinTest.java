package io.openaev.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The tenant a transaction pins follows the scope written on it, across the transaction's life:
 * nested and suspended transactions, commit and rollback on a reused thread, a scope changed while
 * it runs, and a shell with no transaction. Deliberately NOT {@code @Transactional}: each test
 * opens its own transactions on a real transaction manager, and seeds and cleans through
 * auto-committed JDBC.
 */
@DisplayName("The tenant pinned by a transaction's scope")
class TenantScopedTransactionPinTest extends IntegrationTest {

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;
  @Autowired private EntityManager manager;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTwoTenants() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("pin-a-");
    tenantB = seedTenant("pin-b-");
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  private String seedTenant(String prefix) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        prefix + id);
    return id;
  }

  private static Optional<String> pinned() {
    return TransactionTenantScope.pinnedTenant();
  }

  private TransactionTemplate transaction(int propagation) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(propagation);
    return template;
  }

  @Nested
  @DisplayName("Nesting")
  class Nesting {

    @Test
    @DisplayName(
        "given a new transaction opened inside a scope of one tenant, should pin its own tenant"
            + " and give the outer pin back after it")
    void given_newTransactionInsideAScope_should_pinItsOwnTenantThenGiveTheOuterPinBack() {
      // Arrange
      AtomicReference<Optional<String>> outerBefore = new AtomicReference<>();
      AtomicReference<Optional<String>> inner = new AtomicReference<>();
      AtomicReference<Optional<String>> outerAfter = new AtomicReference<>();

      // Act
      tenantTx.execute(
          TxCtx.forTenant(tenantA),
          () -> {
            outerBefore.set(pinned());
            tenantTx.executeNew(TxCtx.forTenant(tenantB), () -> inner.set(pinned()));
            outerAfter.set(pinned());
          });

      // Assert
      assertThat(outerBefore.get()).contains(tenantA);
      assertThat(inner.get()).contains(tenantB);
      assertThat(outerAfter.get()).contains(tenantA);
    }
  }

  @Nested
  @DisplayName("Completion")
  class Completion {

    @Test
    @DisplayName(
        "given a committed scoped transaction, should leave no pin to the next transaction of the"
            + " thread")
    void given_committedScopedTransaction_should_leaveNoPinToTheNextTransaction() {
      // Arrange
      tenantTx.execute(TxCtx.forTenant(tenantA), () -> assertThat(pinned()).contains(tenantA));

      // Act
      Optional<String> next =
          transaction(TransactionDefinition.PROPAGATION_REQUIRED).execute(status -> pinned());

      // Assert
      assertThat(pinned()).isEmpty();
      assertThat(next).isEmpty();
    }

    @Test
    @DisplayName(
        "given a rolled back scoped transaction, should leave no pin to the next transaction of"
            + " the thread")
    void given_rolledBackScopedTransaction_should_leaveNoPinToTheNextTransaction() {
      // Arrange
      assertThatThrownBy(
              () ->
                  tenantTx.execute(
                      TxCtx.forTenant(tenantA),
                      () -> {
                        throw new IllegalStateException("work failed");
                      }))
          .hasMessage("work failed");

      // Act
      Optional<String> next =
          transaction(TransactionDefinition.PROPAGATION_REQUIRED).execute(status -> pinned());

      // Assert
      assertThat(pinned()).isEmpty();
      assertThat(next).isEmpty();
    }
  }

  @Nested
  @DisplayName("Scope changed in a transaction")
  class ScopeChange {

    @Test
    @DisplayName(
        "given a scope changed inside a transaction, should pin the new tenant and the put back"
            + " one after work run in another tenant")
    void given_scopeChangedInsideATransaction_should_pinTheTenantOfEachScope() {
      // Arrange
      AtomicReference<Optional<String>> first = new AtomicReference<>();
      AtomicReference<Optional<String>> changed = new AtomicReference<>();
      AtomicReference<Optional<String>> duringWork = new AtomicReference<>();

      // Act
      Optional<String> putBack =
          transaction(TransactionDefinition.PROPAGATION_REQUIRED)
              .execute(
                  status -> {
                    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));
                    first.set(pinned());
                    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantB));
                    changed.set(pinned());
                    tenantTx.executeOnCurrentTransaction(
                        TxCtx.forTenant(tenantA),
                        () -> {
                          duringWork.set(pinned());
                          return null;
                        });
                    return pinned();
                  });

      // Assert
      assertThat(first.get()).contains(tenantA);
      assertThat(changed.get()).contains(tenantB);
      assertThat(duringWork.get()).contains(tenantA);
      assertThat(putBack).contains(tenantB);
    }

    @Test
    @DisplayName(
        "given a transaction already holding the same tenants from another writer, should pin"
            + " them when the scope is set again")
    void given_sameTenantsWrittenByAnotherWriter_should_pinThemWhenTheScopeIsSetAgain() {
      // Act
      Optional<String> pin =
          transaction(TransactionDefinition.PROPAGATION_REQUIRED)
              .execute(
                  status -> {
                    manager
                        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
                        .setParameter("scope", tenantB)
                        .getSingleResult();
                    assertThat(pinned()).isEmpty();
                    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantB));
                    return pinned();
                  });

      // Assert
      assertThat(pin).contains(tenantB);
    }
  }

  @Nested
  @DisplayName("No transaction")
  class NoTransaction {

    @Test
    @DisplayName("given no synchronization active, should record no pin")
    void given_noSynchronizationActive_should_recordNoPin() {
      // Arrange
      assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

      // Act
      TransactionTenantScope.write(manager, tenantA);

      // Assert
      assertThat(pinned()).isEmpty();
    }

    @Test
    @DisplayName(
        "given a shell with no transaction around a scoped transaction, should pin only inside"
            + " the scoped transaction")
    void given_shellWithNoTransactionAroundAScopedTransaction_should_pinOnlyInsideIt() {
      // Arrange
      AtomicReference<Optional<String>> inShell = new AtomicReference<>();
      AtomicReference<Optional<String>> inside = new AtomicReference<>();
      AtomicReference<Optional<String>> afterInnerCommit = new AtomicReference<>();

      // Act
      transaction(TransactionDefinition.PROPAGATION_NOT_SUPPORTED)
          .executeWithoutResult(
              status -> {
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                TransactionTenantScope.write(manager, tenantA);
                inShell.set(pinned());
                tenantTx.execute(TxCtx.forTenant(tenantB), () -> inside.set(pinned()));
                afterInnerCommit.set(pinned());
              });

      // Assert
      assertThat(inShell.get()).isEmpty();
      assertThat(inside.get()).contains(tenantB);
      assertThat(afterInnerCommit.get()).isEmpty();
    }
  }

  /** Whether the current scope holds several tenants, and whether it holds none. */
  private record Held(boolean several, boolean none) {
    static Held now() {
      return new Held(
          TransactionTenantScope.holdsSeveralTenants(), TransactionTenantScope.holdsNoTenant());
    }
  }

  private static final Held ONE_OR_NO_SCOPE = new Held(false, false);
  private static final Held SEVERAL = new Held(true, false);
  private static final Held NONE = new Held(false, true);

  @Nested
  @DisplayName("Tenants the scope holds")
  class TenantsHeld {

    @Test
    @DisplayName(
        "given a new transaction of one tenant inside a scope of two, should hold one tenant inside"
            + " it and two again once the outer transaction resumes")
    void given_newTransactionOfOneTenantInsideTwo_should_holdOneThenTwoAgain() {
      // Arrange
      AtomicReference<Held> outerBefore = new AtomicReference<>();
      AtomicReference<Held> inner = new AtomicReference<>();
      AtomicReference<Held> outerAfter = new AtomicReference<>();

      // Act
      tenantTx.execute(
          TxCtx.forTenants(List.of(tenantA, tenantB)),
          () -> {
            outerBefore.set(Held.now());
            tenantTx.executeNew(TxCtx.forTenant(tenantA), () -> inner.set(Held.now()));
            outerAfter.set(Held.now());
          });

      // Assert
      assertThat(outerBefore.get()).isEqualTo(SEVERAL);
      assertThat(inner.get()).isEqualTo(ONE_OR_NO_SCOPE);
      assertThat(outerAfter.get()).isEqualTo(SEVERAL);
    }

    @Test
    @DisplayName(
        "given a new transaction of two tenants inside a scope of none, should hold two inside it"
            + " and none again once the outer transaction resumes")
    void given_newTransactionOfTwoTenantsInsideNone_should_holdTwoThenNoneAgain() {
      // Arrange
      AtomicReference<Held> outerBefore = new AtomicReference<>();
      AtomicReference<Held> inner = new AtomicReference<>();
      AtomicReference<Held> outerAfter = new AtomicReference<>();

      // Act
      transaction(TransactionDefinition.PROPAGATION_REQUIRED)
          .executeWithoutResult(
              status -> {
                // The scope a request with no tenant runs under, as the transaction aspect writes
                // it.
                TransactionTenantScope.write(manager, "");
                outerBefore.set(Held.now());
                tenantTx.executeNew(
                    TxCtx.forTenants(List.of(tenantA, tenantB)), () -> inner.set(Held.now()));
                outerAfter.set(Held.now());
              });

      // Assert
      assertThat(outerBefore.get()).isEqualTo(NONE);
      assertThat(inner.get()).isEqualTo(SEVERAL);
      assertThat(outerAfter.get()).isEqualTo(NONE);
    }

    @Test
    @DisplayName(
        "given work suspended by a transaction of no scope, should hold nothing inside it and the"
            + " outer tenants again after it")
    void given_workSuspendedByATransactionOfNoScope_should_holdNothingInsideIt() {
      // Arrange
      AtomicReference<Held> inner = new AtomicReference<>();
      AtomicReference<Held> outerAfter = new AtomicReference<>();

      // Act
      tenantTx.execute(
          TxCtx.forTenants(List.of(tenantA, tenantB)),
          () -> {
            transaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
                .executeWithoutResult(status -> inner.set(Held.now()));
            outerAfter.set(Held.now());
          });

      // Assert
      assertThat(inner.get()).isEqualTo(ONE_OR_NO_SCOPE);
      assertThat(outerAfter.get()).isEqualTo(SEVERAL);
    }

    @Test
    @DisplayName(
        "given a scope narrowed from two tenants to one, should hold one tenant and pin it")
    void given_scopeNarrowedFromTwoToOne_should_holdOneAndPinIt() {
      // Act
      AtomicReference<Held> wide = new AtomicReference<>();
      Optional<String> pin =
          transaction(TransactionDefinition.PROPAGATION_REQUIRED)
              .execute(
                  status -> {
                    tenantTx.setScopeOnCurrentTransaction(
                        TxCtx.forTenants(List.of(tenantA, tenantB)));
                    wide.set(Held.now());
                    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));
                    assertThat(Held.now()).isEqualTo(ONE_OR_NO_SCOPE);
                    return pinned();
                  });

      // Assert
      assertThat(wide.get()).isEqualTo(SEVERAL);
      assertThat(pin).contains(tenantA);
    }

    @Test
    @DisplayName(
        "given a scope widened from one tenant to two, should hold several tenants and pin none")
    void given_scopeWidenedFromOneToTwo_should_holdSeveralAndPinNone() {
      // Act
      AtomicReference<Optional<String>> narrowPin = new AtomicReference<>();
      AtomicReference<Held> wide = new AtomicReference<>();
      Optional<String> pin =
          transaction(TransactionDefinition.PROPAGATION_REQUIRED)
              .execute(
                  status -> {
                    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));
                    narrowPin.set(pinned());
                    tenantTx.setScopeOnCurrentTransaction(
                        TxCtx.forTenants(List.of(tenantA, tenantB)));
                    wide.set(Held.now());
                    return pinned();
                  });

      // Assert
      assertThat(narrowPin.get()).contains(tenantA);
      assertThat(wide.get()).isEqualTo(SEVERAL);
      assertThat(pin).isEmpty();
    }

    @Test
    @DisplayName(
        "given a scope of no tenant then one, should hold no tenant first and then pin the one")
    void given_scopeOfNoTenantThenOne_should_holdNoneThenPinTheOne() {
      // Act
      AtomicReference<Held> empty = new AtomicReference<>();
      AtomicReference<Held> one = new AtomicReference<>();
      Optional<String> pin =
          transaction(TransactionDefinition.PROPAGATION_REQUIRED)
              .execute(
                  status -> {
                    // The scope a request with no tenant runs under, as the transaction aspect
                    // writes it.
                    TransactionTenantScope.write(manager, "");
                    empty.set(Held.now());
                    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantB));
                    one.set(Held.now());
                    return pinned();
                  });

      // Assert
      assertThat(empty.get()).isEqualTo(NONE);
      assertThat(one.get()).isEqualTo(ONE_OR_NO_SCOPE);
      assertThat(pin).contains(tenantB);
    }

    @Test
    @DisplayName(
        "given a committed scope of two tenants, should hold nothing in the next transaction of"
            + " the thread")
    void given_committedScopeOfTwoTenants_should_holdNothingInTheNextTransaction() {
      // Arrange
      tenantTx.execute(
          TxCtx.forTenants(List.of(tenantA, tenantB)),
          () -> assertThat(Held.now()).isEqualTo(SEVERAL));

      // Act
      Held next = transaction(TransactionDefinition.PROPAGATION_REQUIRED).execute(s -> Held.now());

      // Assert
      assertThat(Held.now()).isEqualTo(ONE_OR_NO_SCOPE);
      assertThat(next).isEqualTo(ONE_OR_NO_SCOPE);
    }
  }
}
