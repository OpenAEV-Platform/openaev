package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.service.stix.SecurityCoverageHuntValidationService;
import io.openaev.service.stix.SecurityCoverageHuntValidationService.HuntValidationOutcome;
import io.openaev.service.stix.SecurityCoverageHuntValidationService.HuntValidationRequest;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityCoverageHuntValidationJob")
class SecurityCoverageHuntValidationJobTest {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  @Mock private SecurityCoverageHuntValidationService huntValidationService;
  @Mock private TenantScopedTransaction tenantTx;

  @InjectMocks private SecurityCoverageHuntValidationJob job;

  @BeforeEach
  void runTransactionsInline() {
    lenient()
        .when(tenantTx.execute(any(), ArgumentMatchers.<Supplier<Object>>any()))
        .thenAnswer(invocation -> invocation.<Supplier<Object>>getArgument(1).get());
    lenient()
        .doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(tenantTx)
        .execute(any(), ArgumentMatchers.<Runnable>any());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clearCurrentTenant();
  }

  private static HuntValidationRequest request(String id) {
    return new HuntValidationRequest(
        id,
        "inject-" + id,
        "T1059.001",
        "platform-" + id,
        "Splunk prod",
        "security-coverage--" + id,
        Instant.parse("2026-10-03T10:00:00Z"),
        Instant.parse("2026-10-03T10:20:00Z"));
  }

  private static HuntValidationOutcome accepted(String id) {
    return new HuntValidationOutcome(id, HuntValidationOutcome.Kind.VALIDATED, 1, 1, null);
  }

  @Test
  @DisplayName("given hunt validation disabled should touch neither OpenCTI nor the database")
  void given_disabled_should_doNothing() throws Exception {
    // Arrange
    when(huntValidationService.isEnabled()).thenReturn(false);

    // Act
    job.execute(null);

    // Assert
    verify(huntValidationService, never()).tenantsWithRegisteredConnector();
    verify(huntValidationService, never()).send(anyString(), anyList());
    verifyNoInteractions(tenantTx);
  }

  @Test
  @DisplayName("given due validations should read, send outside any transaction, then record")
  void given_dueValidations_should_readSendThenRecord() throws Exception {
    // Arrange
    List<HuntValidationRequest> due = List.of(request("1"));
    List<HuntValidationOutcome> outcomes = List.of(accepted("1"));
    when(huntValidationService.isEnabled()).thenReturn(true);
    when(huntValidationService.tenantsWithRegisteredConnector()).thenReturn(List.of(TENANT_A));
    when(huntValidationService.findDueRequests(any(Instant.class))).thenReturn(due);
    when(huntValidationService.send(TENANT_A, due)).thenReturn(outcomes);

    // Act
    job.execute(null);

    // Assert
    InOrder order = inOrder(tenantTx, huntValidationService);
    order
        .verify(tenantTx)
        .execute(eq(TxCtx.forTenant(TENANT_A)), ArgumentMatchers.<Supplier<Object>>any());
    order.verify(huntValidationService).findDueRequests(any(Instant.class));
    order.verify(huntValidationService).send(TENANT_A, due);
    order.verify(tenantTx).execute(eq(TxCtx.forTenant(TENANT_A)), ArgumentMatchers.<Runnable>any());
    order.verify(huntValidationService).recordOutcomes(eq(outcomes), any(Instant.class));
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
  }

  @Test
  @DisplayName("given nothing due should neither call OpenCTI nor write")
  void given_nothingDue_should_notSendNorRecord() throws Exception {
    // Arrange
    when(huntValidationService.isEnabled()).thenReturn(true);
    when(huntValidationService.tenantsWithRegisteredConnector()).thenReturn(List.of(TENANT_A));
    when(huntValidationService.findDueRequests(any(Instant.class))).thenReturn(List.of());

    // Act
    job.execute(null);

    // Assert
    verify(huntValidationService, never()).send(anyString(), anyList());
    verify(huntValidationService, never()).recordOutcomes(anyList(), any(Instant.class));
  }

  @Test
  @DisplayName("given one tenant failing should still deliver the other tenants")
  void given_oneTenantFailing_should_stillDeliverOthers() throws Exception {
    // Arrange
    List<HuntValidationRequest> due = List.of(request("2"));
    when(huntValidationService.isEnabled()).thenReturn(true);
    when(huntValidationService.tenantsWithRegisteredConnector())
        .thenReturn(List.of(TENANT_A, TENANT_B));
    when(huntValidationService.findDueRequests(any(Instant.class)))
        .thenThrow(new IllegalStateException("database hiccup"))
        .thenReturn(due);
    when(huntValidationService.send(TENANT_B, due)).thenReturn(List.of(accepted("2")));

    // Act
    job.execute(null);

    // Assert
    verify(huntValidationService, never()).send(eq(TENANT_A), anyList());
    verify(huntValidationService).send(TENANT_B, due);
    ArgumentCaptor<TxCtx> scopes = ArgumentCaptor.forClass(TxCtx.class);
    verify(tenantTx).execute(scopes.capture(), ArgumentMatchers.<Runnable>any());
    assertThat(scopes.getValue()).isEqualTo(TxCtx.forTenant(TENANT_B));
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
  }
}
