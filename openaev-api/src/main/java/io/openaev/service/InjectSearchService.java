package io.openaev.service;

import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.utils.JpaUtils.createJoinArrayAggOnId;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawInjectExpectation;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.atomic_testing.form.InjectResultOutput;
import io.openaev.rest.atomic_testing.form.InjectStatusSimple;
import io.openaev.rest.atomic_testing.form.InjectorContractSimple;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.rest.inject.output.InjectOutput;
import io.openaev.rest.payload.output.PayloadSimple;
import io.openaev.utils.InjectExpectationResultUtils;
import io.openaev.utils.TargetType;
import io.openaev.utils.mapper.InjectExpectationMapper;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class InjectSearchService {

  private final InjectExpectationRepository injectExpectationRepository;
  private final TeamRepository teamRepository;
  private final AssetRepository assetRepository;
  private final AssetGroupRepository assetGroupRepository;

  private final InjectMapper injectMapper;
  private final InjectExpectationMapper injectExpectationMapper;

  @PersistenceContext private EntityManager entityManager;

  // -- LIST INJECTOUTPUT --

  public List<InjectOutput> injects(Specification<Inject> specification) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<Inject> injectRoot = cq.from(Inject.class);
    selectForInject(cb, cq, injectRoot, new HashMap<>());

    // -- Text Search and Filters --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(injectRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    cq.orderBy(cb.asc(injectRoot.get("dependsDuration")));

    // Type Query
    TypedQuery<Tuple> query = this.entityManager.createQuery(cq);

    // -- EXECUTION --
    return execInject(query);
  }

  // -- PAGE INJECT OUTPUT --
  public Page<InjectOutput> injects(
      Specification<Inject> specification,
      Specification<Inject> specificationCount,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<Inject> injectRoot = cq.from(Inject.class);
    selectForInject(cb, cq, injectRoot, joinMap);

    // -- Text Search and Filters --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(injectRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    List<Order> orders = toSortCriteriaBuilder(cb, injectRoot, pageable.getSort());
    cq.orderBy(orders);

    // Type Query
    TypedQuery<Tuple> query = this.entityManager.createQuery(cq);

    // -- Pagination --
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- EXECUTION --
    List<InjectOutput> injects = execInject(query);

    // -- Count Query --
    Long total = countQuery(cb, this.entityManager, Inject.class, specificationCount);

    return new PageImpl<>(injects, pageable, total);
  }

  private void selectForInject(
      CriteriaBuilder cb,
      CriteriaQuery<Tuple> cq,
      Root<Inject> injectRoot,
      Map<String, Join<Base, Base>> joinMap) {
    // Joins
    Join<Base, Base> injectExerciseJoin = injectRoot.join("exercise", JoinType.LEFT);
    joinMap.put("exercise", injectExerciseJoin);

    Join<Base, Base> injectScenarioJoin = injectRoot.join("scenario", JoinType.LEFT);
    joinMap.put("scenario", injectScenarioJoin);

    Join<Base, Base> injectorContractJoin = injectRoot.join("injectorContract", JoinType.LEFT);
    joinMap.put("injectorContract", injectorContractJoin);

    Join<Base, Base> injectorJoin = injectorContractJoin.join("injector", JoinType.LEFT);
    joinMap.put("injector", injectorJoin);

    Join<Base, Base> injectDependency = injectRoot.join("dependsOn", JoinType.LEFT);
    joinMap.put("dependsOn", injectDependency);

    // Array aggregations
    Expression<String[]> tagIdsExpression = createJoinArrayAggOnId(cb, injectRoot, "tags");
    Expression<String[]> teamIdsExpression = createJoinArrayAggOnId(cb, injectRoot, "teams");
    Expression<String[]> assetIdsExpression = createJoinArrayAggOnId(cb, injectRoot, "assets");
    Expression<String[]> assetGroupIdsExpression =
        createJoinArrayAggOnId(cb, injectRoot, "assetGroups");

    // SELECT
    cq.multiselect(
            injectRoot.get("id").alias("inject_id"),
            injectRoot.get("title").alias("inject_title"),
            injectRoot.get("enabled").alias("inject_enabled"),
            injectRoot.get("content").alias("inject_content"),
            injectRoot.get("allTeams").alias("inject_all_teams"),
            injectExerciseJoin.get("id").alias("inject_exercise"),
            injectScenarioJoin.get("id").alias("inject_scenario"),
            injectRoot.get("dependsDuration").alias("inject_depends_duration"),
            injectorContractJoin.alias("inject_injector_contract"),
            tagIdsExpression.alias("inject_tags"),
            teamIdsExpression.alias("inject_teams"),
            assetIdsExpression.alias("inject_assets"),
            assetGroupIdsExpression.alias("inject_asset_groups"),
            injectorJoin.get("type").alias("inject_type"),
            injectDependency.alias("inject_depends_on"))
        .distinct(true);

    // GROUP BY
    cq.groupBy(
        Arrays.asList(
            injectRoot.get("id"),
            injectExerciseJoin.get("id"),
            injectScenarioJoin.get("id"),
            injectorContractJoin.get("id"),
            injectorJoin.get("id"),
            injectDependency.get("id")));
  }

  private List<InjectOutput> execInject(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple ->
                new InjectOutput(
                    tuple.get("inject_id", String.class),
                    tuple.get("inject_title", String.class),
                    tuple.get("inject_enabled", Boolean.class),
                    tuple.get("inject_content", ObjectNode.class),
                    tuple.get("inject_all_teams", Boolean.class),
                    tuple.get("inject_exercise", String.class),
                    tuple.get("inject_scenario", String.class),
                    tuple.get("inject_depends_duration", Long.class),
                    tuple.get("inject_injector_contract", InjectorContract.class),
                    tuple.get("inject_tags", String[].class),
                    tuple.get("inject_teams", String[].class),
                    tuple.get("inject_assets", String[].class),
                    tuple.get("inject_asset_groups", String[].class),
                    tuple.get("inject_type", String.class),
                    tuple.get("inject_depends_on", InjectDependency.class)))
        .toList();
  }

  // -- PAGE INJECT SEARCH --
  public Page<InjectResultOutput> getPageOfInjectResults(
      String exerciseId, @Valid SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();

    Specification<Inject> customSpec =
        Specification.where(
            (root, query, cb) -> {
              Predicate predicate = cb.conjunction();
              predicate = cb.and(predicate, cb.equal(root.get("exercise").get("id"), exerciseId));
              return predicate;
            });

    return buildPaginationCriteriaBuilder(
        (Specification<Inject> specification,
            Specification<Inject> specificationCount,
            Pageable pageable) ->
            injectResults(
                customSpec.and(specification),
                customSpec.and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        Inject.class,
        joinMap);
  }

  public Page<InjectResultOutput> injectResults(
      Specification<Inject> specification,
      Specification<Inject> specificationCount,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {

    // Prepare query and execute
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    List<InjectResultOutput> injects = executeInjectQuery(cb, specification, pageable, joinMap);

    // Fetch related data for injects
    setComputedAttribute(injects);

    long totalCount = countQuery(cb, entityManager, Inject.class, specificationCount);
    return new PageImpl<>(injects, pageable, totalCount);
  }

  // -- LIST INJECTRESUTLOUTPUT --
  public List<InjectResultOutput> getListOfInjectResults(String exerciseId) {
    // Create specification for filtering by exerciseId
    Specification<Inject> specification =
        Specification.where(
            (root, query, cb) -> cb.equal(root.get("exercise").get("id"), exerciseId));

    // Prepare query and execute
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    Sort sort = Sort.by(Sort.Order.desc("updatedAt"));
    Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE, sort);
    List<InjectResultOutput> injects =
        executeInjectQuery(cb, specification, pageable, new HashMap<>());

    setComputedAttribute(injects);
    return injects;
  }

  // -- UTILS --
  private List<InjectResultOutput> executeInjectQuery(
      CriteriaBuilder cb,
      Specification<Inject> specification,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<Inject> injectRoot = cq.from(Inject.class);

    // Select the injects with possible joins
    selectForInjects(cb, cq, injectRoot, joinMap);

    // Apply filters if any
    if (specification != null) {
      Predicate predicate = specification.toPredicate(injectRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // Apply sorting based on Pageable
    List<Order> orders = toSortCriteriaBuilder(cb, injectRoot, pageable.getSort());
    cq.orderBy(orders);

    // Execute the query with pagination
    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    return execInjects(query);
  }

  private void setComputedAttribute(List<InjectResultOutput> injects) {
    // Fetch related data for injects
    Set<String> injectIds =
        injects.stream()
            .map(InjectResultOutput::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    if (!injectIds.isEmpty()) {
      Map<String, List<Object[]>> teamMap = fetchRelatedTargets(injectIds, "teams");
      Map<String, List<Object[]>> assetMap = fetchRelatedTargets(injectIds, "assets");
      Map<String, List<Object[]>> assetGroupMap = fetchRelatedTargets(injectIds, "assetGroups");
      Map<String, List<RawInjectExpectation>> expectationMap = fetchExpectations(injectIds);

      // Map results to InjectResultOutput and set targets
      mapResultsToInjects(injects, teamMap, assetMap, assetGroupMap, expectationMap);
    }
  }

  public Map<String, List<Object[]>> fetchRelatedTargets(Set<String> injectIds, String targetType) {
    if (injectIds == null || injectIds.isEmpty()) {
      return new HashMap<>();
    }

    Optional<List<Object[]>> data;
    switch (targetType) {
      case "teams":
        data = ofNullable(teamRepository.teamsByInjectIds(injectIds));
        break;
      case "assets":
        data = ofNullable(assetRepository.assetsByInjectIds(injectIds));
        break;
      case "assetGroups":
        data = ofNullable(assetGroupRepository.assetGroupsByInjectIds(injectIds));
        break;
      default:
        throw new IllegalArgumentException("Unknown data type: " + targetType);
    }
    if (data.isEmpty()) {
      return new HashMap<>();
    }

    return data.orElse(emptyList()).stream()
        .filter(Objects::nonNull)
        .filter(row -> 0 < row.length && row[0] != null) // [0]: id
        .collect(Collectors.groupingBy(row -> (String) row[0]));
  }

  private Map<String, List<RawInjectExpectation>> fetchExpectations(Set<String> injectIds) {
    if (injectIds == null || injectIds.isEmpty()) {
      return new HashMap<>();
    }

    return ofNullable(injectExpectationRepository.rawForComputeGlobalByInjectIds(injectIds))
        .orElse(emptyList())
        .stream()
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(RawInjectExpectation::getInject_id));
  }

  private void mapResultsToInjects(
      List<InjectResultOutput> injects,
      Map<String, List<Object[]>> teamMap,
      Map<String, List<Object[]>> assetMap,
      Map<String, List<Object[]>> assetGroupMap,
      Map<String, List<RawInjectExpectation>> expectationMap) {

    for (InjectResultOutput inject : injects) {
      if (inject.getId() != null) {
        // Set global score (expectations)
        inject.setExpectationResultByTypes(
            injectExpectationMapper.extractExpectationResults(
                inject.getContent(),
                expectationMap.getOrDefault(inject.getId(), emptyList()),
                InjectExpectationResultUtils::getScoresFromRaw));

        // Set targets (teams, assets, asset groups)
        List<TargetSimple> allTargets =
            Stream.concat(
                    injectMapper
                        .toTargetSimple(
                            teamMap.getOrDefault(inject.getId(), emptyList()), TargetType.TEAMS)
                        .stream(),
                    Stream.concat(
                        injectMapper
                            .toTargetSimple(
                                assetMap.getOrDefault(inject.getId(), emptyList()),
                                TargetType.ASSETS)
                            .stream(),
                        injectMapper
                            .toTargetSimple(
                                assetGroupMap.getOrDefault(inject.getId(), emptyList()),
                                TargetType.ASSETS_GROUPS)
                            .stream()))
                .toList();

        inject.getTargets().addAll(allTargets);
      }
    }
  }

  private void selectForInjects(
      CriteriaBuilder cb,
      CriteriaQuery<Tuple> cq,
      Root<Inject> injectRoot,
      Map<String, Join<Base, Base>> joinMap) {
    // Joins
    Join<Base, Base> injectorContractJoin = injectRoot.join("injectorContract", JoinType.LEFT);
    joinMap.put("injectorContract", injectorContractJoin);

    Join<Base, Base> injectorJoin = injectorContractJoin.join("injector", JoinType.LEFT);
    joinMap.put("injector", injectorJoin);

    Join<Base, Base> payloadJoin = injectorContractJoin.join("payload", JoinType.LEFT);
    joinMap.put("payload", payloadJoin);

    Join<Base, Base> collectorJoin = payloadJoin.join("collector", JoinType.LEFT);
    joinMap.put("collector", collectorJoin);

    Join<Base, Base> statusJoin = injectRoot.join("status", JoinType.LEFT);
    joinMap.put("status", statusJoin);

    // Array aggregations
    Expression<String[]> teamIdsExpression = createJoinArrayAggOnId(cb, injectRoot, "teams");
    Expression<String[]> assetIdsExpression = createJoinArrayAggOnId(cb, injectRoot, "assets");
    Expression<String[]> assetGroupIdsExpression =
        createJoinArrayAggOnId(cb, injectRoot, "assetGroups");

    // SELECT
    cq.multiselect(
            injectRoot.get("id").alias("inject_id"),
            injectRoot.get("title").alias("inject_title"),
            injectRoot.get("updatedAt").alias("inject_updated_at"),
            injectRoot.get("content").alias("inject_content"),
            injectorJoin.get("type").alias("inject_type"),
            injectorContractJoin.get("id").alias("injector_contract_id"),
            injectorContractJoin.get("content").alias("injector_contract_content"),
            injectorContractJoin.get("convertedContent").alias("convertedContent"),
            injectorContractJoin.get("platforms").alias("injector_contract_platforms"),
            injectorContractJoin.get("labels").alias("injector_contract_labels"),
            payloadJoin.get("id").alias("payload_id"),
            payloadJoin.get("type").alias("payload_type"),
            collectorJoin.get("type").alias("payload_collector_type"),
            statusJoin.get("id").alias("status_id"),
            statusJoin.get("name").alias("status_name"),
            statusJoin.get("trackingSentDate").alias("status_tracking_sent_date"),
            teamIdsExpression.alias("inject_teams"),
            assetIdsExpression.alias("inject_assets"),
            assetGroupIdsExpression.alias("inject_asset_groups"))
        .distinct(true);

    // GROUP BY
    cq.groupBy(
        Arrays.asList(
            injectRoot.get("id"),
            injectorContractJoin.get("id"),
            injectorJoin.get("id"),
            payloadJoin.get("id"),
            collectorJoin.get("id"),
            statusJoin.get("id")));
  }

  private List<InjectResultOutput> execInjects(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple -> {
              InjectStatusSimple injectStatus = null;
              ExecutionStatus status = tuple.get("status_name", ExecutionStatus.class);
              if (status != null) {
                injectStatus =
                    InjectStatusSimple.builder()
                        .id(tuple.get("status_id", String.class))
                        .name(status.name())
                        .trackingSentDate(tuple.get("status_tracking_sent_date", Instant.class))
                        .build();
              } else {
                injectStatus = InjectStatusSimple.builder().build();
              }

              PayloadSimple payloadSimple = null;
              String payloadId = tuple.get("payload_id", String.class);
              if (payloadId != null) {
                payloadSimple =
                    PayloadSimple.builder()
                        .id(payloadId)
                        .type(tuple.get("payload_type", String.class))
                        .collectorType(tuple.get("payload_collector_type", String.class))
                        .build();
              }

              InjectorContractSimple injectorContractSimple = null;
              String injectorContractId = tuple.get("injector_contract_id", String.class);
              if (injectorContractId != null) {
                injectorContractSimple =
                    InjectorContractSimple.builder()
                        .id(injectorContractId)
                        .content(tuple.get("injector_contract_content", String.class))
                        .convertedContent(tuple.get("convertedContent", ObjectNode.class))
                        .platforms(
                            tuple.get(
                                "injector_contract_platforms", Endpoint.PLATFORM_TYPE[].class))
                        .payload(payloadSimple)
                        .labels(tuple.get("injector_contract_labels", Map.class))
                        .build();
              }

              InjectResultOutput injectResultOutput = new InjectResultOutput();
              injectResultOutput.setId(tuple.get("inject_id", String.class));
              injectResultOutput.setTitle(tuple.get("inject_title", String.class));
              injectResultOutput.setContent(tuple.get("inject_content", ObjectNode.class));
              injectResultOutput.setUpdatedAt(tuple.get("inject_updated_at", Instant.class));
              injectResultOutput.setInjectType(tuple.get("inject_type", String.class));
              injectResultOutput.setInjectorContract(injectorContractSimple);
              injectResultOutput.setStatus(injectStatus);
              injectResultOutput.setTeamIds(tuple.get("inject_teams", String[].class));
              injectResultOutput.setAssetIds(tuple.get("inject_assets", String[].class));
              injectResultOutput.setAssetGroupIds(tuple.get("inject_asset_groups", String[].class));

              return injectResultOutput;
            })
        .toList();
  }
}
