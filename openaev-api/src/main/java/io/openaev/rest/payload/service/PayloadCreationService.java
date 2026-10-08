package io.openaev.rest.payload.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import io.openaev.config.OpenAEVAnonymous;
import io.openaev.config.SessionHelper;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.service.UserService;
import io.openaev.service.payload_approval.PayloadApprovalService;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class PayloadCreationService {

  private final PayloadUtils payloadUtils;

  private final PayloadService payloadService;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;

  private final AttackPatternRepository attackPatternRepository;
  private final PayloadRepository payloadRepository;
  private final TagRepository tagRepository;
  private final DomainService domainService;
  private final DocumentService documentService;
  private final ResultsMetricCollector resultsMetricCollector;
  private final UserService userService;
  private final PayloadApprovalService payloadApprovalService;
  private final TenantWriteScopeResolver writeScopeResolver;

  public record PayloadInjectorContractCreationResult(
      Payload payload, InjectorContract injectorContract) {}

  @Transactional(rollbackFor = Exception.class)
  public PayloadInjectorContractCreationResult createPayload(TxCtx ctx, PayloadCreateInput input) {
    return createInternal(ctx, null, input);
  }

  /**
   * Same as {@link #createPayload(TxCtx, PayloadCreateInput)}, with the write tenant supplied
   * explicitly rather than derived from {@code ctx} alone. For a caller already inside a
   * transaction whose scope spans more than one tenant (e.g. an importer that resolved its single
   * write tenant from the parent it writes into, while the transaction's ambient scope is still the
   * caller's whole membership): {@code ctx} must stay the scope already active on the transaction -
   * the {@code TenantScopeTransactionAspect} refuses a nested {@code @Transactional} method that
   * narrows it - and {@code explicitTenant} carries the actual write tenant, validated against
   * {@code ctx} the same way {@link TenantWriteScopeResolver#tenantForWrite} validates any other
   * explicit tenant.
   */
  @Transactional(rollbackFor = Exception.class)
  public PayloadInjectorContractCreationResult createPayload(
      TxCtx ctx, String explicitTenant, PayloadCreateInput input) {
    return createInternal(ctx, explicitTenant, input);
  }

  /**
   * Shared body of both {@code createPayload} overloads, called directly rather than through {@code
   * this.createPayload(...)}: an intra-class call to a @Transactional method bypasses the Spring
   * proxy (no transaction, no tenant scope, silently), which is exactly the shape {@code
   * TenantBackgroundTransactionArchTest.no_transactional_self_invocation} forbids.
   */
  private PayloadInjectorContractCreationResult createInternal(
      TxCtx ctx, String explicitTenant, PayloadCreateInput input) {
    if (enterpriseEditionService.isEnterpriseLicenseInactive(
        licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setDetectionRemediations(null);
    }

    return create(ctx, explicitTenant, input);
  }

  private PayloadInjectorContractCreationResult create(
      TxCtx ctx, String explicitTenant, PayloadCreateInput input) {
    PayloadType payloadType = PayloadType.fromString(input.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Payload payload = payloadType.getPayloadSupplier().get();
    payloadUtils.copyProperties(input, payload);
    payload.setTenant(new Tenant(writeScopeResolver.tenantForWrite(ctx, explicitTenant)));

    // Manually created payloads are authored by the current user. System-driven
    // creations (startup datapacks, schedulers) have no authenticated user and
    // stay authorless.
    if (!(SessionHelper.currentUser() instanceof OpenAEVAnonymous)) {
      payload.setAuthorUser(userService.currentUser());
      payload.setLastModifiedBy(payload.getAuthorUser());
    }

    if (payload instanceof Executable executable) {
      executable.setExecutableFile(documentService.document(input.getExecutableFile()));
    } else if (payload instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.document(input.getFileDropFile()));
    }

    Payload payloadSaved = payloadRepository.save(payload);
    payloadApprovalService.onWrite(
        payloadSaved, payload.getAuthorUser(), PayloadApproval.ORIGIN.CREATE, null);
    // The id collections default to empty lists on the input, but callers that build the
    // input via BeanUtils.copyProperties (e.g. threat arsenal action creation) can overwrite
    // those defaults with null. Spring Data's findAllById throws IllegalArgumentException
    // ("Ids must not be null") on a null argument, surfacing as a confusing 400. Treat an
    // absent collection as "no associations" instead.
    InjectorContract injectorContract =
        payloadService.synchroniseInjectorContractBasedOnPayload(
            payloadSaved,
            fromIterable(
                attackPatternRepository.findAllById(emptyIfNull(input.getAttackPatternsIds()))),
            iterableToSet(domainService.findAllById(emptyIfNull(input.getDomainIds()))),
            iterableToSet(tagRepository.findAllById(emptyIfNull(input.getTagIds()))));
    // Telemetry: one payload created, by type - recorded only once the payload and
    // its injector contract are persisted (a rollback would otherwise inflate the counter).
    resultsMetricCollector.recordPayloadCreated(payloadType.key);
    return new PayloadInjectorContractCreationResult(payloadSaved, injectorContract);
  }
}
