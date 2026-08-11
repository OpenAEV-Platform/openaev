package io.openaev.rest.finding;

import io.openaev.context.TxCtx;
import io.openaev.database.repository.FindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a complete finding (the finding row plus its asset and tag links) in its own
 * transaction. The three statements replace a single modifying CTE that JSQLParser cannot parse, so
 * the tenant inspector can filter them once the findings table is activated. The {@code
 * REQUIRES_NEW} boundary lives here in the API layer (not on the repository) and is reached
 * cross-bean, so the new transaction actually applies.
 *
 * <p>That boundary is why {@link #saveCompleteFinding} declares a {@link TxCtx}: {@code
 * REQUIRES_NEW} suspends the caller's transaction, so the caller's scope does not travel with it
 * and the new transaction would start with {@code app.current_tenants} unset. Every read predicate
 * the inspector adds would then be false, the upsert's {@code ON CONFLICT DO UPDATE} branch
 * included, so a re-detected finding could never be updated. The scope is set by the tenant aspect
 * from that parameter, which is how the HTTP path carries a scope; the background primitive must
 * not be reachable from here.
 */
@Component
@RequiredArgsConstructor
public class FindingWriter {

  private final FindingRepository findingRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void saveCompleteFinding(
      TxCtx ctx,
      String findingField,
      String findingType,
      String findingValue,
      String[] findingLabels,
      String injectId,
      String name,
      String assetId,
      String[] tagIds) {
    // The transaction's scope and the row's tenant are one value. Taking both from the caller let
    // them disagree: the transaction scoped to one tenant while the row carried another's id, so
    // the upsert's conflict branch could never match. A scope naming anything but exactly one
    // tenant cannot attribute a row, and findings.tenant_id is NOT NULL.
    String tenantId =
        switch (ctx) {
          case TxCtx.Restricted restricted when restricted.tenantIds().size() == 1 ->
              restricted.tenantIds().get(0);
          default ->
              throw new IllegalStateException(
                  "Cannot write a finding for inject "
                      + injectId
                      + " without a single-tenant scope: findings.tenant_id is NOT NULL and the row"
                      + " would be unattributed. The caller must resolve the tenant from the"
                      + " inject.");
        };
    String findingId =
        findingRepository.upsertFinding(
            findingField, findingType, findingValue, findingLabels, injectId, name, tenantId);
    if (findingId == null) {
      // The tenant statement inspector rewrites the upsert's conflict branch to
      // "DO UPDATE SET ... WHERE can_access_tenant(findings.tenant_id) RETURNING finding_id".
      // On a conflict with no scope, or with a row owned by another tenant, that predicate is
      // false: nothing is updated, RETURNING yields nothing, and this comes back null. Refusing
      // here names the cause; without it the next line inserts a null finding_id into
      // findings_assets and the failure surfaces as a NOT NULL violation on a different table.
      throw new IllegalStateException(
          "Finding upsert returned no id for inject "
              + injectId
              + " in tenant "
              + tenantId
              + ": the conflicting row is outside the current tenant scope, or no scope is set on"
              + " this transaction. A background caller must open one (TenantScopedTransaction);"
              + " an HTTP caller must carry a TxCtx.");
    }
    findingRepository.insertFindingAsset(findingId, assetId);
    findingRepository.insertFindingTags(findingId, tagIds);
  }
}
