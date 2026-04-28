package io.openaev.rest.injector_contract;

import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.database.model.InjectorContract.*;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.openaev.utils.JpaUtils.*;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;

import co.elastic.clients.util.TriConsumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalAction;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionWithContentOutput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawInjectorsContracts;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.specification.InjectorContractSpecification;
import io.openaev.injector_contract.Contract;
import io.openaev.injectors.challenge.ChallengeContract;
import io.openaev.injectors.channel.ChannelContract;
import io.openaev.injectors.email.EmailContract;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.injectors.opencti.OpenCTIContract;
import io.openaev.injectors.ovh.OvhSmsContract;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.form.*;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractDomainCountOutput;
import io.openaev.rest.injector_contract.output.InjectorContractFullOutput;
import io.openaev.rest.payload.output.PayloadSimple;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.service.InjectorService;
import io.openaev.service.UserService;
import io.openaev.utils.TargetType;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * Service for managing injector contracts.
 *
 * <p>Provides CRUD operations, search functionality, and mapping management for injector contracts.
 * Injector contracts define the interface between injects and injectors, specifying input fields,
 * target types, and associated attack patterns.
 *
 * @see io.openaev.database.model.InjectorContract
 * @see io.openaev.database.model.Injector
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class InjectorContractService implements DependenciesManager {

  @PersistenceContext private EntityManager entityManager;
  @Resource private ObjectMapper mapper;

  private final InjectorContractRepository injectorContractRepository;
  private final AttackPatternService attackPatternService;
  private final VulnerabilityService vulnerabilityService;
  private final DomainService domainService;
  private final InjectorRepository injectorRepository;
  private final UserService userService;
  private final AttackPatternRepository attackPatternRepository;
  private final TagRepository tagRepository;
  private final InjectorService injectorService;

  private final List<String> listDefaultInjectorContract =
      List.of(
          EmailContract.EMAIL_GLOBAL,
          EmailContract.EMAIL_DEFAULT,
          ChallengeContract.CHALLENGE_PUBLISH,
          OvhSmsContract.OVH_DEFAULT,
          ManualContract.MANUAL_DEFAULT,
          ChannelContract.CHANNEL_PUBLISH,
          OpenCTIContract.OPENCTI_CREATE_CASE,
          OpenCTIContract.OPENCTI_CREATE_REPORT);

  /** Configuration flag for enabling email import from XLS files. */
  @Value("${openaev.xls.import.mail.enable}")
  private boolean mailImportEnabled;

  /** Configuration flag for enabling SMS import from XLS files. */
  @Value("${openaev.xls.import.sms.enable}")
  private boolean smsImportEnabled;

  // -- CRUD --

  /**
   * Retrieves an injector contract by ID or external ID.
   *
   * @param id the injector contract ID or external ID
   * @return the injector contract
   * @throws ElementNotFoundException if not found
   */
  public InjectorContract injectorContract(@NotBlank final String id) {
    return injectorContractRepository
        .findByIdOrExternalId(id, id)
        .orElseThrow(() -> new ElementNotFoundException("Injector contract not found"));
  }

  // -- OTHERS --

  @Setter
  @Getter
  private class QuerySetup {
    private TypedQuery<Tuple> query;
    private Long total;
  }

  private QuerySetup setupQuery(
      @Nullable final Specification<InjectorContract> specification,
      @Nullable final Specification<InjectorContract> specificationCount,
      @NotNull final Pageable pageable,
      @NotNull
          TriConsumer<CriteriaBuilder, CriteriaQuery<Tuple>, Root<InjectorContract>> selector) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<InjectorContract> injectorContractRoot = cq.from(InjectorContract.class);
    selector.accept(cb, cq, injectorContractRoot);

    // Always apply access spec
    Specification<InjectorContract> accessSpec =
        InjectorContractSpecification.hasAccessToInjectorContract(userService.currentUser());

    Specification<InjectorContract> combinedSpec =
        (specification == null ? accessSpec : specification.and(accessSpec));

    // -- Text Search and Filters --
    if (specification != null) {
      Predicate predicate = combinedSpec.toPredicate(injectorContractRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    List<Order> orders = toSortCriteriaBuilder(cb, injectorContractRoot, pageable.getSort());
    cq.orderBy(orders);

    // Type Query
    TypedQuery<Tuple> query = this.entityManager.createQuery(cq);

    // -- Pagination --
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- Count Query --
    Specification<InjectorContract> combinedSpecCount =
        (specificationCount == null ? accessSpec : specificationCount.and(accessSpec));
    Long total = countQuery(cb, this.entityManager, InjectorContract.class, combinedSpecCount);

    QuerySetup qs = new QuerySetup();
    qs.setQuery(query);
    qs.setTotal(total);
    return qs;
  }

  public Iterable<RawInjectorsContracts> getAllRawInjectContracts() {
    User currentUser = userService.currentUser();
    String tenantId = TenantContext.getCurrentTenant();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_PAYLOADS)) {
      return injectorContractRepository.getAllRawInjectorsContracts();
    }
    return injectorContractRepository.getAllRawInjectorsContractsWithoutPayloadOrGranted(
        currentUser.getId());
  }

  /**
   * Creates a new custom injector contract.
   *
   * <p>Custom contracts are user-defined and can be modified or deleted. Sets up attack pattern
   * mappings, vulnerabilities, and domain associations.
   *
   * @param input the creation input
   * @return the created injector contract
   */
  @Transactional(rollbackOn = Exception.class)
  public InjectorContract createNewInjectorContract(InjectorContractAddInput input) {
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setCustom(true);
    injectorContract.setUpdateAttributes(input);
    List<AttackPattern> aps = new ArrayList<>();
    if (!input.getAttackPatternsExternalIds().isEmpty()) {
      aps =
          attackPatternService.getAttackPatternsByExternalIdsThrowIfMissing(
              new HashSet<>(input.getAttackPatternsExternalIds()));
    } else if (!input.getAttackPatternsIds().isEmpty()) {
      aps =
          attackPatternService.findAllByInternalIdsThrowIfMissing(
              new HashSet<>(input.getAttackPatternsIds()));
    }
    injectorContract.setAttackPatterns(aps);
    setVulnerabilitiesFromExternalOrInternalIds(
        input.getVulnerabilityExternalIds(), input.getVulnerabilityIds(), injectorContract);

    // Resolve the injector specified in the input
    Injector injector = injectorService.injector(input.getInjectorId());

    // Link the contract to the specified injector only.
    // Custom contracts are user-defined for a specific instance —
    // sharing across instances of the same type is only done for builtin contracts
    // during registration (InjectorService.registerBuiltinInjector).
    injectorContract.addInjector(injector);

    injectorContract.setDomains(
        injector != null && !injector.isPayloads()
            ? this.domainService.upserts(input.getDomains(), TenantContext.getCurrentTenant())
            : new HashSet<>());
    InjectorContract saved = injectorContractRepository.save(injectorContract);
    // Link on the owning side now that the contract is persisted
    injector.getContracts().add(saved);
    injectorRepository.save(injector);
    return saved;
  }

  public InjectorContract createBuiltinInjectorContract(
      Contract source, Injector injector, boolean isPayloads) {
    InjectorContract target = new InjectorContract();
    target.setId(source.getId());
    // Populate the inverse (non-owning) side only so getInjector() works
    // for tenant resolution in applyBuiltinContractData.
    // Do NOT call addInjector() here — it modifies the owning side (Injector.contracts)
    // and causes auto-flush issues since this contract is still transient.
    if (injector != null) {
      target.getInjectors().add(injector);
    }
    target.setTenant(injector.getTenant());

    applyBuiltinContractData(target, source, isPayloads, injector);
    return target;
  }

  public void updateBuiltInInjectorContract(
      InjectorContract target, Contract source, boolean isPayloads, Injector injector) {
    applyBuiltinContractData(target, source, isPayloads, injector);
  }

  private void applyBuiltinContractData(
      InjectorContract target, Contract source, boolean isPayloads, Injector injector) {
    target.setManual(source.isManual());
    target.setAtomicTesting(source.isAtomicTesting());
    target.setPlatforms(source.getPlatforms().toArray(new Endpoint.PLATFORM_TYPE[0]));
    target.setNeedsExecutor(source.isNeedsExecutor());

    Map<String, String> labels =
        source.getLabel().entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
    target.setLabels(labels);

    // Update attack patterns if not overridden
    if (target.getAttackPatterns().isEmpty() && !source.getAttackPatternsExternalIds().isEmpty()) {
      List<AttackPattern> attackPatterns =
          fromIterable(
              attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(
                  source.getAttackPatternsExternalIds(), injector.getTenant().getId()));
      target.setAttackPatterns(attackPatterns);
    } else {
      target.setAttackPatterns(new ArrayList<>());
    }

    try {
      target.setContent(mapper.writeValueAsString(source));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Failed to serialize contract content for: " + target.getId(), e);
    }

    if (!isPayloads && injector != null) {
      Set<Domain> currentDomains =
          this.domainService.upsertDomainEntities(
              target.getDomains(), injector.getTenant().getId());
      Set<Domain> domainsToAdd =
          this.domainService.upsertDomainEntities(
              source.getDomains(), injector.getTenant().getId());
      target.setDomains(
          this.domainService.mergeDomains(currentDomains, domainsToAdd, injector.getTenant()));
    }
    setupImportAvailable(target);
  }

  private void setupImportAvailable(InjectorContract injectorContract) {
    if (Arrays.asList(EmailContract.EMAIL_GLOBAL, EmailContract.EMAIL_DEFAULT)
        .contains(injectorContract.getId())) {
      injectorContract.setImportAvailable(mailImportEnabled);
    }
    if (OvhSmsContract.OVH_DEFAULT.equals(injectorContract.getId())) {
      injectorContract.setImportAvailable(smsImportEnabled);
    }
  }

  /**
   * Updates an existing injector contract.
   *
   * @param injectorContractId the contract ID to update
   * @param input the update input
   * @return the updated injector contract
   * @throws ElementNotFoundException if not found
   */
  public InjectorContract updateInjectorContract(
      String injectorContractId, InjectorContractUpdateInput input) {
    InjectorContract injectorContract =
        injectorContractRepository
            .findByIdOrExternalId(injectorContractId, injectorContractId)
            .orElseThrow(ElementNotFoundException::new);
    injectorContract.setUpdateAttributes(input);
    injectorContract.setAttackPatterns(
        attackPatternService.findAllByInternalIdsThrowIfMissing(
            new HashSet<>(input.getAttackPatternsIds())));
    setVulnerabilitiesFromExternalOrInternalIds(
        input.getVulnerabilityExternalIds(), input.getVulnerabilityIds(), injectorContract);
    injectorContract.setDomains(
        this.domainService.upserts(input.getDomains(), TenantContext.getCurrentTenant()));

    injectorContract.setUpdatedAt(Instant.now());
    return injectorContractRepository.save(injectorContract);
  }

  private void setVulnerabilitiesFromExternalOrInternalIds(
      List<String> externalIds, List<String> internalIds, InjectorContract injectorContract) {
    Set<Vulnerability> vulns = new HashSet<>();
    if (!externalIds.isEmpty()) {
      vulns =
          vulnerabilityService.findAllByExternalIdsAndAlertIfMissing(new HashSet<>(externalIds));
    } else if (!internalIds.isEmpty()) {
      vulns = vulnerabilityService.findAllByIdsOrThrowIfMissing(new HashSet<>(internalIds));
    }
    injectorContract.setVulnerabilities(vulns);
  }

  /**
   * Updates the attack pattern and vulnerability mappings for a contract.
   *
   * @param injectorContractId the contract ID to update
   * @param input the mapping update input
   * @return the updated injector contract
   * @throws ElementNotFoundException if not found
   */
  public InjectorContract updateAttackPatternMappings(
      String injectorContractId, InjectorContractUpdateMappingInput input) {
    InjectorContract injectorContract =
        injectorContractRepository
            .findByIdOrExternalId(injectorContractId, injectorContractId)
            .orElseThrow(ElementNotFoundException::new);
    injectorContract.setAttackPatterns(
        attackPatternService.findAllByInternalIdsThrowIfMissing(
            new HashSet<>(input.getAttackPatternsIds())));
    injectorContract.setVulnerabilities(
        vulnerabilityService.findAllByIdsOrThrowIfMissing(
            new HashSet<>(input.getVulnerabilityIds())));
    injectorContract.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    injectorContract.setDomains(iterableToSet(domainService.findAllById(input.getDomainIds())));
    injectorContract.setUpdatedAt(Instant.now());
    return injectorContractRepository.save(injectorContract);
  }

  /**
   * Deletes a custom injector contract.
   *
   * <p>Only custom contracts (user-created) can be deleted. Built-in contracts cannot be removed.
   *
   * @param injectorContractId the contract ID to delete
   * @throws ElementNotFoundException if not found
   * @throws IllegalArgumentException if the contract is not custom
   */
  public void deleteInjectorContract(final String injectorContractId) {
    InjectorContract injectorContract =
        this.injectorContractRepository
            .findByIdOrExternalId(injectorContractId, injectorContractId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Injector contract not found: " + injectorContractId));
    if (!injectorContract.getCustom()) {
      throw new IllegalArgumentException(
          "This injector contract can't be removed because is not a custom one: "
              + injectorContractId);
    } else {
      this.injectorContractRepository.deleteById(injectorContract.getId());
    }
  }

  /**
   * Checks if an injector contract supports a specific target type.
   *
   * <p>Analyzes the contract's field definitions to determine which target types are supported.
   *
   * @param injectorContract the contract to check
   * @param targetType the target type to verify support for
   * @return true if the contract supports the target type
   */
  public boolean checkTargetSupport(InjectorContract injectorContract, TargetType targetType) {
    JsonNode fieldsNode = injectorContract.getConvertedContent().get(CONTRACT_CONTENT_FIELDS);
    Set<TargetType> supportedTargetTypes = new HashSet<>();
    for (JsonNode field : fieldsNode) {
      String type = field.path(CONTRACT_ELEMENT_CONTENT_TYPE).asText();
      switch (type) {
        case CONTRACT_ELEMENT_CONTENT_TYPE_ASSET_GROUP ->
            supportedTargetTypes.add(TargetType.ASSETS_GROUPS);
        case CONTRACT_ELEMENT_CONTENT_TYPE_ASSET -> supportedTargetTypes.add(TargetType.ASSETS);
        case CONTRACT_ELEMENT_CONTENT_TYPE_TEAM -> supportedTargetTypes.add(TargetType.TEAMS);
        default -> {
          // ignore other types: expectations, text, textarea
        }
      }
    }

    return supportedTargetTypes.contains(targetType);
  }

  // -- CRITERIA BUILDER --
  private record OutputModeConfig(
      TriConsumer<CriteriaBuilder, CriteriaQuery<Tuple>, Root<InjectorContract>> selector,
      Function<Tuple, ? extends InjectorContractBaseOutput> mapper) {}

  /** Maps each output mode to its criteria selector and tuple mapper. */
  public enum OutputMode {
    FULL,
    THREAT_ARSENAL,
    THREAT_ARSENAL_CONTENT,
    BASE
  }

  private final Map<OutputMode, OutputModeConfig> CONFIGS =
      Map.of(
          OutputMode.FULL,
          new OutputModeConfig(this::selectForInjectorContractFull, this::mapFull),
          OutputMode.BASE,
          new OutputModeConfig(this::selectForInjectorContractBase, this::mapBase),
          OutputMode.THREAT_ARSENAL,
          new OutputModeConfig(
              this::selectForInjectorContractThreatArsenal, this::mapThreatArsenal),
          OutputMode.THREAT_ARSENAL_CONTENT,
          new OutputModeConfig(
              this::selectForInjectorContractThreatArsenalContent, this::mapThreatArsenalContent));

  /**
   * Returns a page of injector contracts using the requested output mode.
   *
   * @param specification filtering/search specification for returned items
   * @param specificationCount specification used to compute total count
   * @param pageable pagination and sorting information
   * @param mode output mode (defaults to FULL when null)
   * @param idsToIgnore ids to exclude from research
   * @param idsToProcess ids to include on research
   * @return page of contracts mapped to the selected output format
   */
  public PageImpl<? extends InjectorContractBaseOutput> getSinglePage(
      Specification<InjectorContract> specification,
      Specification<InjectorContract> specificationCount,
      Pageable pageable,
      OutputMode mode,
      List<String> idsToIgnore,
      List<String> idsToProcess) {
    OutputMode safeMode = (mode == null) ? OutputMode.FULL : mode;
    OutputModeConfig config = CONFIGS.get(safeMode);

    if (!CollectionUtils.isEmpty(idsToIgnore)) {
      specification =
          specification.and(
              (root, query, cb) ->
                  cb.not(
                      root.get(InjectorContract.COMPOSITE_ID_FIELD_NAME)
                          .get(InjectorContract.ID_FIELD_NAME)
                          .in(idsToIgnore)));
    }
    if (!CollectionUtils.isEmpty(idsToProcess)) {
      specification =
          specification.and(
              (root, query, cb) ->
                  root.get(InjectorContract.COMPOSITE_ID_FIELD_NAME)
                      .get(InjectorContract.ID_FIELD_NAME)
                      .in(idsToProcess));
    }

    QuerySetup qs = setupQuery(specification, specificationCount, pageable, config.selector());

    List<? extends InjectorContractBaseOutput> results =
        qs.query.getResultList().stream().map(config.mapper()).toList();

    return new PageImpl<>(results, pageable, qs.total);
  }

  private record InjectorContractQueryContext(
      Join<InjectorContract, Payload> payloadJoin,
      Join<Payload, CollectorType> payloadCollectorTypeJoin,
      Join<InjectorContract, Injector> injectorJoin,
      Expression<String[]> injectorIdsExpression,
      Expression<String[]> injectorNamesExpression,
      Expression<String[]> injectorContractDomainsIdsExpression,
      Expression<String[]> attackPatternIdsExpression,
      Expression<String[]> tagsIdsExpression) {}

  private InjectorContractQueryContext buildCommonInjectorContractContext(
      CriteriaBuilder cb, Root<InjectorContract> injectorContractRoot) {
    Join<InjectorContract, Payload> payloadJoin = createLeftJoin(injectorContractRoot, "payload");
    Join<Payload, CollectorType> payloadCollectorTypeJoin =
        payloadJoin.join("collectorType", JoinType.LEFT);
    Join<InjectorContract, Injector> injectorContractInjectorJoin =
        createLeftJoin(injectorContractRoot, "injectors");

    Expression<String[]> injectorContractDomainsIdsExpression =
        createJoinArrayAggOnId(cb, injectorContractRoot, "domains");

    Expression<String[]> attackPatternIdsExpression =
        createJoinArrayAggOnId(cb, injectorContractRoot, "attackPatterns");

    Expression<String[]> tagsIdsExpression =
        createJoinArrayAggOnIdForJoin(cb, injectorContractRoot, "tags");

    Expression<String[]> injectorIdsExpression =
        arrayAggOnId((HibernateCriteriaBuilder) cb, injectorContractInjectorJoin);

    HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
    Expression<String> injectorNameNull = hcb.nullLiteral(String.class);

    Expression<String[]> injectorNamesRaw =
        hcb.arrayAgg(null, injectorContractInjectorJoin.get("name"));

    Expression<String[]> injectorNamesExpression =
        hcb.<String>arrayRemove(injectorNamesRaw, (Expression<String>) injectorNameNull);

    return new InjectorContractQueryContext(
        payloadJoin,
        payloadCollectorTypeJoin,
        injectorContractInjectorJoin,
        injectorIdsExpression,
        injectorNamesExpression,
        injectorContractDomainsIdsExpression,
        attackPatternIdsExpression,
        tagsIdsExpression);
  }

  private void selectForInjectorContractFull(
      @NotNull final CriteriaBuilder cb,
      @NotNull final CriteriaQuery<Tuple> cq,
      @NotNull final Root<InjectorContract> injectorContractRoot) {

    InjectorContractQueryContext ctx = buildCommonInjectorContractContext(cb, injectorContractRoot);

    // SELECT
    cq.multiselect(
        injectorContractRoot.get("compositeId").get("id").alias("injector_contract_id"),
        injectorContractRoot.get("externalId").alias("injector_contract_external_id"),
        injectorContractRoot.get("labels").alias("injector_contract_labels"),
        injectorContractRoot.get("content").alias("injector_contract_content"),
        injectorContractRoot.get("platforms").alias("injector_contract_platforms"),
        ctx.payloadJoin().get("type").alias("payload_type"),
        ctx.payloadCollectorTypeJoin().get("name").alias("collector_type"),
        cb.least(ctx.injectorJoin().<String>get("type")).alias("injector_contract_injector_type"),
        ctx.attackPatternIdsExpression().alias("injector_contract_attack_patterns"),
        ctx.tagsIdsExpression().alias("injector_contract_tags"),
        ctx.injectorContractDomainsIdsExpression().alias("injector_contract_domains"),
        injectorContractRoot.get("updatedAt").alias("injector_contract_updated_at"),
        ctx.payloadJoin().get("executionArch").alias("payload_execution_arch"),
        ctx.injectorIdsExpression().alias("injector_contract_injector_ids"),
        ctx.injectorNamesExpression().alias("injector_contract_injector_names"));

    // GROUP BY
    cq.groupBy(getCommonGroupBy(injectorContractRoot, ctx));
  }

  private InjectorContractFullOutput mapFull(Tuple tuple) {
    String[] injectorIdsArray = tuple.get("injector_contract_injector_ids", String[].class);
    String[] injectorNamesArray = tuple.get("injector_contract_injector_names", String[].class);
    Map<String, String> injectorNames = buildInjectorNamesMap(injectorIdsArray, injectorNamesArray);
    return new InjectorContractFullOutput(
        tuple.get("injector_contract_id", String.class),
        tuple.get("injector_contract_external_id", String.class),
        tuple.get("injector_contract_labels", Map.class),
        tuple.get("injector_contract_content", String.class),
        tuple.get("injector_contract_platforms", Endpoint.PLATFORM_TYPE[].class),
        tuple.get("payload_type", String.class),
        tuple.get("collector_type", String.class),
        tuple.get("injector_contract_injector_type", String.class),
        tuple.get("injector_contract_attack_patterns", String[].class),
        tuple.get("injector_contract_tags", String[].class),
        tuple.get("injector_contract_domains", String[].class),
        tuple.get("injector_contract_updated_at", Instant.class),
        tuple.get("payload_execution_arch", Payload.PAYLOAD_EXECUTION_ARCH.class),
        injectorNames);
  }

  /**
   * Builds a map of injector ID → injector name from parallel arrays returned by array_agg. Both
   * arrays are in the same order because they come from the same GROUP BY aggregation.
   */
  private Map<String, String> buildInjectorNamesMap(
      String[] injectorIdsArray, String[] injectorNamesArray) {
    if (injectorIdsArray == null || injectorNamesArray == null) {
      return new LinkedHashMap<>();
    }
    Map<String, String> map = new LinkedHashMap<>();
    int len = Math.min(injectorIdsArray.length, injectorNamesArray.length);
    for (int i = 0; i < len; i++) {
      if (injectorIdsArray[i] != null) {
        map.put(injectorIdsArray[i], injectorNamesArray[i]);
      }
    }
    return map;
  }

  private List<Expression<?>> getCommonGroupBy(
      @NotNull final Root<InjectorContract> injectorContractRoot,
      @NotNull InjectorContractQueryContext ctx) {
    return Arrays.asList(
        injectorContractRoot.get("compositeId"),
        ctx.payloadJoin().get("id"),
        ctx.payloadCollectorTypeJoin().get("id"),
        ctx.injectorJoin().get("id"));
  }

  private void selectForInjectorContractThreatArsenal(
      @NotNull final CriteriaBuilder cb,
      @NotNull final CriteriaQuery<Tuple> cq,
      @NotNull final Root<InjectorContract> injectorContractRoot) {
    InjectorContractQueryContext ctx = buildCommonInjectorContractContext(cb, injectorContractRoot);

    cq.multiselect(
        injectorContractRoot.get("compositeId").get("id").alias("injector_contract_id"),
        injectorContractRoot.get("externalId").alias("injector_contract_external_id"),
        injectorContractRoot.get("labels").alias("injector_contract_labels"),
        injectorContractRoot.get("platforms").alias("injector_contract_platforms"),
        ctx.payloadJoin().get("type").alias("payload_type"),
        ctx.payloadCollectorTypeJoin().get("name").alias("collector_type"),
        ctx.injectorJoin().get("type").alias("injector_contract_injector_type"),
        ctx.tagsIdsExpression().alias("injector_contract_tags"),
        ctx.injectorContractDomainsIdsExpression().alias("injector_contract_domains"),
        ctx.payloadJoin().get("status").alias("payload_status"),
        ctx.payloadJoin().get("id").alias("payload_id"),
        ctx.attackPatternIdsExpression().alias("injector_contract_attack_patterns"),
        injectorContractRoot.get("updatedAt").alias("injector_contract_updated_at"));

    List<Expression<?>> groupBy = new ArrayList<>(getCommonGroupBy(injectorContractRoot, ctx));
    groupBy.add(ctx.injectorJoin().get("type"));
    cq.groupBy(groupBy);
  }

  private void selectForInjectorContractThreatArsenalContent(
      @NotNull final CriteriaBuilder cb,
      @NotNull final CriteriaQuery<Tuple> cq,
      @NotNull final Root<InjectorContract> injectorContractRoot) {
    InjectorContractQueryContext ctx = buildCommonInjectorContractContext(cb, injectorContractRoot);

    cq.multiselect(
        injectorContractRoot.get("compositeId").get("id").alias("injector_contract_id"),
        ctx.payloadJoin().get("id").alias("payload_id"),
        ctx.payloadJoin().get("type").alias("payload_type"),
        ctx.payloadJoin().get("status").alias("payload_status"),
        ctx.injectorJoin().get("type").alias("injector_type"),
        ctx.injectorJoin().get("name").alias("injector_name"),
        injectorContractRoot.get("labels").alias("injector_contract_labels"),
        ctx.payloadJoin().get("executionArch").alias("payload_execution_arch"),
        injectorContractRoot.get("platforms").alias("injector_contract_platforms"),
        injectorContractRoot.get("content").alias("injector_contract_content"));

    List<Expression<?>> groupBy = new ArrayList<>(getCommonGroupBy(injectorContractRoot, ctx));
    groupBy.add(ctx.injectorJoin().get("type"));
    groupBy.add(ctx.injectorJoin().get("name"));
    cq.groupBy(groupBy);
  }

  private ThreatArsenalAction mapThreatArsenal(Tuple tuple) {
    String payloadId = tuple.get("payload_id", String.class);
    PayloadSimple payload =
        payloadId != null
            ? new PayloadSimple(
                payloadId,
                tuple.get("payload_type", String.class),
                tuple.get("collector_type", String.class),
                tuple.get("payload_status", Payload.PAYLOAD_STATUS.class))
            : null;

    return new ThreatArsenalAction(
        tuple.get("injector_contract_id", String.class),
        tuple.get("injector_contract_external_id", String.class),
        tuple.get("injector_contract_updated_at", Instant.class),
        tuple.get("injector_contract_labels", Map.class),
        tuple.get("injector_contract_injector_type", String.class),
        tuple.get("injector_contract_domains", String[].class),
        tuple.get("injector_contract_platforms", Endpoint.PLATFORM_TYPE[].class),
        tuple.get("injector_contract_tags", String[].class),
        payload,
        tuple.get("injector_contract_attack_patterns", String[].class));
  }

  private ThreatArsenalActionWithContentOutput mapThreatArsenalContent(Tuple tuple) {
    return new ThreatArsenalActionWithContentOutput(
        tuple.get("injector_contract_id", String.class),
        tuple.get("payload_type", String.class),
        tuple.get("injector_type", String.class),
        tuple.get("injector_name", String.class),
        tuple.get("injector_contract_labels", Map.class),
        tuple.get("payload_execution_arch", Payload.PAYLOAD_EXECUTION_ARCH.class),
        tuple.get("injector_contract_platforms", Endpoint.PLATFORM_TYPE[].class),
        tuple.get("injector_contract_content", String.class));
  }

  private void selectForInjectorContractBase(
      @NotNull final CriteriaBuilder cb,
      @NotNull final CriteriaQuery<Tuple> cq,
      @NotNull final Root<InjectorContract> injectorContractRoot) {
    // SELECT
    cq.multiselect(
        injectorContractRoot.get("compositeId").get("id").alias("injector_contract_id"),
        injectorContractRoot.get("externalId").alias("injector_contract_external_id"),
        injectorContractRoot.get("updatedAt").alias("injector_contract_updated_at"));
  }

  private InjectorContractBaseOutput mapBase(Tuple tuple) {
    return new InjectorContractBaseOutput(
        tuple.get("injector_contract_id", String.class),
        tuple.get("injector_contract_external_id", String.class),
        tuple.get("injector_contract_updated_at", Instant.class));
  }

  /**
   * Converts input data to an injector contract entity.
   *
   * <p>Used during injector registration to create contract entities from input definitions.
   *
   * @param in the contract input data
   * @param injector the parent injector
   * @return the created injector contract (not yet persisted)
   */
  // TODO JRI => REFACTOR TO RELY ON INJECTOR SERVICE
  public InjectorContract convertInjectorFromInput(InjectorContractInput in, Injector injector) {
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setId(in.getId());
    injectorContract.setManual(in.isManual());
    injectorContract.setLabels(in.getLabels());
    injectorContract.addInjector(injector);
    injectorContract.setTenant(injector.getTenant());
    injectorContract.setContent(in.getContent());
    injectorContract.setAtomicTesting(in.isAtomicTesting());
    injectorContract.setPlatforms(in.getPlatforms());
    if (!in.getAttackPatternsExternalIds().isEmpty()) {
      List<AttackPattern> attackPatterns =
          fromIterable(
              attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(
                  in.getAttackPatternsExternalIds(), injector.getTenant().getId()));
      injectorContract.setAttackPatterns(attackPatterns);
    } else {
      injectorContract.setAttackPatterns(new ArrayList<>());
    }
    if (!injector.isPayloads() && in.getDomains() != null) {
      injectorContract.setDomains(
          this.domainService.upserts(in.getDomains(), injector.getTenant().getId()));
    }
    return injectorContract;
  }

  /**
   * Computes the count of injector contracts grouped by domain.
   *
   * @param input the search and filtering criteria
   * @return the list of domain counts derived from effective contract associations
   */
  public List<InjectorContractDomainCountOutput> getDomainCounts(SearchPaginationInput input) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    Specification<InjectorContract> filterSpec = computeFilterGroupJpa(input.getFilterGroup());
    Specification<InjectorContract> searchSpec = computeSearchJpa(input.getTextSearch());
    Specification<InjectorContract> baseSpec =
        Specification.<InjectorContract>unrestricted().and(filterSpec).and(searchSpec);

    CriteriaQuery<InjectorContractDomainCountOutput> qDirect =
        cb.createQuery(InjectorContractDomainCountOutput.class);
    Root<InjectorContract> root = qDirect.from(InjectorContract.class);
    Join<InjectorContract, Domain> domainsJoin = root.join("domains", JoinType.INNER);

    Predicate predicate = baseSpec.toPredicate(root, qDirect, cb);
    if (predicate != null) {
      qDirect.where(predicate);
    }

    qDirect.multiselect(domainsJoin.get("id"), cb.countDistinct(root));
    qDirect.groupBy(domainsJoin.get("id"));

    return entityManager.createQuery(qDirect).getResultList();
  }

  @Override
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    try {
      for (String injectorContractId : listDefaultInjectorContract) {
        InjectorContractId compositeId =
            new InjectorContractId(injectorContractId, TenantContext.getCurrentTenant());
        InjectorContract source = entityManager.find(InjectorContract.class, compositeId);
        if (source == null) {
          continue; // contract does not exist for the current tenant — skip
        }
        entityManager.detach(source);
        source.setTenant(tenant);
        entityManager.persist(source);
      }
    } catch (Exception e) {
      log.error(
          "Failed to create default injector contracts for tenant {}: {}",
          tenant.getId(),
          e.getMessage());
      throw new DependenciesManagerException(
          "Failed to create default injector contracts for tenant " + tenant.getName(), e);
    }
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) throws DependenciesManagerException {
    try {
      for (String injectorContractId : listDefaultInjectorContract) {
        injectorContractRepository.deleteById(new InjectorContractId(injectorContractId, tenantId));
      }

    } catch (Exception e) {
      log.error(
          "Failed to create default injector contracts for tenant {}: {}",
          tenantId,
          e.getMessage());
      throw new DependenciesManagerException(
          "Failed to create default injector contracts for tenant " + tenantId, e);
    }
  }
}
