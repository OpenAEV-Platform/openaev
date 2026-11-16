package io.openaev.service.catalog.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseApplied;
import io.openaev.service.payload.PayloadRevocationService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Catalogue revocation listener")
class CatalogRevocationListenerTest {

  private static final String TENANT_ID = "tenant";

  @Mock private PayloadRevocationService payloadRevocationService;
  @Mock private TenantScopedTransaction tenantScopedTransaction;
  @InjectMocks private CatalogRevocationListener listener;

  @Test
  @DisplayName("Follows the revocations of the release in a transaction scoped to its tenant")
  void given_releaseApplied_should_followItsRevocationsInItsTenant() {
    // Arrange
    TxCtx ctx = TxCtx.forTenant(TENANT_ID);
    doAnswer(
            invocation -> {
              invocation.getArgument(1, Runnable.class).run();
              return null;
            })
        .when(tenantScopedTransaction)
        .execute(eq(ctx), any(Runnable.class));

    // Act
    listener.onReleaseApplied(new CatalogReleaseApplied(TENANT_ID, "1.261110.0", List.of()));

    // Assert
    InOrder order = inOrder(tenantScopedTransaction, payloadRevocationService);
    order.verify(tenantScopedTransaction).execute(eq(ctx), any(Runnable.class));
    order.verify(payloadRevocationService).applyCatalogueRevocations(ctx, TENANT_ID);
  }
}
