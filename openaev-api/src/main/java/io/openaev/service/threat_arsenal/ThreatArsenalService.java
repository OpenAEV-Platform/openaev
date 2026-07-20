package io.openaev.service.threat_arsenal;

import static io.openaev.utils.ArchitectureFilterUtils.handleArchitectureFilter;
import static io.openaev.utils.ThreatArsenalFilterUtils.ACTION_TO_ENTITY_FIELDS;
import static io.openaev.utils.ThreatArsenalFilterUtils.ENTITY_TO_ACTION_FIELDS;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.api.threat_arsenal.dto.*;
import io.openaev.database.model.Collector;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateMappingInput;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.injector_contract.output.InjectorContractAuthorCountOutput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractDomainCountOutput;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.payload.service.PayloadUpdateService;
import io.openaev.schema.SchemaUtils;
import io.openaev.schema.model.PropertySchemaDTO;
import io.openaev.utils.ThreatArsenalFilterUtils;
import io.openaev.utils.mapper.ThreatArsenalMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.criteria.Join;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ThreatArsenalService {

  private final PayloadCreationService payloadCreationService;
  private final PayloadUpdateService payloadUpdateService;
  private final PayloadService payloadService;
  private final InjectorContractService injectorContractService;
  private final ThreatArsenalMapper threatArsenalMapper;
  private final CollectorService collectorService;

  /** Injector types considered "tabletop" (email, SMS, challenges, media pressure). */
  public static final List<String> TABLETOP_INJECTOR_TYPES =
      List.of("openaev_email", "openaev_ovh_sms", "openaev_challenge", "openaev_channel");

  /** Max page size allowed by {@code Pagination}; used to page through select-all bulk deletes. */
  private static final int MAX_PAGE_SIZE = 1000;

  /**
   * Retrieves a threat arsenal action by its identifier and returns the full-detail output.
   *
   * <p>Loads the underlying {@link Payload} together with attack-pattern, domain and tag IDs
   * resolved through the injector contract, then maps everything to a
   *
   * @param actionId the action (payload) identifier
   * @return the fully populated action output DTO
   */
  public ThreatArsenalActionFullOutput findById(String actionId) {
    InjectorContract injectorContract = injectorContractService.injectorContract(actionId);

    if (injectorContract.getPayload() != null) {
      PayloadService.PayloadWithRelatedEntities payloadWithRelatedEntities =
          payloadService.findPayloadWithRelatedEntities(injectorContract.getPayload().getId());
      return threatArsenalMapper.toThreatArsenalActionFullOutput(
          payloadWithRelatedEntities.payload(),
          injectorContract.getId(),
          injectorContract.getLabels(),
          payloadWithRelatedEntities.attackPatternIds(),
          payloadWithRelatedEntities.domainIds(),
          payloadWithRelatedEntities.tagIds());
    }

    return threatArsenalMapper.toThreatArsenalActionFullOutput(injectorContract);
  }

  /**
   * Returns the filterable property schemas for the threat arsenal view.
   *
   * <p>Since {@code ThreatArsenalAction} is a DTO (not a JPA entity), this method introspects the
   * underlying {@link InjectorContract} entity and translates {@code injector_contract_*} JSON
   * names to their {@code action_*} equivalents so that the frontend filter system works
   * transparently with the threat-arsenal naming convention.
   *
   * @param filterableOnly when {@code true}, only filterable properties are returned
   * @param filterNames if non-empty, restricts the result to properties whose translated JSON name
   *     matches one of these values
   * @return the translated property schemas
   * @throws ClassNotFoundException if {@link InjectorContract} cannot be introspected
   */
  public List<PropertySchemaDTO> getSchemas(boolean filterableOnly, List<String> filterNames)
      throws ClassNotFoundException {
    // Translate action_* filter names to injector_contract_* for matching
    List<String> translatedFilterNames =
        filterNames.stream().map(name -> ACTION_TO_ENTITY_FIELDS.getOrDefault(name, name)).toList();

    return SchemaUtils.schemaWithSubtypes(InjectorContract.class).stream()
        .filter(p -> !filterableOnly || p.isFilterable())
        .filter(
            p -> translatedFilterNames.isEmpty() || translatedFilterNames.contains(p.getJsonName()))
        .map(
            p -> {
              PropertySchemaDTO dto = new PropertySchemaDTO(p);
              dto.setJsonName(
                  ENTITY_TO_ACTION_FIELDS.getOrDefault(dto.getJsonName(), dto.getJsonName()));
              return dto;
            })
        .toList();
  }

  /**
   * Returns the number of injector contracts per domain, applying the given search filters.
   *
   * <p>Translates {@code action_*} filter keys to their {@code injector_contract_*} counterparts
   * and applies the architecture filter before delegating to {@link InjectorContractService}.
   *
   * @param input the search and pagination parameters (only filters are used)
   * @return a list of domain counts
   */
  public List<InjectorContractDomainCountOutput> getDomainCounts(SearchPaginationInput input) {
    SearchPaginationInput filtered =
        handleArchitectureFilter(ThreatArsenalFilterUtils.translateSearchInput(input));
    return injectorContractService.getDomainCounts(filtered);
  }

  /**
   * Author facet counts for the current filters, so the sidebar can show every author and grey out
   * the zero-count ones. Uses the same {@code action_*} -> {@code injector_contract_*} translation
   * as the search route.
   */
  public List<InjectorContractAuthorCountOutput> getAuthorCounts(SearchPaginationInput input) {
    SearchPaginationInput filtered =
        handleArchitectureFilter(ThreatArsenalFilterUtils.translateSearchInput(input));
    return injectorContractService.getAuthorCounts(filtered);
  }

  /**
   * Populates a {@link PayloadUpdateInput} with the fields common to all action inputs.
   *
   * @param source the action input holding the common field values
   * @return a populated {@link PayloadUpdateInput}
   */
  private PayloadUpdateInput getPayloadUpdateInputFromCommonActionInput(CommonActionInput source) {
    PayloadUpdateInput target = new PayloadUpdateInput();
    target.setName(source.name());
    target.setPlatforms(source.platforms());
    target.setDescription(source.description());
    target.setExecutor(source.executor());
    target.setContent(source.content());
    target.setExecutionArch(source.executionArch());
    target.setExpectations(source.expectations());
    target.setExecutableFile(source.executableFile());
    target.setFileDropFile(source.fileDropFile());
    target.setHostname(source.hostname());
    target.setArguments(source.arguments());
    target.setPrerequisites(source.prerequisites());
    target.setCleanupExecutor(source.cleanupExecutor());
    target.setCleanupCommand(source.cleanupCommand());
    target.setTagIds(source.tagIds());
    target.setAttackPatternsIds(source.attackPatternsIds());
    target.setDetectionRemediations(source.detectionRemediations());
    target.setOutputParsers(source.outputParsers());
    target.setDomainIds(source.domainIds());
    return target;
  }

  private PayloadCreateInput convertActionCreateInputToPayloadCreateInput(
      ThreatArsenalActionCreateInput actionInput) {
    PayloadCreateInput payloadInput = new PayloadCreateInput();
    BeanUtils.copyProperties(getPayloadUpdateInputFromCommonActionInput(actionInput), payloadInput);
    payloadInput.setType(actionInput.type());
    payloadInput.setSource(actionInput.source());
    payloadInput.setStatus(actionInput.status());
    return payloadInput;
  }

  /**
   * Retrieves the collectors associated with the remediation of a given action.
   *
   * <p>Resolves the injector contract by the given action ID and fetches the collectors linked to
   * the underlying payload. Only payload-based injector contracts are supported.
   *
   * @param actionId the action (injector contract) identifier
   * @return the list of collectors associated with the action's payload
   * @throws ElementNotFoundException if the injector contract is not payload-based
   */
  public List<Collector> getCollectorsForActionRemediation(String actionId) {
    InjectorContract injectorContract = injectorContractService.injectorContract(actionId);
    Payload payload = injectorContract.getPayload();
    if (payload == null) {
      throw new ElementNotFoundException(
          "Only payload-based injector contracts can provide collectors for action remediation.");
    }

    return collectorService.collectorsForPayload(payload.getId());
  }

  /**
   * Creates a new threat arsenal action.
   *
   * <p>Converts the action input into a payload create input, delegates the creation to the payload
   * creation service, and maps the result back to a {@link ThreatArsenalAction}.
   *
   * @param actionInput the creation input containing the new action values
   * @return the created threat arsenal action
   */
  @Transactional(rollbackFor = Exception.class)
  public ThreatArsenalAction create(ThreatArsenalActionCreateInput actionInput) {
    PayloadCreateInput payloadCreateInput =
        convertActionCreateInputToPayloadCreateInput(actionInput);
    PayloadCreationService.PayloadInjectorContractCreationResult result =
        this.payloadCreationService.createPayload(payloadCreateInput);
    return threatArsenalMapper.toThreatArsenalAction(result.injectorContract());
  }

  /**
   * Updates an existing threat arsenal action.
   *
   * <p>Converts the action input into a payload update input, resolves the payload ID from the
   * injector contract, delegates the update to the payload update service, and maps the result back
   * to a {@link ThreatArsenalAction}.
   *
   * @param actionId the ID of the action to update — equals the injector contract ID
   * @param actionInput the update input containing the new action values
   * @return the updated threat arsenal action
   */
  @Transactional(rollbackFor = Exception.class)
  public ThreatArsenalAction update(String actionId, ThreatArsenalActionUpdateInput actionInput) {
    // resolve the payload ID from the injector contract
    InjectorContract injectorContract = injectorContractService.injectorContract(actionId);

    if (injectorContract.getPayload() == null) {
      return updateActionNotPayloadBased(injectorContract, actionInput);
    }

    return updateActionPayloadBased(injectorContract, actionInput);
  }

  private ThreatArsenalAction updateActionPayloadBased(
      InjectorContract injectorContract, ThreatArsenalActionUpdateInput actionInput) {
    if (actionInput.executionArch() == null) {
      throw new IllegalArgumentException(
          "action_execution_arch is required for payload-based actions");
    }
    if (actionInput.expectations() == null) {
      throw new IllegalArgumentException(
          "action_expectations is required for payload-based actions");
    }
    // convert ThreatArsenalActionUpdateInput into PayloadUpdateInput
    PayloadUpdateInput payloadInput = getPayloadUpdateInputFromCommonActionInput(actionInput);
    // update payload using the resolved payload ID
    PayloadCreationService.PayloadInjectorContractCreationResult result =
        this.payloadUpdateService.updatePayload(
            injectorContract.getPayload().getId(), payloadInput);
    // convert to ThreatArsenalAction
    return threatArsenalMapper.toThreatArsenalAction(result.injectorContract());
  }

  private ThreatArsenalAction updateActionNotPayloadBased(
      InjectorContract injectorContract, ThreatArsenalActionUpdateInput actionInput) {
    InjectorContractUpdateMappingInput injectorContractInput =
        getInjectorContractUpdateMappingInputFromActionInput(actionInput);
    InjectorContract injectorContractUpdated =
        injectorContractService.updateInjectorContractTTPDomainsAndTags(
            injectorContract, injectorContractInput);
    return threatArsenalMapper.toThreatArsenalAction(injectorContractUpdated);
  }

  private InjectorContractUpdateMappingInput getInjectorContractUpdateMappingInputFromActionInput(
      ThreatArsenalActionUpdateInput actionInput) {
    InjectorContractUpdateMappingInput input = new InjectorContractUpdateMappingInput();
    input.setAttackPatternsIds(actionInput.attackPatternsIds());
    input.setDomainIds(actionInput.domainIds());
    input.setTagIds(actionInput.tagIds());
    return input;
  }

  /**
   * Duplicates an existing threat arsenal action.
   *
   * <p>Delegates the duplication to the payload service and maps the result back to a {@link
   * ThreatArsenalAction}.
   *
   * @param actionId the ID of the action to duplicate
   * @return the newly created threat arsenal action copy
   */
  @Transactional(rollbackFor = Exception.class)
  public ThreatArsenalAction duplicate(String actionId) {
    // resolve the payload ID from the injector contract
    InjectorContract injectorContract = injectorContractService.injectorContract(actionId);
    Payload payload = injectorContract.getPayload();
    if (payload == null) {
      throw new ElementNotFoundException(
          "Only injector contract based on payload can be duplicated.");
    }

    PayloadCreationService.PayloadInjectorContractCreationResult result =
        this.payloadService.duplicate(payload.getId());
    return threatArsenalMapper.toThreatArsenalAction(result.injectorContract());
  }

  /**
   * Search for Injector Contracts, depending on pagination input and filter
   *
   * @param mode output mode
   * @param input to filter
   * @return the injector contracts search results
   */
  public Page<? extends InjectorContractBaseOutput> searchInjectorContracts(
      InjectorContractService.OutputMode mode, InjectorContractSearchPaginationInput input) {
    return buildPaginationCriteriaBuilder(
        (spec, specCount, pageable) ->
            this.injectorContractService.getSinglePage(
                spec,
                specCount,
                pageable,
                mode,
                input.getInjectorContractIdsToIgnore(),
                input.getInjectorContractIdsToProcess()),
        handleArchitectureFilter(ThreatArsenalFilterUtils.translateSearchInput(input)),
        InjectorContract.class);
  }

  /**
   * Search for non-tabletop Injector Contracts (excludes email, SMS, challenges, media pressure).
   * Adds a JPA specification to filter out contracts whose injector type is in {@link
   * #TABLETOP_INJECTOR_TYPES}.
   *
   * @param mode output mode
   * @param input to filter
   * @return the injector contracts search results excluding tabletop types
   */
  public Page<? extends InjectorContractBaseOutput> searchNonTabletopInjectorContracts(
      InjectorContractService.OutputMode mode, InjectorContractSearchPaginationInput input) {
    Specification<InjectorContract> excludeTabletop =
        (root, query, cb) -> {
          Join<?, Injector> injectorJoin = root.join("injectorLinks").join("injector");
          return cb.not(injectorJoin.get("type").in(TABLETOP_INJECTOR_TYPES));
        };

    return buildPaginationCriteriaBuilder(
        (spec, specCount, pageable) ->
            this.injectorContractService.getSinglePage(
                spec.and(excludeTabletop),
                specCount.and(excludeTabletop),
                pageable,
                mode,
                input.getInjectorContractIdsToIgnore(),
                input.getInjectorContractIdsToProcess()),
        handleArchitectureFilter(ThreatArsenalFilterUtils.translateSearchInput(input)),
        InjectorContract.class);
  }

  /**
   * Deletes a payload-based threat arsenal action.
   *
   * <p>Resolves the injector contract by the given action ID and deletes it. Only payload-based
   * actions can be deleted. The associated {@link Payload} is automatically removed via JPA cascade
   * ({@code CascadeType.REMOVE}) configured on {@link InjectorContract#getPayload()}.
   *
   * @param actionId the ID of the action to delete — equals the injector contract ID
   * @throws ElementNotFoundException if the injector contract is not payload-based
   */
  @Transactional(rollbackFor = Exception.class)
  public void delete(String actionId) {
    InjectorContract injectorContract = injectorContractService.injectorContract(actionId);
    if (!isEligibleForDeletion(injectorContract)) {
      throw new ElementNotFoundException("Only payload-based or orphaned actions can be deleted.");
    }
    this.injectorContractService.deleteInjectorContractById(actionId);
  }

  /**
   * Bulk-deletes threat arsenal actions.
   *
   * <p>Resolves the target set from the search input using the same ids-to-process / ids-to-ignore
   * semantics as the mass-run flow: when {@code injector_contract_ids_to_process} is provided, only
   * those are considered; otherwise (select-all mode) every action matching the current filters is
   * considered, minus {@code injector_contract_ids_to_ignore}. Only eligible actions are actually
   * deleted (payload-based, and - for collector-sourced payloads - only when deprecated), mirroring
   * the per-row delete eligibility. Non-eligible actions are silently skipped.
   *
   * @param input the search + selection input
   * @return the ids that were actually deleted
   */
  @Transactional(rollbackFor = Exception.class)
  public List<String> bulkDelete(InjectorContractSearchPaginationInput input) {
    List<String> candidateIds = resolveCandidateIds(input);
    List<String> deleted = new ArrayList<>();
    for (String actionId : candidateIds) {
      if (isEligibleForDeletion(actionId)) {
        injectorContractService.deleteInjectorContractById(actionId);
        deleted.add(actionId);
      }
    }
    return deleted;
  }

  private List<String> resolveCandidateIds(InjectorContractSearchPaginationInput input) {
    List<String> idsToProcess = input.getInjectorContractIdsToProcess();
    if (idsToProcess != null && !idsToProcess.isEmpty()) {
      return idsToProcess;
    }
    // Select-all mode: page through every action matching the current filters
    // (minus the explicitly de-selected ids, already honored by getSinglePage).
    List<String> allIds = new ArrayList<>();
    int page = 0;
    Page<? extends InjectorContractBaseOutput> resultPage;
    do {
      InjectorContractSearchPaginationInput pageInput = new InjectorContractSearchPaginationInput();
      BeanUtils.copyProperties(input, pageInput);
      pageInput.setPage(page);
      pageInput.setSize(MAX_PAGE_SIZE);
      resultPage =
          searchInjectorContracts(InjectorContractService.OutputMode.THREAT_ARSENAL, pageInput);
      resultPage.getContent().forEach(output -> allIds.add(output.getId()));
      page++;
    } while (resultPage.hasNext());
    return allIds;
  }

  private boolean isEligibleForDeletion(String actionId) {
    InjectorContract injectorContract;
    try {
      injectorContract = injectorContractService.injectorContract(actionId);
    } catch (ElementNotFoundException e) {
      return false;
    }
    return isEligibleForDeletion(injectorContract);
  }

  private boolean isEligibleForDeletion(InjectorContract injectorContract) {
    // Orphaned actions (their injector has been removed, so no injector type) can
    // always be purged - they are dead entries the user needs to clean up.
    if (injectorContract.getInjectorType() == null) {
      return true;
    }
    Payload payload = injectorContract.getPayload();
    if (payload == null) {
      return false;
    }
    // Collector-sourced payloads can only be deleted once deprecated.
    boolean fromCollector = payload.getCollectorType() != null;
    return !fromCollector || payload.getStatus() == Payload.PAYLOAD_STATUS.DEPRECATED;
  }
}
