package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import io.openaev.context.TxCtx;
import io.openaev.service.PermissionService;
import java.util.List;
import java.util.stream.Stream;
import org.mockito.ArgumentCaptor;

/**
 * Asserts the scope a request handed to the per-tenant row decisions of {@link PermissionService},
 * the decisions that answer "every row of this tenant, or only the granted ones".
 *
 * <p>The caller passes a {@code @MockitoSpyBean} of the service, which records the real calls
 * without changing them. A handler that takes its decision without the request scope hands {@code
 * TxCtx.missing()} to them, or calls them not at all; either way it names no tenant here.
 */
public final class TenantDecisionScopeAssertions {

  private TenantDecisionScopeAssertions() {}

  /**
   * Fails unless the request took at least one row decision, and every one of them on a scope
   * naming exactly {@code expectedTenantIds}.
   */
  public static void assertDecidedIn(
      final PermissionService permissionService, final String... expectedTenantIds) {
    ArgumentCaptor<TxCtx> filtered = ArgumentCaptor.forClass(TxCtx.class);
    ArgumentCaptor<TxCtx> listed = ArgumentCaptor.forClass(TxCtx.class);
    verify(permissionService, atLeast(0)).filterInEachTenant(filtered.capture(), any(), any());
    verify(permissionService, atLeast(0))
        .listInEachTenant(listed.capture(), any(), any(), any(), any(), any());
    List<TxCtx> scopes =
        Stream.concat(filtered.getAllValues().stream(), listed.getAllValues().stream()).toList();
    assertThat(scopes).isNotEmpty();
    assertThat(scopes)
        .allSatisfy(
            scope ->
                assertThat(tenantsNamedBy(scope)).containsExactlyInAnyOrder(expectedTenantIds));
  }

  /** The tenants a scope names, read without the production helper the sites read it with. */
  private static List<String> tenantsNamedBy(final TxCtx scope) {
    return scope instanceof TxCtx.Restricted restricted ? restricted.tenantIds() : List.of();
  }
}
