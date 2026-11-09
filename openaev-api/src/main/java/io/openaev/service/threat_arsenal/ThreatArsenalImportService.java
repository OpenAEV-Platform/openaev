package io.openaev.service.threat_arsenal;

import static io.openaev.jsonapi.GenericJsonApiIUtils.resolveType;

import io.openaev.api.payload.PayloadImportService;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalAction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.jsonapi.GenericJsonApiIUtils;
import io.openaev.jsonapi.ZipJsonApi;
import io.openaev.service.ZipJsonService;
import io.openaev.utils.injector_contract.InjectorContractMigrationUtils;
import io.openaev.utils.mapper.ThreatArsenalMapper;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ThreatArsenalImportService {

  private static final String INJECTOR_CONTRACTS_TYPE = resolveType(InjectorContract.class);

  /** The root types of a legacy payload export. */
  private static final Set<String> PAYLOAD_ROOT_TYPES =
      typesOf(
          Command.class,
          Executable.class,
          FileDrop.class,
          DnsResolution.class,
          NetworkTraffic.class,
          AiAttack.class);

  /**
   * The resource types of the threat arsenal export format: the types an exported action (injector
   * contract export or legacy payload export) carries as its root or in {@code included}. An import
   * rebuilds these types only.
   */
  public static final Set<String> EXPORT_FORMAT_TYPES =
      Stream.concat(
              Stream.concat(Stream.of(INJECTOR_CONTRACTS_TYPE), PAYLOAD_ROOT_TYPES.stream()),
              typesOf(
                  // related resources of an action ("payloads" is the base type of a payload
                  // referenced by id, which still goes through the write tenant check)
                  Payload.class,
                  Document.class,
                  Domain.class,
                  Tag.class,
                  AttackPattern.class,
                  KillChainPhase.class,
                  Vulnerability.class,
                  Cwe.class,
                  OutputParser.class,
                  ContractOutputElement.class,
                  RegexGroup.class,
                  DetectionRemediation.class,
                  SecurityPlatform.class)
                  .stream())
          .collect(Collectors.toUnmodifiableSet());

  private static Set<String> typesOf(Class<?>... classes) {
    return Arrays.stream(classes)
        .map(GenericJsonApiIUtils::resolveType)
        .collect(Collectors.toUnmodifiableSet());
  }

  private final ZipJsonApi<InjectorContract> injectorContractZipJsonApi;
  private final PayloadImportService payloadImportService;
  private final ThreatArsenalMapper threatArsenalMapper;
  private final InjectorRepository injectorRepository;

  public ThreatArsenalAction importThreatArsenalAction(
      @NotNull TxCtx ctx, @NotNull MultipartFile file, @NotNull String tenantId) throws Exception {
    String rootType = injectorContractZipJsonApi.readRootType(file);
    if (INJECTOR_CONTRACTS_TYPE.equals(rootType)) {
      return importFromInjectorContract(file, tenantId);
    }
    if (rootType == null || !PAYLOAD_ROOT_TYPES.contains(rootType)) {
      throw new IllegalArgumentException("Unsupported resource type for this import: " + rootType);
    }
    return importFromPayload(ctx, file);
  }

  private ThreatArsenalAction importFromInjectorContract(MultipartFile file, String tenantId)
      throws Exception {
    ZipJsonService.ImportOutput<InjectorContract> response =
        injectorContractZipJsonApi.handleImport(
            file,
            "injector_contract_labels",
            null,
            contract -> {
              contract.setId(UUID.randomUUID().toString());
              contract.setTenant(new Tenant(tenantId));
              if (contract.getLabels() != null) {
                Map<String, String> updatedLabels = new HashMap<>(contract.getLabels());
                updatedLabels.replaceAll((key, value) -> value + " (Import)");
                contract.setLabels(updatedLabels);
              }
              if (contract.getPayload() != null && contract.getPayload().getName() != null) {
                contract.getPayload().setName(contract.getPayload().getName() + " (Import)");
              }
              // Injector links are not part of the export (the owning-side join rows are
              // @JsonIgnore), so an imported contract would otherwise have no injector and
              // show up as an unregistered/orphaned action (update, duplicate and export
              // disabled). Re-link payload-based contracts to the payload-supporting
              // injectors, as synchroniseInjectorContractBasedOnPayload does on creation.
              if (contract.getPayload() != null) {
                contract.addInjectors(injectorRepository.findAllByPayloads(true));
              }
              InjectorContractMigrationUtils.migratePredefinedExpectations(contract);
              return contract;
            },
            tenantId,
            EXPORT_FORMAT_TYPES);
    return threatArsenalMapper.toThreatArsenalAction(response.persistedData());
  }

  /**
   * Imports a threat arsenal action from a legacy payload export file.
   *
   * <p><b>Legacy — kept for backward compatibility only.</b> New exports use the injector contract
   * format handled by {@link #importFromInjectorContract(MultipartFile)}. This path will be removed
   * soon.
   */
  private ThreatArsenalAction importFromPayload(TxCtx ctx, MultipartFile file) throws Exception {
    PayloadImportService.PayloadImportResult result =
        payloadImportService.importPayload(ctx, file, EXPORT_FORMAT_TYPES);
    return threatArsenalMapper.toThreatArsenalAction(result.injectorContract());
  }
}
