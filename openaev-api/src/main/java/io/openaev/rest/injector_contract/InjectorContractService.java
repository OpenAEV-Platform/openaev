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
import io.openaev.config.OpenAEVAnonymous;
import io.openaev.config.SessionHelper;
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
import io.openaev.rest.inject.service.InjectIndexCleanupService;
import io.openaev.rest.injector_contract.form.*;
import io.openaev.rest.injector_contract.output.InjectorContractAuthorCountOutput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractDomainCountOutput;
import io.openaev.rest.injector_contract.output.InjectorContractFullOutput;
import io.openaev.rest.payload.output.PayloadSimple;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.service.InjectorService;
import io.openaev.service.UserService;
import io.openaev.service.organization.OrganizationService;
import io.openaev.utils.TargetType;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

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
  private final OrganizationService organizationService;
  private final InjectIndexCleanupService injectIndexCleanupService;

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
        // User-facing wording: the entity is exposed as "threat arsenal item" everywhere.
        .orElseThrow(() -> new ElementNotFoundException("Threat arsenal item not found"));
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
        || currentUser.getCapabilities().contains(Capability.ACCESS_THREAT_ARSENALS)) {
      return injectorContractRepository.getAllRawInjectorsContracts();
    }
    return injectorContractRepository.getAllRawInjectorsContractsGranted(currentUser.getId());
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
  @Transactional(rollbackFor = Exception.class)
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

    // Authorship is a function of the creation's provenance (machine sync vs interactive),
    // never of which credentials happened to authenticate the HTTP call.
    if (isMachineProvenance(injectorContract, injector)) {
      attributeToPublisher(injectorContract, injector);
    } else {
      attributeToCreatingUser(injectorContract);
    }

    injectorContract.setDomains(
        injector != null && !injector.isPayloads()
            ? this.domainService.upserts(input.getDomains(), TenantContext.getCurrentTenant())
            : new HashSet<>());
    InjectorContract saved = injectorContractRepository.save(injectorContract);
    // The join row is persisted through the contract's cascade over the join entity (addInjector)
    injectorRepository.save(injector);
    return saved;
  }

  public InjectorContract createBuiltinInjectorContract(
      Contract source, Injector injector, boolean isPayloads) {
    InjectorContract target = new InjectorContract();
    target.setId(source.getId());
    // Set the tenant before linking so the cross-tenant guard in the join-entity constructor is
    // active here, then link the injector on the contract's owning side (injectorLinks). The join
    // row is persisted by the cascade when the contract is saved.
    target.setTenant(new Tenant(injector.getTenantId()));
    if (injector != null) {
      target.addInjector(injector);
    }

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
                  source.getAttackPatternsExternalIds(), injector.getTenantId()));
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
          this.domainService.upsertDomainEntities(target.getDomains(), injector.getTenantId());
      Set<Domain> domainsToAdd =
          this.domainService.upsertDomainEntities(source.getDomains(), injector.getTenantId());
      target.setDomains(
          this.domainService.mergeDomains(
              currentDomains, domainsToAdd, new Tenant(injector.getTenantId())));
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

    reconcileExternalInjectorAuthorship(injectorContract);

    injectorContract.setUpdatedAt(Instant.now());
    return injectorContractRepository.save(injectorContract);
  }

  // -- CONTRACT AUTHORSHIP --
  //
  // Authorship is derived from the provenance of the data, never from the HTTP session:
  //
  //   machine provenance  -> the injector's publisher organization (e.g. "Nuclei")
  //   interactive         -> the human creating the contract through the UI / ad-hoc API
  //
  // The session user MUST NOT be used as the discriminator: external injector sync loops
  // (Nuclei per-CVE contracts, nmap...) authenticate their background API calls with the
  // registering user's token, so a real session user is present during machine syncs. The
  // reliable provenance marker is the external contract id: sync loops always send
  // `external_contract_id`, interactive creations never do.

  /**
   * A creation has machine provenance when it targets an external injector with a payload-less
   * contract and either carries an injector-generated external id (all sync loops send one) or
   * arrives anonymously (fallback for machine callers that predate external ids).
   */
  private boolean isMachineProvenance(InjectorContract contract, Injector injector) {
    boolean externalPayloadless =
        injector != null && injector.isExternal() && contract.getPayload() == null;
    return externalPayloadless
        && (StringUtils.hasText(contract.getExternalId())
            || SessionHelper.currentUser() instanceof OpenAEVAnonymous);
  }

  /**
   * Attributes a machine-synced contract to its publisher organization. Deliberately never reads
   * the session: whichever token authenticated the background call is irrelevant to authorship.
   */
  private void attributeToPublisher(InjectorContract contract, Injector injector) {
    contract.setAuthorUser(null);
    contract.setAuthorOrganization(organizationService.findOrCreateByName(injector.getName()));
  }

  /** Attributes an interactively created contract to the authenticated human creating it. */
  private void attributeToCreatingUser(InjectorContract contract) {
    if (!(SessionHelper.currentUser() instanceof OpenAEVAnonymous)) {
      contract.setAuthorUser(userService.currentUser());
    }
  }

  /**
   * Reconciles authorship of external-injector contracts on update. The Nuclei/nmap maintenance
   * loops call {@code updateInjectorContract} on every existing contract each sync cycle, so two
   * legacy cases self-repair to the publisher organization without any migration:
   *
   * <ul>
   *   <li>contracts created before authorship existed (all author FKs null);
   *   <li>machine-synced contracts wrongly stamped with a human author-user by the old
   *       session-based logic (recognizable by their injector-generated external id —
   *       interactively created contracts never carry one).
   * </ul>
   *
   * <p>Payload-based contracts and contracts with a deliberate team/organization author are never
   * touched.
   */
  private void reconcileExternalInjectorAuthorship(InjectorContract contract) {
    if (contract.getPayload() != null || contract.getAuthorTeam() != null) {
      return;
    }
    boolean authorless =
        contract.getAuthorUser() == null && contract.getAuthorOrganization() == null;
    boolean misattributedSync =
        contract.getAuthorUser() != null && StringUtils.hasText(contract.getExternalId());
    if (!authorless && !misattributedSync) {
      return;
    }
    contract.getInjectors().stream()
        .filter(Injector::isExternal)
        .findFirst()
        .ifPresent(injector -> attributeToPublisher(contract, injector));
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
   * Updates the attack patterns, domains and tags mappings for a contract.
   *
   * @param injectorContractId the contract ID to update
   * @param input the mapping update input
   * @return the updated injector contract
   * @throws ElementNotFoundException if not found
   */
  public InjectorContract updateInjectorContractTTPDomainsAndTags(
      String injectorContractId, InjectorContractUpdateMappingInput input) {
    InjectorContract injectorContract =
        injectorContractRepository
            .findByIdOrExternalId(injectorContractId, injectorContractId)
            .orElseThrow(ElementNotFoundException::new);
    return updateInjectorContractTTPDomainsAndTags(injectorContract, input);
  }

  public InjectorContract updateInjectorContractTTPDomainsAndTags(
      InjectorContract injectorContract, InjectorContractUpdateMappingInput input) {
    injectorContract.setAttackPatterns(
        attackPatternService.findAllByInternalIdsThrowIfMissing(
            new HashSet<>(input.getAttackPatternsIds())));
    injectorContract.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    injectorContract.setDomains(iterableToSet(domainService.findAllById(input.getDomainIds())));
    injectorContract.setUpdatedAt(Instant.now());
    return injectorContractRepository.save(injectorContract);
  }

  /**
   * Deletes an injector contract.
   *
   * <p>Only custom contracts (user-created) or payload-based contracts can be deleted. Built-in,
   * non-payload contracts cannot be removed. When a payload-based contract is deleted, the
   * associated {@link Payload} is automatically removed via JPA cascade ({@code
   * CascadeType.REMOVE}).
   *
   * @param injectorContractId the contract ID to delete
   * @throws ElementNotFoundException if not found
   * @throws IllegalArgumentException if the contract is neither custom nor payload-based
   */
  public void deleteInjectorContract(final String injectorContractId) {
    InjectorContract injectorContract =
        this.injectorContractRepository
            .findByIdOrExternalId(injectorContractId, injectorContractId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Threat arsenal item not found: " + injectorContractId));
    if (!injectorContract.getCustom()) {
      throw new IllegalArgumentException(
          "This threat arsenal item can't be removed because it is not a custom one: "
              + injectorContractId);
    }
    deleteInjectorContract(injectorContract);
  }

  public void deleteInjectorContract(InjectorContract injectorContract) {
    // Same compensation as deleteInjectorContractById: the injects FK on injectors_contracts is ON
    // DELETE CASCADE, so the injects (and, one hop further, their expectations and findings) vanish
    // at the database level without a single JPA lifecycle event. The engine cascade is single-hop:
    // the contract delete event alone would never reach the expectation and finding documents,
    // which reference the INJECT id in their dependencies - they would keep feeding every
    // engine-backed statistic (dashboards, KPIs) after the contract is gone.
    String tenantId =
        injectorContract.getTenant() != null
            ? injectorContract.getTenant().getId()
            : TenantContext.getCurrentTenant();
    List<String> cascadeDeletedInjectIds =
        injectIndexCleanupService.injectIdsByContractIds(
            List.of(injectorContract.getId()), tenantId);
    this.injectorContractRepository.delete(injectorContract);
    injectIndexCleanupService.notifyEngineOfDeletedInjects(cascadeDeletedInjectIds);
  }

  /**
   * Checks whether the given injector contract is payload-based without loading the full entity.
   *
   * @param injectorContractId the contract ID to check
   * @return true if the contract exists and has a non-null payload
   */
  public boolean isPayloadBased(String injectorContractId) {
    return this.injectorContractRepository.existsByIdAndPayloadIsNotNull(injectorContractId);
  }

  /**
   * Deletes an injector contract by its ID using a direct DELETE query (no entity loading).
   *
   * @param injectorContractId the contract ID to delete
   */
  public void deleteInjectorContractById(String injectorContractId) {
    // The injects FK on injectors_contracts is ON DELETE CASCADE: collect the doomed inject ids
    // before the delete and de-index them explicitly (no JPA lifecycle fires for DB cascades).
    List<String> cascadeDeletedInjectIds =
        injectIndexCleanupService.injectIdsByContractIds(
            List.of(injectorContractId), TenantContext.getCurrentTenant());
    this.injectorContractRepository.deleteById(
        new InjectorContractId(injectorContractId, TenantContext.getCurrentTenant()));
    injectIndexCleanupService.notifyEngineOfDeletedInjects(cascadeDeletedInjectIds);
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
      Join<?, Injector> injectorJoin,
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
    Join<?, Injector> injectorContractInjectorJoin =
        injectorContractRoot.join("injectorLinks", JoinType.LEFT).join("injector", JoinType.LEFT);

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

  /**
   * Display label for a user author, mirroring {@link User#getNameOrEmail()}: "Firstname Lastname"
   * when both are set and non-blank, the email otherwise. Evaluates to NULL when the (left-joined)
   * user is absent, so outer coalesces fall through to the next author kind.
   */
  private Expression<String> userAuthorDisplayName(
      @NotNull final CriteriaBuilder cb, @NotNull final Join<?, User> userJoin) {
    Expression<String> firstname = userJoin.get("firstname");
    Expression<String> lastname = userJoin.get("lastname");
    Predicate hasFullName =
        cb.and(
            cb.isNotNull(firstname),
            cb.notEqual(cb.trim(firstname), ""),
            cb.isNotNull(lastname),
            cb.notEqual(cb.trim(lastname), ""));
    Expression<String> fullName = cb.concat(cb.concat(firstname, " "), lastname);
    return cb.<String>selectCase()
        .when(hasFullName, fullName)
        .otherwise(userJoin.<String>get("email"));
  }

  private void selectForInjectorContractThreatArsenal(
      @NotNull final CriteriaBuilder cb,
      @NotNull final CriteriaQuery<Tuple> cq,
      @NotNull final Root<InjectorContract> injectorContractRoot) {
    InjectorContractQueryContext ctx = buildCommonInjectorContractContext(cb, injectorContractRoot);

    // The author is polymorphic (user / team / organization) and stored both on
    // the contract (built-in => Filigran, custom => creator) and, for payload
    // contracts, on the payload. Resolve the id, a display label and a type by
    // coalescing contract-level author first, then payload-level author, so the
    // Threat Arsenal shows and filters by author in a single field.
    Join<InjectorContract, User> cAuthorUserJoin =
        injectorContractRoot.join("authorUser", JoinType.LEFT);
    Join<InjectorContract, Team> cAuthorTeamJoin =
        injectorContractRoot.join("authorTeam", JoinType.LEFT);
    Join<InjectorContract, Organization> cAuthorOrgJoin =
        injectorContractRoot.join("authorOrganization", JoinType.LEFT);
    Join<Payload, User> authorUserJoin = ctx.payloadJoin().join("authorUser", JoinType.LEFT);
    Join<Payload, Team> authorTeamJoin = ctx.payloadJoin().join("authorTeam", JoinType.LEFT);
    Join<Payload, Organization> authorOrgJoin =
        ctx.payloadJoin().join("authorOrganization", JoinType.LEFT);

    Expression<String> authorId =
        cb.<String>coalesce()
            .value(cAuthorUserJoin.get("id"))
            .value(cAuthorTeamJoin.get("id"))
            .value(cAuthorOrgJoin.get("id"))
            .value(authorUserJoin.get("id"))
            .value(authorTeamJoin.get("id"))
            .value(authorOrgJoin.get("id"));
    Expression<String> authorName =
        cb.<String>coalesce()
            .value(userAuthorDisplayName(cb, cAuthorUserJoin))
            .value(cAuthorTeamJoin.get("name"))
            .value(cAuthorOrgJoin.get("name"))
            .value(userAuthorDisplayName(cb, authorUserJoin))
            .value(authorTeamJoin.get("name"))
            .value(authorOrgJoin.get("name"));
    Expression<String> authorType =
        cb.<String>selectCase()
            .when(cb.isNotNull(cAuthorUserJoin.get("id")), cb.literal("user"))
            .when(cb.isNotNull(cAuthorTeamJoin.get("id")), cb.literal("team"))
            .when(cb.isNotNull(cAuthorOrgJoin.get("id")), cb.literal("organization"))
            .when(cb.isNotNull(authorUserJoin.get("id")), cb.literal("user"))
            .when(cb.isNotNull(authorTeamJoin.get("id")), cb.literal("team"))
            .when(cb.isNotNull(authorOrgJoin.get("id")), cb.literal("organization"))
            .otherwise(cb.nullLiteral(String.class));

    cq.multiselect(
        injectorContractRoot.get("compositeId").get("id").alias("injector_contract_id"),
        injectorContractRoot.get("externalId").alias("injector_contract_external_id"),
        injectorContractRoot.get("labels").alias("injector_contract_labels"),
        injectorContractRoot.get("platforms").alias("injector_contract_platforms"),
        ctx.payloadJoin().get("type").alias("payload_type"),
        ctx.payloadCollectorTypeJoin().get("name").alias("collector_type"),
        cb.least(ctx.injectorJoin().<String>get("type")).alias("injector_contract_injector_type"),
        ctx.tagsIdsExpression().alias("injector_contract_tags"),
        ctx.injectorContractDomainsIdsExpression().alias("injector_contract_domains"),
        ctx.payloadJoin().get("status").alias("payload_status"),
        ctx.payloadJoin().get("id").alias("payload_id"),
        ctx.attackPatternIdsExpression().alias("injector_contract_attack_patterns"),
        injectorContractRoot.get("updatedAt").alias("injector_contract_updated_at"),
        authorId.alias("injector_contract_payload_author"),
        authorName.alias("injector_contract_payload_author_name"),
        authorType.alias("injector_contract_payload_author_type"));

    List<Expression<?>> groupBy = new ArrayList<>(getCommonGroupBy(injectorContractRoot, ctx));
    groupBy.add(cAuthorUserJoin.get("id"));
    groupBy.add(cAuthorUserJoin.get("email"));
    groupBy.add(cAuthorUserJoin.get("firstname"));
    groupBy.add(cAuthorUserJoin.get("lastname"));
    groupBy.add(cAuthorTeamJoin.get("id"));
    groupBy.add(cAuthorTeamJoin.get("name"));
    groupBy.add(cAuthorOrgJoin.get("id"));
    groupBy.add(cAuthorOrgJoin.get("name"));
    groupBy.add(authorUserJoin.get("id"));
    groupBy.add(authorUserJoin.get("email"));
    groupBy.add(authorUserJoin.get("firstname"));
    groupBy.add(authorUserJoin.get("lastname"));
    groupBy.add(authorTeamJoin.get("id"));
    groupBy.add(authorTeamJoin.get("name"));
    groupBy.add(authorOrgJoin.get("id"));
    groupBy.add(authorOrgJoin.get("name"));
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
        tuple.get("injector_contract_attack_patterns", String[].class),
        tuple.get("injector_contract_payload_author", String.class),
        tuple.get("injector_contract_payload_author_name", String.class),
        tuple.get("injector_contract_payload_author_type", String.class));
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
    injectorContract.setTenant(new Tenant(injector.getTenantId()));
    injectorContract.setContent(in.getContent());
    injectorContract.setAtomicTesting(in.isAtomicTesting());
    injectorContract.setPlatforms(in.getPlatforms());
    if (!in.getAttackPatternsExternalIds().isEmpty()) {
      List<AttackPattern> attackPatterns =
          fromIterable(
              attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(
                  in.getAttackPatternsExternalIds(), injector.getTenantId()));
      injectorContract.setAttackPatterns(attackPatterns);
    } else {
      injectorContract.setAttackPatterns(new ArrayList<>());
    }
    if (!injector.isPayloads() && in.getDomains() != null) {
      injectorContract.setDomains(
          this.domainService.upserts(in.getDomains(), injector.getTenantId()));
    }
    return injectorContract;
  }

  /**
   * Shared base specification for the facet-count aggregations: current filters + text search +
   * access control, exactly like the search route.
   */
  private Specification<InjectorContract> facetBaseSpec(SearchPaginationInput input) {
    Specification<InjectorContract> filterSpec = computeFilterGroupJpa(input.getFilterGroup());
    Specification<InjectorContract> searchSpec = computeSearchJpa(input.getTextSearch());
    Specification<InjectorContract> accessSpec =
        InjectorContractSpecification.hasAccessToInjectorContract(userService.currentUser());
    return Specification.<InjectorContract>unrestricted()
        .and(filterSpec)
        .and(searchSpec)
        .and(accessSpec);
  }

  /**
   * Computes the count of injector contracts grouped by domain.
   *
   * @param input the search and filtering criteria
   * @return the list of domain counts derived from effective contract associations
   */
  public List<InjectorContractDomainCountOutput> getDomainCounts(SearchPaginationInput input) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    Specification<InjectorContract> baseSpec = facetBaseSpec(input);

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

  /**
   * Distinct authors (with per-author contract counts) matching the given filters, resolving the
   * polymorphic author contract-first then payload (same coalesce as the Threat Arsenal
   * projection). Authorless contracts are excluded here - the UI exposes them via a dedicated "No
   * author" facet.
   */
  public List<InjectorContractAuthorCountOutput> getAuthorCounts(SearchPaginationInput input) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    Specification<InjectorContract> baseSpec = facetBaseSpec(input);

    CriteriaQuery<InjectorContractAuthorCountOutput> q =
        cb.createQuery(InjectorContractAuthorCountOutput.class);
    Root<InjectorContract> root = q.from(InjectorContract.class);
    Join<InjectorContract, User> cAuthorUserJoin = root.join("authorUser", JoinType.LEFT);
    Join<InjectorContract, Team> cAuthorTeamJoin = root.join("authorTeam", JoinType.LEFT);
    Join<InjectorContract, Organization> cAuthorOrgJoin =
        root.join("authorOrganization", JoinType.LEFT);
    Join<InjectorContract, Payload> payloadJoin = root.join("payload", JoinType.LEFT);
    Join<Payload, User> pAuthorUserJoin = payloadJoin.join("authorUser", JoinType.LEFT);
    Join<Payload, Team> pAuthorTeamJoin = payloadJoin.join("authorTeam", JoinType.LEFT);
    Join<Payload, Organization> pAuthorOrgJoin =
        payloadJoin.join("authorOrganization", JoinType.LEFT);

    Expression<String> authorId =
        cb.<String>coalesce()
            .value(cAuthorUserJoin.get("id"))
            .value(cAuthorTeamJoin.get("id"))
            .value(cAuthorOrgJoin.get("id"))
            .value(pAuthorUserJoin.get("id"))
            .value(pAuthorTeamJoin.get("id"))
            .value(pAuthorOrgJoin.get("id"));
    Expression<String> authorName =
        cb.<String>coalesce()
            .value(userAuthorDisplayName(cb, cAuthorUserJoin))
            .value(cAuthorTeamJoin.get("name"))
            .value(cAuthorOrgJoin.get("name"))
            .value(userAuthorDisplayName(cb, pAuthorUserJoin))
            .value(pAuthorTeamJoin.get("name"))
            .value(pAuthorOrgJoin.get("name"));
    Expression<String> authorType =
        cb.<String>selectCase()
            .when(cb.isNotNull(cAuthorUserJoin.get("id")), cb.literal("user"))
            .when(cb.isNotNull(cAuthorTeamJoin.get("id")), cb.literal("team"))
            .when(cb.isNotNull(cAuthorOrgJoin.get("id")), cb.literal("organization"))
            .when(cb.isNotNull(pAuthorUserJoin.get("id")), cb.literal("user"))
            .when(cb.isNotNull(pAuthorTeamJoin.get("id")), cb.literal("team"))
            .when(cb.isNotNull(pAuthorOrgJoin.get("id")), cb.literal("organization"))
            .otherwise(cb.nullLiteral(String.class));

    Predicate predicate = baseSpec.toPredicate(root, q, cb);
    Predicate hasAuthor = cb.isNotNull(authorId);
    q.where(predicate != null ? cb.and(predicate, hasAuthor) : hasAuthor);
    q.multiselect(authorId, authorName, authorType, cb.countDistinct(root));
    q.groupBy(authorId, authorName, authorType);

    return entityManager.createQuery(q).getResultList();
  }

  /**
   * Number of contracts per platform under the given filters. The platforms column is a Postgres
   * {@code text[]} array, which JPA criteria cannot group by, so a bounded count query runs per
   * known platform value using the same {@code array_position_wrapper} membership test as the
   * filter engine ({@link io.openaev.utils.OperationUtilsJpa}).
   */
  public Map<String, Long> getPlatformCounts(SearchPaginationInput input) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    Specification<InjectorContract> baseSpec = facetBaseSpec(input);

    Map<String, Long> counts = new LinkedHashMap<>();
    for (Endpoint.PLATFORM_TYPE platform : Endpoint.PLATFORM_TYPE.values()) {
      CriteriaQuery<Long> q = cb.createQuery(Long.class);
      Root<InjectorContract> root = q.from(InjectorContract.class);
      Predicate predicate = baseSpec.toPredicate(root, q, cb);
      Predicate containsPlatform =
          cb.isNotNull(
              cb.function(
                  "array_position_wrapper",
                  Integer.class,
                  root.get("platforms"),
                  cb.literal(platform.name())));
      q.where(predicate != null ? cb.and(predicate, containsPlatform) : containsPlatform);
      q.select(cb.countDistinct(root));
      counts.put(platform.name(), entityManager.createQuery(q).getSingleResult());
    }
    return counts;
  }

  /**
   * Number of contracts per payload status under the given filters. Contracts without a payload
   * carry no status and are excluded (like the author facet excludes authorless contracts).
   */
  public Map<String, Long> getStatusCounts(SearchPaginationInput input) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    Specification<InjectorContract> baseSpec = facetBaseSpec(input);

    CriteriaQuery<Tuple> q = cb.createTupleQuery();
    Root<InjectorContract> root = q.from(InjectorContract.class);
    Join<InjectorContract, Payload> payloadJoin = root.join("payload", JoinType.LEFT);
    Path<Payload.PAYLOAD_STATUS> status = payloadJoin.get("status");

    Predicate predicate = baseSpec.toPredicate(root, q, cb);
    Predicate hasStatus = cb.isNotNull(status);
    q.where(predicate != null ? cb.and(predicate, hasStatus) : hasStatus);
    q.multiselect(status, cb.countDistinct(root));
    q.groupBy(status);

    Map<String, Long> counts = new LinkedHashMap<>();
    for (Tuple tuple : entityManager.createQuery(q).getResultList()) {
      counts.put(String.valueOf(tuple.get(0)), (Long) tuple.get(1));
    }
    return counts;
  }

  /**
   * Number of contracts per kill chain phase under the given filters (through the attack pattern
   * relation), so the inject-contract picker sidebar can display live counts on its kill chain
   * facets like the domain and platform ones.
   */
  public Map<String, Long> getKillChainPhaseCounts(SearchPaginationInput input) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    Specification<InjectorContract> baseSpec = facetBaseSpec(input);

    CriteriaQuery<Tuple> q = cb.createTupleQuery();
    Root<InjectorContract> root = q.from(InjectorContract.class);
    Join<InjectorContract, AttackPattern> attackPatternJoin =
        root.join("attackPatterns", JoinType.INNER);
    Join<AttackPattern, KillChainPhase> phaseJoin =
        attackPatternJoin.join("killChainPhases", JoinType.INNER);

    Predicate predicate = baseSpec.toPredicate(root, q, cb);
    if (predicate != null) {
      q.where(predicate);
    }
    q.multiselect(phaseJoin.get("id"), cb.countDistinct(root));
    q.groupBy(phaseJoin.get("id"));

    Map<String, Long> counts = new LinkedHashMap<>();
    for (Tuple tuple : entityManager.createQuery(q).getResultList()) {
      counts.put(String.valueOf(tuple.get(0)), (Long) tuple.get(1));
    }
    return counts;
  }

  @Override
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    try {
      int copied = 0;
      for (String injectorContractId : listDefaultInjectorContract) {
        InjectorContractId compositeId =
            new InjectorContractId(injectorContractId, Tenant.DEFAULT_TENANT_UUID);
        InjectorContract source = entityManager.find(InjectorContract.class, compositeId);
        if (source == null) {
          continue; // contract does not exist for the current tenant — skip
        }
        entityManager.detach(source);
        source.setTenant(tenant);
        // Provision the default contracts only. The injector rows for this tenant do not exist yet
        // (built-in injectors register later, in the ManagerFactory round), so cascading the join
        // rows here would reference a missing injector and violate the composite foreign key
        // injectors_injector_contracts_injector_fk. Injector registration recreates the links
        // FK-safely once its own injector row is in place. Replace the collection with a fresh list
        // (instead of clearing it) so orphanRemoval does not schedule a delete of the detached
        // default-tenant links loaded with the source.
        source.setInjectorLinks(new ArrayList<>());
        entityManager.persist(source);
        copied++;
      }
      log.info(
          "Provisioned {} default injector contract(s) for tenant {}; their join rows are "
              + "(re)created during builtin injector registration",
          copied,
          tenant.getId());
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
