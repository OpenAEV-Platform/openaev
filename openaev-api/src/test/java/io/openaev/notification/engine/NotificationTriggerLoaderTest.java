package io.openaev.notification.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.repository.NotificationEventRecordRepository;
import io.openaev.database.repository.NotificationTriggerRepository;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("Notification triggers loaded across tenants")
class NotificationTriggerLoaderTest {

  private TenantScopedTransaction tenantTx;
  private NotificationTriggerLoader loader;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    tenantTx = mock(TenantScopedTransaction.class);
    when(tenantTx.execute(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    when(tenantTx.executeNew(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    NotificationTriggerRepository triggers = mock(NotificationTriggerRepository.class);
    when(triggers.findAllByTypeAndEnabledTrue(NotificationTriggerType.LIVE)).thenReturn(List.of());
    loader =
        new NotificationTriggerLoader(
            triggers, mock(NotificationEventRecordRepository.class), tenantTx);
  }

  @AfterEach
  void endTransaction() {
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  @DisplayName("Outside a transaction, the triggers load in a transaction of their own")
  @SuppressWarnings("unchecked")
  void given_noTransaction_should_openOne() {
    // Act
    List<ResolvedNotificationTrigger> loaded =
        loader.loadEnabledTriggers(NotificationTriggerType.LIVE);

    // Assert
    assertThat(loaded).isEmpty();
    verify(tenantTx).execute(eq(TxCtx.allTenants()), any(Supplier.class));
    verify(tenantTx, never()).executeNew(any(TxCtx.class), any(Supplier.class));
  }

  @Test
  @DisplayName("During a deletion, the triggers load in a nested transaction")
  @SuppressWarnings("unchecked")
  void given_activeTransaction_should_nestOne() {
    // Arrange
    TransactionSynchronizationManager.setActualTransactionActive(true);

    // Act
    List<ResolvedNotificationTrigger> loaded =
        loader.loadEnabledTriggers(NotificationTriggerType.LIVE);

    // Assert
    assertThat(loaded).isEmpty();
    verify(tenantTx).executeNew(eq(TxCtx.allTenants()), any(Supplier.class));
    verify(tenantTx, never()).execute(any(TxCtx.class), any(Supplier.class));
  }
}
