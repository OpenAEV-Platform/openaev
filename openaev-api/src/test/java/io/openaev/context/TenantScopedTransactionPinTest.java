package io.openaev.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import jakarta.persistence.EntityManager;
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
}
