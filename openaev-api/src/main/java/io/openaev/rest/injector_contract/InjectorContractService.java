package io.openaev.rest.injector_contract;

import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.database.model.InjectorContract.*;
import static io.openaev.helper.DatabaseHelper.updateRelation;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.openaev.utils.JpaUtils.*;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawInjectorsContracts;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.specification.InjectorContractSpecification;
import io.openaev.injector_contract.Contract;
import io.openaev.injectors.email.EmailContract;
import io.openaev.injectors.ovh.OvhSmsContract;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.form.InjectorContractAddInput;
import io.openaev.rest.injector_contract.form.InjectorContractInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateMappingInput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractDomainCountOutput;
import io.openaev.rest.injector_contract.output.InjectorContractFullOutput;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.service.UserService;
import io.openaev.utils.TargetType;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

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
public class InjectorContractService {

  @PersistenceContext private EntityManager entityManager;
  @Resource private ObjectMapper mapper;

  private final InjectorContractRepository injectorContractRepository;
  private final AttackPatternService attackPatternService;
  private final VulnerabilityService vulnerabilityService;
  private final DomainService domainService;
  private final InjectorRepository injectorRepository;
  private final UserService userService;
  private final AttackPatternRepository attackPatternRepository;

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
      boolean include_full_details) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<InjectorContract> injectorContractRoot = cq.from(InjectorContract.class);
    if (include_full_details) {
      selectForInjectorContractFull(cb, cq, injectorContractRoot);
    } else {
      selectForInjectorContractBase(cb, cq, injectorContractRoot);
    }

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

  /**
   * Retrieves a page of injector contracts with full details.
   *
   * <p>Includes attack patterns, domains, injector information, and payload details.
   *
   * @param specification the filter specification
   * @param specificationCount the count specification (may differ for accurate counting)
   * @param pageable the pagination parameters
   * @return a page of full injector contract outputs
   */
  public PageImpl<InjectorContractFullOutput> getSinglePageFullDetails(
      @Nullable final Specification<InjectorContract> specification,
      @Nullable final Specification<InjectorContract> specificationCount,
      @NotNull final Pageable pageable) {
    QuerySetup qs = setupQuery(specification, specificationCount, pageable, true);

    // -- EXECUTION --
    List<InjectorContractFullOutput> injectorContractFullOutputs =
        execInjectorFullContract(qs.query);

    return new PageImpl<>(injectorContractFullOutputs, pageable, qs.total);
  }

  /**
   * Retrieves a page of injector contracts with base details only.
   *
   * <p>Returns minimal information for list views and dropdowns.
   *
   * @param specification the filter specification
   * @param specificationCount the count specification
   * @param pageable the pagination parameters
   * @return a page of base injector contract outputs
   */
  public PageImpl<InjectorContractBaseOutput> getSinglePageBaseDetails(
      @Nullable final Specification<InjectorContract> specification,
      @Nullable final Specification<InjectorContract> specificationCount,
      @NotNull final Pageable pageable) {
    QuerySetup qs = setupQuery(specification, specificationCount, pageable, false);

    // -- EXECUTION --
    List<InjectorContractBaseOutput> injectorContractBaseOutputs =
        execInjectorBaseContract(qs.query);

    return new PageImpl<>(injectorContractBaseOutputs, pageable, qs.total);
  }

  public Iterable<RawInjectorsContracts> getAllRawInjectContracts() {
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_PAYLOADS)) {
      return injectorContractRepository.getAllRawInjectorsContracts();
    }
    return injectorContractRepository.getAllRawInjectorsContractsWithoutPayloadOrGranted(
        currentUser.getId());
  }

  /**
   * Retrieves a single injector contract by ID or external ID.
   *
   * @param injectorContractId the contract ID or external ID
   * @return the injector contract
   * @throws ElementNotFoundException if not found
   */
  public InjectorContract getSingleInjectorContract(String injectorContractId) {
    return injectorContractRepository
        .findByIdOrExternalId(injectorContractId, injectorContractId)
        .orElseThrow(ElementNotFoundException::new);
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

    injectorContract.setInjector(
        updateRelation(input.getInjectorId(), injectorContract.getInjector(), injectorRepository));
    injectorContract.setDomains(
        !injectorContract.getInjector().isPayloads()
            ? this.domainService.upserts(input.getDomains())
            : new HashSet<>());
    return injectorContractRepository.save(injectorContract);
  }

  public InjectorContract createBuiltinInjectorContract(
      Contract source, Injector injector, boolean isPayloads) {
    InjectorContract target = new InjectorContract();
    target.setId(source.getId());
    target.setInjector(injector);

    applyBuiltinContractData(target, source, isPayloads);
    return target;
  }

  public void updateBuiltInInjectorContract(
      InjectorContract target, Contract source, boolean isPayloads) {
    applyBuiltinContractData(target, source, isPayloads);
  }

  private void applyBuiltinContractData(
      InjectorContract target, Contract source, boolean isPayloads) {
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
              attackPatternRepository.findAllByExternalIdInIgnoreCase(
                  source.getAttackPatternsExternalIds()));
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

    if (!isPayloads) {
      Set<Domain> currentDomains = this.domainService.upserts(target.getDomains());
      Set<Domain> domainsToAdd = this.domainService.upserts(source.getDomains());
      target.setDomains(this.domainService.mergeDomains(currentDomains, domainsToAdd));
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
    injectorContract.setDomains(this.domainService.upserts(input.getDomains()));

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

  private void selectForInjectorContractFull(
      @NotNull final CriteriaBuilder cb,
      @NotNull final CriteriaQuery<Tuple> cq,
      @NotNull final Root<InjectorContract> injectorContractRoot) {
    // Joins
    Join<InjectorContract, Payload> injectorContractPayloadJoin =
        createLeftJoin(injectorContractRoot, "payload");
    Join<Payload, Collector> payloadCollectorJoin =
        injectorContractPayloadJoin.join("collector", JoinType.LEFT);
    Join<InjectorContract, Injector> injectorContractInjectorJoin =
        createLeftJoin(injectorContractRoot, "injector");
    // Array aggregations
    Expression<String[]> attackPatternIdsExpression =
        createJoinArrayAggOnId(cb, injectorContractRoot, "attackPatterns");

    Expression<String[]> domainsIdsExpression =
        createJoinArrayAggOnId(cb, injectorContractRoot, "domains");

    Expression<String[]> payloadDomainsIdsExpression =
        createJoinArrayAggOnIdForJoin(cb, injectorContractPayloadJoin, "domains");

    // SELECT
    cq.multiselect(
            injectorContractRoot.get("id").alias("injector_contract_id"),
            injectorContractRoot.get("externalId").alias("injector_contract_external_id"),
            injectorContractRoot.get("labels").alias("injector_contract_labels"),
            injectorContractRoot.get("content").alias("injector_contract_content"),
            injectorContractRoot.get("platforms").alias("injector_contract_platforms"),
            injectorContractPayloadJoin.get("type").alias("payload_type"),
            payloadCollectorJoin.get("type").alias("collector_type"),
            injectorContractInjectorJoin.get("type").alias("injector_contract_injector_type"),
            injectorContractInjectorJoin.get("name").alias("injector_contract_injector_name"),
            attackPatternIdsExpression.alias("injector_contract_attack_patterns"),
            payloadDomainsIdsExpression.alias("payload_domains"),
            domainsIdsExpression.alias("injector_contract_domains"),
            injectorContractRoot.get("updatedAt").alias("injector_contract_updated_at"),
            injectorContractPayloadJoin.get("executionArch").alias("payload_execution_arch"))
        .distinct(true);

    // GROUP BY
    cq.groupBy(
        Arrays.asList(
            injectorContractRoot.get("id"),
            injectorContractPayloadJoin.get("id"),
            payloadCollectorJoin.get("id"),
            injectorContractInjectorJoin.get("id")));
  }

  private List<InjectorContractFullOutput> execInjectorFullContract(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple ->
                new InjectorContractFullOutput(
                    tuple.get("injector_contract_id", String.class),
                    tuple.get("injector_contract_external_id", String.class),
                    tuple.get("injector_contract_labels", Map.class),
                    tuple.get("injector_contract_content", String.class),
                    tuple.get("injector_contract_platforms", Endpoint.PLATFORM_TYPE[].class),
                    tuple.get("payload_type", String.class),
                    tuple.get("injector_contract_injector_name", String.class),
                    tuple.get("collector_type", String.class),
                    tuple.get("injector_contract_injector_type", String.class),
                    tuple.get("injector_contract_attack_patterns", String[].class),
                    resolveEffectiveDomains(
                        tuple.get("injector_contract_domains", String[].class),
                        tuple.get("payload_domains", String[].class)),
                    tuple.get("injector_contract_updated_at", Instant.class),
                    tuple.get("payload_execution_arch", Payload.PAYLOAD_EXECUTION_ARCH.class)))
        .toList();
  }

  private List<String> resolveEffectiveDomains(String[] injectorDomains, String[] payloadDomains) {
    String[] effectiveDomains =
        (payloadDomains != null && payloadDomains.length > 0) ? payloadDomains : injectorDomains;
    if (effectiveDomains == null) {
      return List.of();
    }
    return Arrays.stream(effectiveDomains).filter(Objects::nonNull).distinct().toList();
  }

  private void selectForInjectorContractBase(
      @NotNull final CriteriaBuilder cb,
      @NotNull final CriteriaQuery<Tuple> cq,
      @NotNull final Root<InjectorContract> injectorContractRoot) {
    // SELECT
    cq.multiselect(
            injectorContractRoot.get("id").alias("injector_contract_id"),
            injectorContractRoot.get("externalId").alias("injector_contract_external_id"),
            injectorContractRoot.get("updatedAt").alias("injector_contract_updated_at"))
        .distinct(true);
  }

  private List<InjectorContractBaseOutput> execInjectorBaseContract(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple ->
                new InjectorContractBaseOutput(
                    tuple.get("injector_contract_id", String.class),
                    tuple.get("injector_contract_external_id", String.class),
                    tuple.get("injector_contract_updated_at", Instant.class)))
        .toList();
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
    injectorContract.setInjector(injector);
    injectorContract.setContent(in.getContent());
    injectorContract.setAtomicTesting(in.isAtomicTesting());
    injectorContract.setPlatforms(in.getPlatforms());
    if (!in.getAttackPatternsExternalIds().isEmpty()) {
      List<AttackPattern> attackPatterns =
          fromIterable(
              attackPatternRepository.findAllByExternalIdInIgnoreCase(
                  in.getAttackPatternsExternalIds()));
      injectorContract.setAttackPatterns(attackPatterns);
    } else {
      injectorContract.setAttackPatterns(new ArrayList<>());
    }
    if (!injector.isPayloads() && in.getDomains() != null) {
      injectorContract.setDomains(this.domainService.upserts(in.getDomains()));
    }
    return injectorContract;
  }

  /**
   * Computes the count of injector contracts grouped by domain.
   *
   * <p>This method applies a specific precedence logic for domain resolution:
   *
   * <ul>
   *   <li>**Priority**: If the contract's payload defines specific domains, these are used for the
   *       count.
   *   <li>**Fallback**: If the payload has no domains (or is null), the contract's direct domains
   *       are used.
   * </ul>
   *
   * <p>It executes two distinct criteria queries and merges the results to generate the final
   * distribution.
   *
   * @param input the search and filtering criteria
   * @return the list of domain counts derived from effective contract associations
   */
  public List<InjectorContractDomainCountOutput> getDomainCounts(SearchPaginationInput input) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    Specification<InjectorContract> filterSpec = computeFilterGroupJpa(input.getFilterGroup());
    Specification<InjectorContract> searchSpec = computeSearchJpa(input.getTextSearch());
    Specification<InjectorContract> baseSpec = Specification.where(filterSpec).and(searchSpec);

    CriteriaQuery<InjectorContractDomainCountOutput> qPayload =
        cb.createQuery(InjectorContractDomainCountOutput.class);
    Root<InjectorContract> rootPayload = qPayload.from(InjectorContract.class);
    Join<InjectorContract, Payload> payloadJoin = rootPayload.join("payload", JoinType.INNER);
    Join<Payload, Domain> payloadDomainsJoin = payloadJoin.join("domains", JoinType.INNER);

    Predicate payloadPredicate = baseSpec.toPredicate(rootPayload, qPayload, cb);
    if (payloadPredicate != null) {
      qPayload.where(payloadPredicate);
    }

    qPayload.multiselect(payloadDomainsJoin.get("id"), cb.countDistinct(rootPayload));
    qPayload.groupBy(payloadDomainsJoin.get("id"));

    List<InjectorContractDomainCountOutput> payloadCounts =
        entityManager.createQuery(qPayload).getResultList();

    CriteriaQuery<InjectorContractDomainCountOutput> qDirect =
        cb.createQuery(InjectorContractDomainCountOutput.class);
    Root<InjectorContract> rootDirect = qDirect.from(InjectorContract.class);
    Join<InjectorContract, Domain> directDomainsJoin = rootDirect.join("domains", JoinType.INNER);
    Join<InjectorContract, Payload> pCheckJoin = rootDirect.join("payload", JoinType.LEFT);

    Predicate noPayloadDomains =
        cb.or(cb.isNull(pCheckJoin), cb.isEmpty(pCheckJoin.get("domains")));

    Predicate directUserPredicate = baseSpec.toPredicate(rootDirect, qDirect, cb);
    if (directUserPredicate != null) {
      qDirect.where(cb.and(directUserPredicate, noPayloadDomains));
    } else {
      qDirect.where(noPayloadDomains);
    }

    qDirect.multiselect(directDomainsJoin.get("id"), cb.countDistinct(rootDirect));
    qDirect.groupBy(directDomainsJoin.get("id"));

    List<InjectorContractDomainCountOutput> directCounts =
        entityManager.createQuery(qDirect).getResultList();

    Map<String, Long> mergedMap = new HashMap<>();
    Stream.concat(payloadCounts.stream(), directCounts.stream())
        .forEach(output -> mergedMap.merge(output.getDomain(), output.getCount(), Long::sum));

    return mergedMap.entrySet().stream()
        .map(entry -> new InjectorContractDomainCountOutput(entry.getKey(), entry.getValue()))
        .toList();
  }
}
