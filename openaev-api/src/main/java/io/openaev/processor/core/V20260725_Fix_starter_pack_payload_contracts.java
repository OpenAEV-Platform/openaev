package io.openaev.processor.core;

import io.openaev.context.TenantContext;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.DataPackService;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * One-shot migration that repairs payload contracts broken by a starter-pack import regression.
 *
 * <p>On fresh platforms, the starter-pack scenarios are imported before any payload-supporting
 * injector (e.g. the OpenAEV implant) is registered. A regression in {@code V1_DataImporter}
 * persisted those payload contracts <b>without</b> their payload reference and without any injector
 * link: the payload itself was created but left orphaned, and the injects showed a question-mark
 * icon with "no payload attached". The importer is fixed for new imports, and {@code
 * InjectorService#adoptOrphanPayloadContracts} links payload-bearing orphans at injector
 * registration — but platforms that already ran the broken import are stuck: the starter pack is
 * idempotency-guarded and will not re-run, and their contracts carry no payload so adoption never
 * matches them.
 *
 * <p>This migration heals that state ("fix if exists" — fresh platforms get a correct import from
 * the fixed importer). For every non-custom contract with no payload and no injector link, it looks
 * up an orphan payload with the contract's label name (payload contracts are labeled with their
 * payload name by construction, see {@code
 * PayloadService#setInjectorContractPropertyBasedOnPayload}). When one is found, the payload is
 * re-attached and the contract is rebuilt and linked to all payload-supporting injectors via {@code
 * PayloadService#synchroniseInjectorContractBasedOnPayload}. Contracts with no matching orphan
 * payload (e.g. static nmap/nuclei contracts awaiting their injector registration) are left
 * untouched.
 */
@Component
@Slf4j
public class V20260725_Fix_starter_pack_payload_contracts extends RuntimeMigration {

  private final InjectorContractRepository injectorContractRepository;
  private final PayloadRepository payloadRepository;
  private final PayloadService payloadService;

  public V20260725_Fix_starter_pack_payload_contracts(
      DataPackService dataPackService,
      InjectorContractRepository injectorContractRepository,
      PayloadRepository payloadRepository,
      PayloadService payloadService) {
    super(dataPackService);
    this.injectorContractRepository = injectorContractRepository;
    this.payloadRepository = payloadRepository;
    this.payloadService = payloadService;
  }

  @Override
  protected boolean doMigrate() {
    // This migration reads v1-scoped entities (injectors_contracts is a composite-PK table where
    // built-in contract ids repeat across tenants) through queries that are not all explicitly
    // tenant-parameterized: findById(String) below matches every tenant's copy of a built-in
    // contract unless the v1 filter scopes it. The filter used to be enabled implicitly by the
    // @Transactional aspect before migrations moved under MigrationProcessor's programmatic
    // tenant-scoped transactions; enable it explicitly now.
    enableV1TenantFilter();
    String tenantId = TenantContext.getCurrentTenant();
    List<String> brokenContractIds =
        injectorContractRepository.findContractsWithoutPayloadAndInjector(tenantId).stream()
            .map(InjectorContract::getId)
            .toList();
    if (brokenContractIds.isEmpty()) {
      return true;
    }

    int repaired = 0;
    for (String contractId : brokenContractIds) {
      // Re-fetch inside the loop: synchroniseInjectorContractBasedOnPayload clears the
      // persistence context (flush/clear on the injector-link insert), which would detach
      // entities loaded before the previous iteration.
      InjectorContract contract = injectorContractRepository.findById(contractId).orElse(null);
      if (contract == null || contract.getPayload() != null) {
        continue;
      }
      Map<String, String> labels = contract.getLabels();
      String payloadName = labels != null ? labels.get("en") : null;
      if (payloadName == null || payloadName.isBlank()) {
        continue;
      }
      List<Payload> orphanPayloads =
          payloadRepository.findOrphansByNameAndTenantId(payloadName, tenantId);
      if (orphanPayloads.isEmpty()) {
        // Static contracts awaiting their injector registration (e.g. nmap/nuclei) also match
        // the broken-contract query but have no orphan payload: leave them untouched.
        log.debug(
            "No orphan payload named '{}' for contract {} — skipping.", payloadName, contractId);
        continue;
      }
      if (orphanPayloads.size() > 1) {
        log.warn(
            "Multiple orphan payloads named '{}' — attaching the first one to contract {}.",
            payloadName,
            contractId);
      }
      Payload payload = orphanPayloads.getFirst();
      contract.setPayload(payload);
      injectorContractRepository.save(contract);
      // Rebuild the contract from its payload and link it to every payload-supporting injector.
      // If no payload injector is registered yet, the contract keeps its payload and is adopted
      // when the injector registers (InjectorService#adoptOrphanPayloadContracts).
      payloadService.synchroniseInjectorContractBasedOnPayload(
          payload, contract.getAttackPatterns(), contract.getDomains(), contract.getTags());
      repaired++;
    }

    if (repaired > 0) {
      log.info("Repaired {} starter-pack payload contract(s) for tenant {}.", repaired, tenantId);
    }
    return true;
  }
}
