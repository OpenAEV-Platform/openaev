package io.openaev.rest.finding;

import static io.openaev.database.audit.ModelBaseListener.DATA_PERSIST;
import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.FindingRepository.UpsertedFinding;
import io.openaev.utils.SensitiveValueMaskingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
 *
 * <p>The writes are native, so {@code ModelBaseListener} never sees them: this writer publishes the
 * lifecycle event itself, without which notification triggers on findings never fire (#8649).
 */
@Component
@RequiredArgsConstructor
public class FindingWriter {

  private final FindingRepository findingRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper mapper;

  /**
   * Upserts the finding, links it to the asset and tags, and publishes its lifecycle event.
   *
   * <p>The events mirror what {@code ModelBaseListener} would publish for a JPA save: a create when
   * the row is inserted, an update when an existing finding gains an asset, and nothing when an
   * agent reports a finding already linked to its asset. Each agent of an inject reports its own
   * copy, so an update tells the triggers filtered on that asset that it now matches.
   */
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
    // The native upsert skips the JPA callbacks of Finding, so the displayed value is computed here
    // with the same helper, and the row is never written without it.
    String findingValueDisplay =
        SensitiveValueMaskingUtils.maskIfNeededFailClosed(findingType, findingValue);
    UpsertedFinding upserted =
        findingRepository.upsertFinding(
            findingField,
            findingType,
            findingValue,
            findingValueDisplay,
            findingLabels,
            injectId,
            name,
            tenantId);
    if (upserted == null) {
      // The tenant statement inspector rewrites the upsert's conflict branch to
      // "DO UPDATE SET ... WHERE can_access_tenant(findings.tenant_id) RETURNING ...".
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
    String findingId = upserted.getId();
    boolean assetLinked = findingRepository.insertFindingAsset(findingId, assetId) > 0;
    findingRepository.insertFindingTags(findingId, tagIds);

    if (upserted.getInserted()) {
      publishLifecycleEvent(DATA_PERSIST, findingId);
    } else if (assetLinked) {
      publishLifecycleEvent(DATA_UPDATE, findingId);
    }
  }

  // Loaded after the links are written: the event carries the serialized finding to the stream,
  // so it must hold its full asset and tag lists. Listeners consume it after this commit.
  private void publishLifecycleEvent(String eventType, String findingId) {
    findingRepository
        .findById(findingId)
        .ifPresent(
            finding -> eventPublisher.publishEvent(new BaseEvent(eventType, finding, mapper)));
  }
}
