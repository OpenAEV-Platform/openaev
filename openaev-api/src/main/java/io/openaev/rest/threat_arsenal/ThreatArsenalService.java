package io.openaev.rest.threat_arsenal;

import static io.openaev.utils.ArchitectureFilterUtils.handleArchitectureFilter;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.database.model.Filters;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractDomainCountOutput;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.payload.service.PayloadUpdateService;
import io.openaev.rest.threat_arsenal.dto.*;
import io.openaev.schema.SchemaUtils;
import io.openaev.schema.model.PropertySchemaDTO;
import io.openaev.utils.mapper.ThreatArsenalMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ThreatArsenalService {

  private final PayloadCreationService payloadCreationService;
  private final PayloadUpdateService payloadUpdateService;
  private final PayloadService payloadService;
  private final InjectorContractService injectorContractService;
  private final ThreatArsenalMapper threatArsenalMapper;

  /**
   * Maps {@code action_*} field names used by the frontend to the corresponding {@code
   * injector_contract_*} field names on the JPA entity.
   */
  private static final Map<String, String> ACTION_TO_ENTITY_FIELDS =
      Map.of(
          "action_labels", "injector_contract_labels",
          "action_platforms", "injector_contract_platforms",
          "action_domains", "injector_contract_domains",
          "action_tags", "injector_contract_tags",
          "action_payload_status", "injector_contract_payload_status",
          "action_injectors", "injector_contract_injectors",
          "action_updated_at", "injector_contract_updated_at");

  private static final Map<String, String> ENTITY_TO_ACTION_FIELDS =
      ACTION_TO_ENTITY_FIELDS.entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

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
    SearchPaginationInput filtered = handleArchitectureFilter(this.translateSearchInput(input));
    return injectorContractService.getDomainCounts(filtered);
  }

  /**
   * Translates a {@link SearchPaginationInput} so that {@code action_*} filter keys and sort
   * properties are replaced by their {@code injector_contract_*} counterparts expected by the JPA
   * entity.
   *
   * <p>Keys absent from the mapping (e.g. {@code injector_contract_injector}) are kept as-is.
   *
   * @param input the original search input from the frontend
   * @return a new {@link SearchPaginationInput} with translated keys
   */
  public SearchPaginationInput translateSearchInput(SearchPaginationInput input) {
    SearchPaginationInput translated = new SearchPaginationInput();
    translated.setPage(input.getPage());
    translated.setSize(input.getSize());
    translated.setTextSearch(input.getTextSearch());

    if (input.getFilterGroup() != null) {
      Filters.FilterGroup translatedGroup = new Filters.FilterGroup();
      translatedGroup.setMode(input.getFilterGroup().getMode());
      List<Filters.Filter> translatedFilters = new ArrayList<>();
      for (Filters.Filter filter : input.getFilterGroup().getFilters()) {
        Filters.Filter copy = new Filters.Filter();
        copy.setKey(ACTION_TO_ENTITY_FIELDS.getOrDefault(filter.getKey(), filter.getKey()));
        copy.setOperator(filter.getOperator());
        copy.setValues(filter.getValues());
        copy.setMode(filter.getMode());
        translatedFilters.add(copy);
      }
      translatedGroup.setFilters(translatedFilters);
      translated.setFilterGroup(translatedGroup);
    }

    if (input.getSorts() != null) {
      List<SortField> translatedSorts = new ArrayList<>();
      for (SortField sort : input.getSorts()) {
        translatedSorts.add(
            new SortField(
                ACTION_TO_ENTITY_FIELDS.getOrDefault(sort.property(), sort.property()),
                sort.direction(),
                sort.nullHandling()));
      }
      translated.setSorts(translatedSorts);
    }

    return translated;
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
   * Creates a new threat arsenal action.
   *
   * <p>Converts the action input into a payload create input, delegates the creation to the payload
   * creation service, and maps the result back to a {@link ThreatArsenalAction}.
   *
   * @param actionInput the creation input containing the new action values
   * @return the created threat arsenal action
   */
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
  public ThreatArsenalAction update(String actionId, ThreatArsenalActionUpdateInput actionInput) {
    // resolve the payload ID from the injector contract
    InjectorContract injectorContract = injectorContractService.injectorContract(actionId);
    Payload payload = injectorContract.getPayload();
    if (payload == null) {
      throw new ElementNotFoundException(
          "Only payload linked to injector contract can be updated ");
    }

    // convert ThreatArsenalActionUpdateInput into PayloadUpdateInput
    PayloadUpdateInput payloadInput = getPayloadUpdateInputFromCommonActionInput(actionInput);

    // update payload using the resolved payload ID
    PayloadCreationService.PayloadInjectorContractCreationResult result =
        this.payloadUpdateService.updatePayload(payload.getId(), payloadInput);

    // convert to ThreatArsenalAction
    return threatArsenalMapper.toThreatArsenalAction(result.injectorContract());
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
  public ThreatArsenalAction duplicate(String actionId) {
    // resolve the payload ID from the injector contract
    InjectorContract injectorContract = injectorContractService.injectorContract(actionId);
    Payload payload = injectorContract.getPayload();
    if (payload == null) {
      throw new ElementNotFoundException(
          "Only payload linked to injector contract can be duplicated ");
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
        handleArchitectureFilter(translateSearchInput(input)),
        InjectorContract.class);
  }
}
