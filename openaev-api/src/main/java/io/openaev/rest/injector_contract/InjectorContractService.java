package io.openaev.rest.injector_contract;

import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.database.model.InjectorContract.*;
import static io.openaev.helper.DatabaseHelper.updateRelation;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.JpaUtils.*;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawInjectorsContrats;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.specification.InjectorContractSpecification;
import io.openaev.injectors.email.EmailContract;
import io.openaev.injectors.ovh.OvhSmsContract;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.form.InjectorContractAddInput;
import io.openaev.rest.injector_contract.form.InjectorContractInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateMappingInput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractFullOutput;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.service.UserService;
import io.openaev.utils.TargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class InjectorContractService {

  @PersistenceContext private EntityManager entityManager;

  private final InjectorContractRepository injectorContractRepository;
  private final AttackPatternService attackPatternService;
  private final VulnerabilityService vulnerabilityService;
  private final InjectorRepository injectorRepository;
  private final UserService userService;
  private final AttackPatternRepository attackPatternRepository;

  @Value("${openaev.xls.import.mail.enable}")
  private boolean mailImportEnabled;

  @Value("${openaev.xls.import.sms.enable}")
  private boolean smsImportEnabled;

  // -- CRUD --

  public InjectorContract injectorContract(@NotBlank final String id) {
    return injectorContractRepository
        .findByIdOrExternalId(id, id)
        .orElseThrow(() -> new ElementNotFoundException("Injector contract not found"));
  }

  // -- OTHERS --

  @EventListener(ApplicationReadyEvent.class)
  public void initImportAvailableOnStartup() {
    List<String> listOfInjectorImportAvailable = new ArrayList<>();
    if (mailImportEnabled) {
      listOfInjectorImportAvailable.addAll(
          Arrays.asList(EmailContract.EMAIL_GLOBAL, EmailContract.EMAIL_DEFAULT));
    }
    if (smsImportEnabled) {
      listOfInjectorImportAvailable.add(OvhSmsContract.OVH_DEFAULT);
    }

    List<InjectorContract> listInjectorContract = new ArrayList<>();
    injectorContractRepository.findAll().spliterator().forEachRemaining(listInjectorContract::add);
    listInjectorContract.forEach(
        injectorContract -> {
          injectorContract.setImportAvailable(
              listOfInjectorImportAvailable.contains(injectorContract.getId()));
        });
    injectorContractRepository.saveAll(listInjectorContract);
  }

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

  public Iterable<RawInjectorsContrats> getAllRawInjectContracts() {
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_PAYLOADS)) {
      return injectorContractRepository.getAllRawInjectorsContracts();
    }
    return injectorContractRepository.getAllRawInjectorsContractsWithoutPayloadOrGranted(
        currentUser.getId());
  }

  public InjectorContract getSingleInjectorContract(String injectorContractId) {
    return injectorContractRepository
        .findByIdOrExternalId(injectorContractId, injectorContractId)
        .orElseThrow(ElementNotFoundException::new);
  }

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
    return injectorContractRepository.save(injectorContract);
  }

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
    injectorContract.setUpdatedAt(Instant.now());
    return injectorContractRepository.save(injectorContract);
  }

  private void setVulnerabilitiesFromExternalOrInternalIds(
      List<String> externalIds, List<String> internalIds, InjectorContract injectorContract) {
    Set<Vulnerability> vulns = new HashSet<>();
    if (!externalIds.isEmpty()) {
      vulns = vulnerabilityService.findAllByExternalIdsOrThrowIfMissing(new HashSet<>(externalIds));
    } else if (!internalIds.isEmpty()) {
      vulns = vulnerabilityService.findAllByIdsOrThrowIfMissing(new HashSet<>(internalIds));
    }
    injectorContract.setVulnerabilities(vulns);
  }

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
    injectorContract.setUpdatedAt(Instant.now());
    return injectorContractRepository.save(injectorContract);
  }

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
    return injectorContract;
  }
}
