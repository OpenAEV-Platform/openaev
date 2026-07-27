package io.openaev.service.scenario;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.database.specification.ScenarioSpecification.findGrantedFor;
import static io.openaev.database.specification.TeamSpecification.fromIds;
import static io.openaev.helper.MailHelper.resolveFromName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.scenario.utils.ScenarioUtils.handleCustomFilter;
import static io.openaev.service.ImportService.EXPORT_ENTRY_ATTACHMENT;
import static io.openaev.service.ImportService.EXPORT_ENTRY_SCENARIO;
import static io.openaev.utils.StringUtils.duplicateString;
import static io.openaev.utils.constants.Constants.ARTICLES;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawExerciseSimple;
import io.openaev.database.raw.RawPaginationScenario;
import io.openaev.database.raw.RawScenario;
import io.openaev.database.raw.RawScenarioSimpleIndexing;
import io.openaev.database.repository.*;
import io.openaev.database.specification.ScenarioSpecification;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.export.Mixins;
import io.openaev.export.WorkflowExportInitializer;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.helper.ObjectMapperHelper;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.exports.ExerciseFileExport;
import io.openaev.rest.exercise.exports.VariableMixin;
import io.openaev.rest.exercise.exports.VariableWithValueMixin;
import io.openaev.rest.exercise.form.ExerciseSimple;
import io.openaev.rest.inject.service.InjectDuplicateService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.kill_chain_phase.response.KillChainPhaseOutput;
import io.openaev.rest.scenario.export.ScenarioFileExport;
import io.openaev.rest.scenario.form.ScenarioBulkProcessingInput;
import io.openaev.rest.scenario.form.ScenarioInput;
import io.openaev.rest.scenario.form.ScenarioSimple;
import io.openaev.rest.scenario.response.ScenarioOutput;
import io.openaev.rest.scenario.response.ScenarioTeamUserOutput;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.service.*;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.service.utils.BulkDeleteExecutor;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.TargetType;
import io.openaev.utils.mapper.ExerciseMapper;
import io.openaev.utils.mapper.ScenarioMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.TriFunction;
import org.hibernate.Hibernate;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor
@Service
@Slf4j
@Validated
public class ScenarioService {

  @Value("${openaev.mail.imap.enabled}")
  private boolean imapEnabled;

  @Value("${openaev.mail.imap.username}")
  private String imapUsername;

  @Resource private OpenAEVConfig openAEVConfig;

  @PersistenceContext private EntityManager entityManager;

  private final ScenarioRepository scenarioRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final DocumentRepository documentRepository;
  private final ScenarioTeamUserRepository scenarioTeamUserRepository;
  private final ArticleRepository articleRepository;

  private final ExerciseMapper exerciseMapper;
  private final ActionMetricCollector actionMetricCollector;
  private final LicenseCacheManager licenseCacheManager;

  private final EnterpriseEditionService enterpriseEditionService;
  private final VariableService variableService;
  private final ChallengeService challengeService;
  private final TeamService teamService;
  private final FileService fileService;
  private final InjectDuplicateService injectDuplicateService;
  private final TagRuleService tagRuleService;
  private final InjectService injectService;
  private final UserService userService;
  private final TenantSettingsService tenantSettingsService;
  private final CustomDashboardService customDashboardService;
  private final InjectorContractService injectorContractService;

  private final InjectRepository injectRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;
  private final TagRepository tagRepository;

  private final HealthCheckUtils healthCheckUtils;

  private final ScenarioMapper scenarioMapper;
  private final WorkflowService workflowService;
  private final WorkflowExportInitializer workflowExportInitializer;
  private final BulkDeleteExecutor bulkDeleteExecutor;

  @Transactional
  public Scenario createScenario(@NotNull final Scenario scenario) {
    return computeAndCreateScenario(scenario);
  }

  @Transactional
  public Scenario createScenarioChaining(@NotNull final Scenario scenario)
      throws ChainingException {
    workflowService.isPreviewFeatureChainingEnable();

    computeEmails(scenario);
    this.actionMetricCollector.addScenarioCreatedCount();
    Scenario savedScenario = this.scenarioRepository.save(scenario);
    workflowService.creationWorkflow(savedScenario);

    return savedScenario;
  }

  @Transactional
  public Scenario createScenarioWithInjectorContracts(
      @NotBlank final String tenantId,
      @NotNull final ScenarioInput scenarioInput,
      @NotNull final InjectorContractSearchPaginationInput injectorContractSearchPaginationInput,
      @NotBlank final String locale) {
    Scenario preparedScenario = prepareScenarioFromScenarioInput(tenantId, scenarioInput);
    Scenario scenario = computeAndCreateScenario(preparedScenario);
    this.injectService.createInjectsFromInjectorContractInput(
        null, new ArrayList<>(List.of(scenario)), injectorContractSearchPaginationInput, locale);
    return scenario;
  }

  @Transactional
  public List<Scenario> updateScenariosWithInjectorContracts(
      @NotNull final List<String> scenarioIds,
      @NotNull final InjectorContractSearchPaginationInput injectorContractSearchPaginationInput,
      @NotBlank final String locale) {
    List<Scenario> scenarios = this.scenarioRepository.findAllById(scenarioIds);
    this.injectService.createInjectsFromInjectorContractInput(
        null, scenarios, injectorContractSearchPaginationInput, locale);
    return scenarios;
  }

  public void computeEmails(@NotNull Scenario scenario) {
    if (!hasText(scenario.getFrom())) {
      if (this.imapEnabled) {
        scenario.setFrom(this.imapUsername);
        scenario.setFromName(resolveFromName(null, this.imapUsername));
        scenario.setReplyTos(new ArrayList<>(Arrays.asList(this.imapUsername)));
      } else {
        scenario.setFrom(this.openAEVConfig.getDefaultMailer());
        scenario.setFromName(this.openAEVConfig.getDefaultMailerName());
        scenario.setReplyTos(
            new ArrayList<>(Arrays.asList(this.openAEVConfig.getDefaultReplyTo())));
      }
    }
  }

  public List<ScenarioSimple> scenarios() {
    List<RawScenarioSimpleIndexing> scenarios;
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)) {
      scenarios = fromIterable(this.scenarioRepository.rawAll());
    } else {
      scenarios = this.scenarioRepository.rawAllGranted(currentUser().getId());
    }
    return scenarios.stream().map(ScenarioSimple::fromRawScenario).toList();
  }

  public List<ScenarioSimple> scenarios(final List<String> scenarioIds) {
    List<RawScenarioSimpleIndexing> scenarios;
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)) {
      scenarios = fromIterable(this.scenarioRepository.rawByScenarioIds(scenarioIds));
    } else {
      scenarios =
          this.scenarioRepository.rawGrantedByScenarioIds(currentUser().getId(), scenarioIds);
    }
    return scenarios.stream().map(ScenarioSimple::fromRawScenario).toList();
  }

  public Page<RawPaginationScenario> scenarios(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();

    // Compute custom filter
    UnaryOperator<Specification<Scenario>> deepFilterSpecification =
        handleCustomFilter(searchPaginationInput);

    // Compute find all method
    TriFunction<
            Specification<Scenario>, Specification<Scenario>, Pageable, Page<RawPaginationScenario>>
        findAll = getFindAllFunction(deepFilterSpecification, joinMap);

    // Compute pagination from find all
    return buildPaginationCriteriaBuilder(findAll, searchPaginationInput, Scenario.class, joinMap);
  }

  private TriFunction<
          Specification<Scenario>, Specification<Scenario>, Pageable, Page<RawPaginationScenario>>
      getFindAllFunction(
          UnaryOperator<Specification<Scenario>> deepFilterSpecification,
          Map<String, Join<Base, Base>> joinMap) {
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)) {
      return (specification, specificationCount, pageable) ->
          this.findAllWithCriteriaBuilder(
              deepFilterSpecification.apply(specification),
              deepFilterSpecification.apply(specificationCount),
              pageable,
              joinMap);
    } else {
      return (specification, specificationCount, pageable) ->
          this.findAllWithCriteriaBuilder(
              findGrantedFor(currentUser().getId())
                  .and(deepFilterSpecification.apply(specification)),
              findGrantedFor(currentUser().getId())
                  .and(deepFilterSpecification.apply(specificationCount)),
              pageable,
              joinMap);
    }
  }

  private Page<RawPaginationScenario> findAllWithCriteriaBuilder(
      Specification<Scenario> specification,
      Specification<Scenario> specificationCount,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // -- Create Query --
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    // FROM
    Root<Scenario> scenarioRoot = cq.from(Scenario.class);
    // Join on TAG
    Join<Base, Base> scenarioTagsJoin = scenarioRoot.join("tags", JoinType.LEFT);
    joinMap.put("tags", scenarioTagsJoin);
    Expression<String> nullString = cb.nullLiteral(String.class);
    Expression<String[]> arr =
        ((HibernateCriteriaBuilder) cb).arrayAgg(null, scenarioTagsJoin.get("id"));
    Expression<String[]> tagIdsExpression =
        ((HibernateCriteriaBuilder) cb).arrayRemove(arr, nullString);

    // Join on INJECT and INJECTOR CONTRACT
    Join<Base, Base> injectsJoin = scenarioRoot.join("injects", JoinType.LEFT);
    joinMap.put("injects", injectsJoin);
    Join<Base, Base> injectorsContractsJoin = injectsJoin.join("injectorContract", JoinType.LEFT);
    joinMap.put("injects.injectorContract", injectorsContractsJoin);
    Expression<String[]> platformExpression =
        cb.function("array_union_agg", String[].class, injectorsContractsJoin.get("platforms"));

    // Subquery for workflow_id
    Subquery<String> workflowSubquery = cq.subquery(String.class);
    Root<Workflow> workflowRoot = workflowSubquery.from(Workflow.class);
    workflowSubquery
        .select(workflowRoot.get("id"))
        .where(
            cb.equal(workflowRoot.get("scenario").get("id"), scenarioRoot.get("id")),
            cb.equal(workflowRoot.get("status"), WorkflowStatus.TEMPLATE));

    // SELECT
    cq.multiselect(
            scenarioRoot.get("id").alias("scenario_id"),
            scenarioRoot.get("name").alias("scenario_name"),
            scenarioRoot.get("description").alias("scenario_description"),
            scenarioRoot.get("severity").alias("scenario_severity"),
            scenarioRoot.get("category").alias("scenario_category"),
            scenarioRoot.get("recurrence").alias("scenario_recurrence"),
            scenarioRoot.get("updatedAt").alias("scenario_updated_at"),
            tagIdsExpression.alias("scenario_tags"),
            platformExpression.alias("scenario_platforms"),
            workflowSubquery.alias("scenario_workflow_id"))
        .distinct(true);
    // Group By
    cq.groupBy(scenarioRoot.get("id"));

    // -- Text Search and Filters --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(scenarioRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    List<Order> orders = toSortCriteriaBuilder(cb, scenarioRoot, pageable.getSort());
    cq.orderBy(orders);

    // Type Query
    TypedQuery<Tuple> query = entityManager.createQuery(cq);

    // -- Pagination --
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- EXECUTION --
    List<RawPaginationScenario> scenarios =
        query.getResultList().stream()
            .map(
                tuple ->
                    new RawPaginationScenario(
                        tuple.get("scenario_id", String.class),
                        tuple.get("scenario_name", String.class),
                        tuple.get("scenario_description", String.class),
                        tuple.get("scenario_severity", Scenario.SEVERITY.class),
                        tuple.get("scenario_category", String.class),
                        tuple.get("scenario_recurrence", String.class),
                        tuple.get("scenario_updated_at", Instant.class),
                        tuple.get("scenario_tags", String[].class),
                        tuple.get("scenario_platforms", String[].class),
                        tuple.get("scenario_workflow_id", String.class)))
            .toList();

    // -- Count Query --
    Long total = countQuery(cb, this.entityManager, Scenario.class, specificationCount);

    return new PageImpl<>(scenarios, pageable, total);
  }

  public void throwIfScenarioNotLaunchable(Scenario scenario) {
    if (enterpriseEditionService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo())) {
      return;
    }
    scenario.getInjects().forEach(injectService::throwIfInjectNotLaunchable);
  }

  /** Scenario is recurring AND end date is after now */
  public List<Scenario> recurringScenarios(@NotNull final Instant instant) {
    return this.scenarioRepository.findAll(
        ScenarioSpecification.isRecurring()
            .and(ScenarioSpecification.recurrenceStopDateAfter(instant)));
  }

  /** Scenario is recurring AND start date is before now OR stop date is before now */
  public List<Scenario> potentialOutdatedRecurringScenario(@NotNull final Instant instant) {
    return this.scenarioRepository.findAll(
        ScenarioSpecification.isRecurring()
            .and(
                ScenarioSpecification.recurrenceStartDateBefore(instant)
                    .or(ScenarioSpecification.recurrenceStopDateBefore(instant))));
  }

  public Scenario scenario(@NotBlank final String scenarioId) {
    return this.scenarioRepository
        .findByIdAndTenantId(scenarioId, TenantContext.getCurrentTenant())
        .orElseThrow(() -> new ElementNotFoundException("Scenario not found"));
  }

  public ScenarioOutput getScenarioById(@NotBlank final String scenarioId) {
    ObjectMapper objectMapper = new ObjectMapper();
    RawScenario rawScenario = this.scenarioRepository.getScenarioByIdAndTenantId(scenarioId);
    if (rawScenario == null) {
      throw new ElementNotFoundException("Scenario not found");
    }
    Set<KillChainPhaseOutput> killChainPhases = new HashSet<>();
    if (rawScenario.getScenario_kill_chain_phases() != null) {
      try {
        killChainPhases =
            objectMapper.readValue(
                rawScenario.getScenario_kill_chain_phases(), new TypeReference<>() {});
      } catch (JsonProcessingException e) {
        log.error("Error reading killChainPhases from scenario id {}", scenarioId, e);
      }
    }
    Set<ScenarioTeamUserOutput> scenarioTeamUsers = new HashSet<>();
    if (rawScenario.getScenario_teams_users() != null) {
      try {
        scenarioTeamUsers =
            objectMapper.readValue(rawScenario.getScenario_teams_users(), new TypeReference<>() {});
      } catch (JsonProcessingException e) {
        log.error("Error reading scenarioTeamUsers from scenario id {}", scenarioId, e);
      }
    }
    return scenarioMapper.toScenarioOutput(rawScenario, killChainPhases, scenarioTeamUsers);
  }

  public Scenario scenarioFromSimulationId(@NotBlank final String simulationId) {
    return this.scenarioRepository
        .findByExercises_Id(simulationId)
        .orElseThrow(
            () ->
                new ElementNotFoundException("Scenario not found for simulation: " + simulationId));
  }

  @Transactional(readOnly = true)
  public ExerciseSimple latestExerciseByExternalReference(
      @NotBlank final String scenarioExternalReference) {
    Optional<RawExerciseSimple> latestEndedExercise =
        scenarioRepository.rawAllByExternalReference(scenarioExternalReference).stream()
            .filter(rawExercise -> rawExercise.getExercise_end_date() != null)
            .max(Comparator.comparing(RawExerciseSimple::getExercise_end_date));

    return latestEndedExercise
        .map(exerciseMapper::getExerciseSimple)
        .orElseThrow(() -> new ElementNotFoundException("Latest exercise not found"));
  }

  public Scenario updateScenario(@NotNull final Scenario scenario) {
    return this.updateScenario(scenario, null, false);
  }

  /**
   * Update the scenario and each of the injects to add default asset groups
   *
   * @param scenario
   * @param currentTags list of the tags before the update
   * @return
   */
  @Transactional
  public Scenario updateScenario(
      @NotNull final Scenario scenario, Set<Tag> currentTags, boolean applyRule) {
    if (applyRule) {
      // Get asset groups from the TagRule of the added tags
      List<AssetGroup> defaultAssetGroupsToAdd =
          tagRuleService.getAssetGroupsFromTagIds(
              scenario.getTags().stream()
                  .filter(tag -> !currentTags.contains(tag))
                  .map(Tag::getId)
                  .toList());

      // Add the default asset groups to/from the injects
      scenario.getInjects().stream()
          .filter(inject -> this.injectService.canApplyTargetType(inject, TargetType.ASSETS_GROUPS))
          .forEach(
              inject ->
                  injectService.applyDefaultAssetGroupsToInject(
                      inject.getId(), defaultAssetGroupsToAdd));
    }
    scenario.setUpdatedAt(now());
    return this.scenarioRepository.save(scenario);
  }

  public void updateScenarios(@NotNull final List<Scenario> scenarios) {
    scenarios.forEach(scenario -> scenario.setUpdatedAt(now()));
    this.scenarioRepository.saveAll(scenarios);
  }

  /** Validates that the scenario exists for the current tenant. Throws if not found. */
  public void existsByIdAndTenantId(@NotBlank final String scenarioId) {
    if (!this.scenarioRepository.existsByIdAndTenantId(
        scenarioId, TenantContext.getCurrentTenant())) {
      throw new ElementNotFoundException("Scenario not found");
    }
  }

  @Transactional(rollbackFor = Exception.class)
  public void deleteScenario(@NotBlank final String scenarioId) {
    existsByIdAndTenantId(scenarioId);
    this.scenarioRepository.deleteById(scenarioId);
  }

  /**
   * Bulk delete of scenarios, either from an explicit list of ids or from a search input
   * (select-all with optional exclusions). Only scenarios the user is allowed to manage are
   * deleted.
   *
   * <p>Deliberately NOT transactional as a whole: the scope is resolved in a short read
   * transaction, then scenarios are deleted in small independent chunks (with deadlock retry). A
   * single all-encompassing transaction used to hold row locks on {@code exercises} (dirtied by the
   * {@code @PreRemove} scenario-reference nulling) for minutes, deadlocking against concurrent
   * inject expectation updates and tripping Hikari's connection-leak detection.
   *
   * @param input the bulk processing input (ids or search input, plus ids to ignore)
   * @return the list of deleted scenario ids
   */
  public List<String> bulkDeleteScenarios(@NotNull final ScenarioBulkProcessingInput input) {
    if ((CollectionUtils.isEmpty(input.getScenarioIdsToProcess())
            && input.getSearchPaginationInput() == null)
        || (!CollectionUtils.isEmpty(input.getScenarioIdsToProcess())
            && input.getSearchPaginationInput() != null)) {
      throw new BadRequestException(
          "Either scenario_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }
    User currentUser = userService.currentUser();
    List<String> scenarioIdsToDelete =
        bulkDeleteExecutor.resolveInTransaction(
            () -> {
              Specification<Scenario> specification;
              if (input.getSearchPaginationInput() != null) {
                SearchPaginationInput searchPaginationInput = input.getSearchPaginationInput();
                // Same specification chain as the list search: custom filters (recurrence) +
                // filter group + text search, so the deletion scope matches exactly what the user
                // sees in the list.
                UnaryOperator<Specification<Scenario>> customSpecification =
                    handleCustomFilter(searchPaginationInput);
                specification =
                    customSpecification.apply(
                        FilterUtilsJpa.<Scenario>computeFilterGroupJpa(
                                searchPaginationInput.getFilterGroup())
                            .and(computeSearchJpa(searchPaginationInput.getTextSearch())));
              } else {
                specification = SpecificationUtils.hasIdIn(input.getScenarioIdsToProcess());
              }
              if (!CollectionUtils.isEmpty(input.getScenarioIdsToIgnore())) {
                List<String> idsToIgnore = input.getScenarioIdsToIgnore();
                specification =
                    specification.and((root, query, cb) -> cb.not(root.get("id").in(idsToIgnore)));
              }
              // Restrict to scenarios the user is granted to plan on (no-op for admins and users
              // with the delete capability)
              specification =
                  specification.and(
                      SpecificationUtils.hasGrantAccess(
                          currentUser.getId(),
                          currentUser.isAdminOrBypass(),
                          currentUser.getCapabilities().contains(Capability.DELETE_ASSESSMENT),
                          Grant.GRANT_TYPE.PLANNER));
              return this.scenarioRepository.findAll(specification).stream()
                  .map(Scenario::getId)
                  .toList();
            });
    List<String> deletedIds =
        bulkDeleteExecutor.deleteInChunks(
            "scenarios",
            scenarioIdsToDelete,
            chunk -> this.scenarioRepository.deleteAll(this.scenarioRepository.findAllById(chunk)));
    log.info("Bulk deleted {} scenarios by user {}", deletedIds.size(), currentUser.getId());
    return deletedIds;
  }

  // -- EXPORT --

  @Transactional
  public void exportScenario(
      @NotBlank final String scenarioId,
      final boolean isWithTeams,
      final boolean isWithPlayers,
      final boolean isWithVariableValues,
      final boolean isWithScopeDefinition,
      HttpServletResponse response)
      throws IOException {
    ObjectMapper objectMapper = ObjectMapperHelper.openAEVJsonMapper();
    Scenario scenario = this.scenario(scenarioId);
    boolean isChaining = workflowService.isScenarioChaining(scenarioId);
    List<Inject> exportedInjects =
        isChaining ? new ArrayList<>() : new ArrayList<>(scenario.getInjects());

    // Start exporting scenario
    ScenarioFileExport scenarioFileExport = new ScenarioFileExport();
    scenarioFileExport.setVersion(1);
    // Add Scenario
    scenarioFileExport.setScenario(scenario);
    objectMapper.addMixIn(Scenario.class, Mixins.Scenario.class);
    List<Tag> scenarioTags = new ArrayList<>(scenario.getTags());
    // Add Objectives
    scenarioFileExport.setObjectives(scenario.getObjectives());
    objectMapper.addMixIn(Objective.class, Mixins.Objective.class);
    // Add Lesson Categories
    scenarioFileExport.setLessonsCategories(scenario.getLessonsCategories());
    objectMapper.addMixIn(LessonsCategory.class, Mixins.LessonsCategory.class);
    // Add Lessons Questions
    List<LessonsQuestion> lessonsQuestions =
        scenario.getLessonsCategories().stream()
            .flatMap(category -> category.getQuestions().stream())
            .toList();
    scenarioFileExport.setLessonsQuestions(lessonsQuestions);
    objectMapper.addMixIn(LessonsQuestion.class, Mixins.LessonsQuestion.class);
    // Add Variables
    List<Variable> variables = this.variableService.variablesFromScenario(scenarioId);
    scenarioFileExport.setVariables(variables);
    if (isWithVariableValues) {
      objectMapper.addMixIn(Variable.class, VariableWithValueMixin.class);
    } else {
      objectMapper.addMixIn(Variable.class, VariableMixin.class);
    }

    // Add Documents — collect from:
    // 1. documents directly attached to the scenario
    // 2. documents directly attached to injects (InjectDocument)
    // 3. payload's attached document (e.g., FileDrop)
    // 4. documents referenced by Document-type payload arguments in inject content
    List<Document> documentExports =
        Stream.of(
                scenario.getDocuments().stream(),
                exportedInjects.stream()
                    .flatMap(
                        inject -> inject.getDocuments().stream().map(InjectDocument::getDocument)),
                exportedInjects.stream()
                    .flatMap(
                        inject -> {
                          if (inject.getPayload().isEmpty()) {
                            return Stream.of();
                          }
                          Payload pl = inject.getPayload().get();
                          return pl.getAttachedDocument().isPresent()
                              ? Stream.of(pl.getAttachedDocument().get())
                              : Stream.of();
                        }),
                exportedInjects.stream()
                    .flatMap(
                        inject -> {
                          if (inject.getPayload().isEmpty() || inject.getContent() == null) {
                            return Stream.of();
                          }
                          ObjectNode content = inject.getContent();
                          return inject.getPayload().get().getArguments().stream()
                              .filter(arg -> PrimitiveType.Document == arg.getType())
                              .map(arg -> content.path(arg.getKey()))
                              .filter(node -> node.isTextual() && hasText(node.asText()))
                              .map(node -> documentRepository.findById(node.asText()))
                              .flatMap(Optional::stream);
                        }))
            .flatMap(s -> s)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));

    scenarioFileExport.setDocuments(documentExports);
    objectMapper.addMixIn(Document.class, Mixins.Document.class);
    scenarioTags.addAll(documentExports.stream().flatMap(doc -> doc.getTags().stream()).toList());
    List<String> documentIds =
        new ArrayList<>(documentExports.stream().map(Document::getId).toList());

    if (isWithTeams) {
      // Add Teams
      scenarioFileExport.setTeams(scenario.getTeams());
      objectMapper.addMixIn(Team.class, isWithPlayers ? Mixins.Team.class : Mixins.EmptyTeam.class);
      scenarioTags.addAll(
          scenario.getTeams().stream().flatMap(team -> team.getTags().stream()).toList());
    }

    if (isWithPlayers) {
      // Add players
      List<User> players =
          scenario.getTeams().stream()
              .flatMap(team -> team.getUsers().stream())
              .distinct()
              .toList();
      scenarioFileExport.setUsers(players);
      objectMapper.addMixIn(User.class, Mixins.User.class);
      scenarioTags.addAll(players.stream().flatMap(user -> user.getTags().stream()).toList());
      // organizations
      List<Organization> organizations =
          new ArrayList<>(
              players.stream().map(User::getOrganization).filter(Objects::nonNull).toList());
      organizations.addAll(
          scenario.getTeams().stream()
              .map(Team::getOrganization)
              .filter(Objects::nonNull)
              .toList());
      scenarioFileExport.setOrganizations(organizations);
      objectMapper.addMixIn(Organization.class, Mixins.Organization.class);
      scenarioTags.addAll(organizations.stream().flatMap(org -> org.getTags().stream()).toList());
    } else {
      objectMapper.addMixIn(ExerciseFileExport.class, Mixins.ScenarioWithoutPlayers.class);
    }

    // Add Injects
    objectMapper.addMixIn(Inject.class, Mixins.Inject.class);
    scenarioFileExport.setInjects(exportedInjects);
    exportedInjects.forEach(
        inject -> {
          scenarioTags.addAll(inject.getTags());
          inject
              .getInjectorContract()
              .ifPresent(
                  injectorContract -> {
                    if (injectorContract.getPayload() != null) {
                      scenarioTags.addAll(injectorContract.getTags());
                    }
                  });
        });

    // Add Articles
    objectMapper.addMixIn(Article.class, Mixins.Article.class);
    scenarioFileExport.setArticles(scenario.getArticles());
    // Add Channels
    objectMapper.addMixIn(Channel.class, Mixins.Channel.class);
    List<Channel> channels =
        scenario.getArticles().stream().map(Article::getChannel).distinct().toList();
    scenarioFileExport.setChannels(channels);
    documentIds.addAll(
        channels.stream()
            .flatMap(channel -> channel.getLogos().stream())
            .map(Document::getId)
            .toList());

    // Add Challenges
    objectMapper.addMixIn(Challenge.class, Mixins.Challenge.class);
    List<Challenge> challenges =
        fromIterable(this.challengeService.getScenarioChallenges(scenario));
    scenarioFileExport.setChallenges(challenges);
    scenarioTags.addAll(
        challenges.stream().flatMap(challenge -> challenge.getTags().stream()).toList());
    documentIds.addAll(
        challenges.stream()
            .flatMap(challenge -> challenge.getDocuments().stream())
            .map(Document::getId)
            .toList());

    // Tags
    scenarioFileExport.setTags(scenarioTags.stream().distinct().toList());
    objectMapper.addMixIn(Tag.class, Mixins.Tag.class);

    // Add Workflow (chaining) if present — scope definition is optional
    Optional<Workflow> workflowOpt =
        workflowService.findWorkflowTemplateByScenarioIdForExport(scenarioId);
    workflowOpt.ifPresent(
        workflow -> {
          workflowExportInitializer.initialize(workflow, isWithScopeDefinition);
          scenarioFileExport.setWorkflow(workflow);
        });
    objectMapper.addMixIn(
        Workflow.class,
        isWithScopeDefinition
            ? Mixins.WorkflowExport.class
            : Mixins.WorkflowExportWithoutScope.class);
    objectMapper.addMixIn(WorkflowScopeRule.class, Mixins.WorkflowScopeRuleExport.class);
    objectMapper.addMixIn(ScopeVariable.class, Mixins.ScopeVariableExport.class);
    objectMapper.addMixIn(Step.class, Mixins.StepExport.class);
    objectMapper.addMixIn(Condition.class, Mixins.ConditionExport.class);

    // Add Attackpattern and kill chain phases
    objectMapper.addMixIn(KillChainPhase.class, Mixins.KillChainPhase.class);
    objectMapper.addMixIn(AttackPattern.class, Mixins.AttackPattern.class);
    objectMapper.addMixIn(InjectorContract.class, Mixins.InjectorContract.class);
    objectMapper.addMixIn(Payload.class, Mixins.Payload.class);

    // load the killchainphases
    exportedInjects.stream()
        .map(Inject::getInjectorContract)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .flatMap(i -> i.getAttackPatterns().stream())
        .distinct()
        .toList()
        .stream()
        .forEach(attackPattern -> Hibernate.initialize(attackPattern.getKillChainPhases()));

    // Build the response
    String infos;
    if (isChaining) {
      infos =
          "("
              + (isWithVariableValues ? "with_variable_values" : "no_variable_values")
              + " & "
              + (isWithScopeDefinition ? "with_scope_definition" : "no_scope_definition")
              + ")";
    } else {
      infos =
          "("
              + (isWithTeams ? "with_teams" : "no_teams")
              + " & "
              + (isWithPlayers ? "with_players" : "no_players")
              + " & "
              + (isWithVariableValues ? "with_variable_values" : "no_variable_values")
              + ")";
    }

    String zipName = (scenario.getName() + "_" + now().toString()) + "_" + infos + ".zip";
    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipName);
    response.addHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
    response.setStatus(HttpServletResponse.SC_OK);
    ZipOutputStream zipExport = new ZipOutputStream(response.getOutputStream());
    ZipEntry zipEntry = new ZipEntry(scenario.getName() + ".json");
    zipEntry.setComment(EXPORT_ENTRY_SCENARIO);
    zipExport.putNextEntry(zipEntry);
    ObjectNode exportNode = objectMapper.valueToTree(scenarioFileExport);
    workflowExportInitializer.enrichWorkflowStepDataForExport(
        exportNode, "scenario_workflow", objectMapper);
    zipExport.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(exportNode));
    zipExport.closeEntry();
    // Add the documents
    documentIds.stream()
        .distinct()
        .forEach(
            docId -> {
              Document doc = this.documentRepository.findById(docId).orElseThrow();
              Optional<InputStream> docStream = this.fileService.getFile(doc);
              if (docStream.isPresent()) {
                try {
                  ZipEntry zipDoc = new ZipEntry(doc.getTarget());
                  zipDoc.setComment(EXPORT_ENTRY_ATTACHMENT);
                  byte[] data = docStream.get().readAllBytes();
                  zipExport.putNextEntry(zipDoc);
                  zipExport.write(data);
                  zipExport.closeEntry();
                } catch (IOException e) {
                  log.error(e.getMessage(), e);
                }
              }
            });
    zipExport.finish();
    zipExport.close();
  }

  // -- TEAMS --

  @Transactional(rollbackFor = Exception.class)
  public Iterable<TeamOutput> removeTeams(
      @NotBlank final String scenarioId, @NotNull final List<String> teamIds) {
    // Remove teams from scenario
    this.scenarioRepository.removeTeams(scenarioId, teamIds);
    // Remove only associations for this scenario
    this.scenarioTeamUserRepository.deleteByScenarioIdAndTeamIds(scenarioId, teamIds);
    // Remove all association between injects and teams
    this.injectRepository.removeTeamsForScenario(scenarioId, teamIds);
    // Remove all association between lessons learned and teams
    this.lessonsCategoryRepository.removeTeamsForScenario(scenarioId, teamIds);
    // The join-table deletes above are native queries that bypass JPA timestamps: bump the
    // scenario and its injects so the incremental indexer refreshes the denormalized team sides.
    this.scenarioRepository.touchUpdatedAt(scenarioId);
    this.injectRepository.touchUpdatedAtByScenarioId(scenarioId);
    return teamService.find(fromIds(teamIds));
  }

  @Transactional(rollbackFor = Exception.class)
  public List<TeamOutput> replaceTeams(
      @NotBlank final String scenarioId, @NotNull final List<String> teamIds) {
    Scenario scenario = this.scenario(scenarioId);
    Set<String> previousTeamIds =
        scenario.getTeams().stream().map(Team::getId).collect(Collectors.toSet());
    Set<String> targetTeamIds = new LinkedHashSet<>(teamIds);

    Set<String> removedTeamIds = new HashSet<>(previousTeamIds);
    removedTeamIds.removeAll(targetTeamIds);
    if (!removedTeamIds.isEmpty()) {
      List<String> removedTeamIdsList = new ArrayList<>(removedTeamIds);
      this.scenarioTeamUserRepository.deleteByScenarioIdAndTeamIds(scenarioId, removedTeamIdsList);
      this.injectRepository.removeTeamsForScenario(scenarioId, removedTeamIdsList);
      this.lessonsCategoryRepository.removeTeamsForScenario(scenarioId, removedTeamIdsList);
    }
    // Team changes alter the denormalized inject_teams of the scenario's injects (including
    // all-teams injects, derived from scenarios_teams): bump the injects so the incremental
    // indexer refreshes them (the native join-table mutations bypass JPA timestamps).
    this.injectRepository.touchUpdatedAtByScenarioId(scenarioId);

    // Replace teams from a scenario
    List<Team> teams = fromIterable(this.teamRepository.findAllById(targetTeamIds));
    scenario.setTeams(teams);
    this.scenarioRepository.save(scenario);

    List<String> teamIdsAdded =
        targetTeamIds.stream().filter(id -> !previousTeamIds.contains(id)).toList();

    List<Team> teamsAdded = fromIterable(this.teamRepository.findAllById(teamIdsAdded));

    // Enable user
    teamsAdded.forEach(
        team -> {
          List<String> playerIds = team.getUsers().stream().map(User::getId).toList();
          this.enablePlayers(scenarioId, team, playerIds);
        });

    // You must return all the modified teams to ensure the frontend store updates correctly
    List<String> modifiedTeamIds =
        Stream.concat(previousTeamIds.stream(), teams.stream().map(Team::getId))
            .distinct()
            .toList();
    return teamService.find(fromIds(modifiedTeamIds));
  }

  public Scenario addScenarioPlayer(
      @NotBlank final String scenarioId,
      @NotBlank final String teamId,
      @NotNull final List<String> playerIds) {
    Team team =
        teamRepository
            .findByIdAndTenantId(teamId, TenantContext.getCurrentTenant())
            .orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(playerIds);
    team.getUsers().addAll(fromIterable(teamUsers));
    Team savedTeam = teamRepository.save(team);
    return this.enablePlayers(scenarioId, savedTeam, playerIds);
  }

  public Scenario enableAddScenarioTeamPlayer(
      @NotBlank final String scenarioId,
      @NotBlank final String teamId,
      @NotNull final List<String> playerIds) {
    Team team =
        teamRepository
            .findByIdAndTenantId(teamId, TenantContext.getCurrentTenant())
            .orElseThrow(ElementNotFoundException::new);
    return this.enablePlayers(scenarioId, team, playerIds);
  }

  public Scenario enablePlayers(
      @NotBlank final String scenarioId,
      @NotBlank final Team team,
      @NotNull final List<String> playerIds) {
    Scenario scenario = this.scenario(scenarioId);
    playerIds.forEach(
        playerId -> {
          boolean alreadyLinked =
              this.scenarioTeamUserRepository.existsByScenarioIdAndTeamIdAndUserId(
                  scenarioId, team.getId(), playerId);
          if (alreadyLinked) {
            return;
          }
          ScenarioTeamUser scenarioTeamUser = new ScenarioTeamUser();
          scenarioTeamUser.setScenario(scenario);
          scenarioTeamUser.setTeam(team);
          scenarioTeamUser.setUser(this.userRepository.findById(playerId).orElseThrow());
          this.scenarioTeamUserRepository.save(scenarioTeamUser);
        });
    return scenario;
  }

  public Scenario disablePlayers(
      @NotBlank final String scenarioId,
      @NotBlank final String teamId,
      @NotNull final List<String> playerIds) {
    playerIds.forEach(
        playerId -> {
          ScenarioTeamUserId scenarioTeamUserId = new ScenarioTeamUserId();
          scenarioTeamUserId.setScenarioId(scenarioId);
          scenarioTeamUserId.setTeamId(teamId);
          scenarioTeamUserId.setUserId(playerId);
          this.scenarioTeamUserRepository.deleteById(scenarioTeamUserId);
        });
    return this.scenario(scenarioId);
  }

  @Transactional
  public Scenario getDuplicateScenario(@NotBlank String scenarioId) {
    if (StringUtils.isNotBlank(scenarioId)) {
      Scenario scenarioOrigin =
          scenarioRepository
              .findByIdAndTenantId(scenarioId, TenantContext.getCurrentTenant())
              .orElseThrow();
      Scenario scenario = copyScenario(scenarioOrigin);
      Scenario scenarioDuplicate = scenarioRepository.save(scenario);
      getListOfDuplicatedInjects(scenarioDuplicate, scenarioOrigin);
      getListOfScenarioTeams(scenarioDuplicate, scenarioOrigin);
      getListOfArticles(scenarioDuplicate, scenarioOrigin);
      getListOfVariables(scenarioDuplicate, scenarioOrigin);
      getObjectives(scenarioDuplicate, scenarioOrigin);
      getLessonsCategories(scenarioDuplicate, scenarioOrigin);
      this.actionMetricCollector.addScenarioCreatedCount();
      return scenarioRepository.save(scenario);
    }
    throw new ElementNotFoundException();
  }

  public boolean checkIfTagRulesApplies(
      @NotNull final Scenario scenario, @NotNull final List<String> newTags) {
    return tagRuleService.checkIfRulesApply(
        scenario.getTags().stream().map(Tag::getId).toList(), newTags);
  }

  private void getListOfScenarioTeams(
      @NotNull Scenario scenario, @NotNull Scenario scenarioOrigin) {
    Map<String, Team> contextualTeams = new HashMap<>();
    List<Team> scenarioTeams = new ArrayList<>();
    scenarioOrigin
        .getTeams()
        .forEach(
            scenarioTeam -> {
              if (scenarioTeam.getContextual()) {
                Team team = teamService.copyContextualTeam(scenarioTeam);
                Team teamSaved = this.teamRepository.save(team);
                scenarioTeams.add(teamSaved);
                contextualTeams.put(scenarioTeam.getId(), teamSaved);
              } else {
                scenarioTeams.add(scenarioTeam);
              }
            });
    scenario.setTeams(new ArrayList<>(scenarioTeams));

    List<Inject> scenarioInjects = scenario.getInjects();
    scenarioInjects.forEach(
        scenarioInject -> {
          List<Team> teams = new ArrayList<>();
          scenarioInject
              .getTeams()
              .forEach(
                  team -> {
                    if (team.getContextual()) {
                      teams.add(contextualTeams.get(team.getId()));
                    } else {
                      teams.add(team);
                    }
                  });
          scenarioInject.setTeams(teams);
        });
  }

  private Scenario copyScenario(Scenario scenario) {
    Scenario scenarioDuplicate = new Scenario();
    scenarioDuplicate.setName(duplicateString(scenario.getName()));
    scenarioDuplicate.setCategory(scenario.getCategory());
    scenarioDuplicate.setDescription(scenario.getDescription());
    scenarioDuplicate.setSeverity(scenario.getSeverity());
    scenarioDuplicate.setSubtitle(scenario.getSubtitle());
    scenarioDuplicate.setHeader(scenario.getHeader());
    scenarioDuplicate.setMainFocus(scenario.getMainFocus());
    scenarioDuplicate.setDefaultKillChain(scenario.getDefaultKillChain());
    scenarioDuplicate.setFrom(scenario.getFrom());
    scenarioDuplicate.setFromName(scenario.getFromName());
    scenarioDuplicate.setExternalUrl(scenario.getExternalUrl());
    scenarioDuplicate.setTags(new HashSet<>(scenario.getTags()));
    scenarioDuplicate.setInjects(new HashSet<>(scenario.getInjects()));
    scenarioDuplicate.setExternalReference(scenario.getExternalReference());
    scenarioDuplicate.setTeamUsers(new ArrayList<>(scenario.getTeamUsers()));
    scenarioDuplicate.setReplyTos(new ArrayList<>(scenario.getReplyTos()));
    scenarioDuplicate.setLessonsAnonymized(scenario.isLessonsAnonymized());
    scenarioDuplicate.setDocuments(new ArrayList<>(scenario.getDocuments()));
    scenarioDuplicate.setGrants(new ArrayList<>(scenario.getGrants()));
    scenarioDuplicate.setDependencies(cleanScenarioDependencies(scenario.getDependencies()));
    return scenarioDuplicate;
  }

  private Scenario.Dependency[] cleanScenarioDependencies(Scenario.Dependency[] dependencies) {
    if (dependencies == null) {
      return new Scenario.Dependency[0];
    }

    return Arrays.stream(dependencies)
        .filter(dependency -> !Scenario.Dependency.STARTERPACK.equals(dependency))
        .toArray(Scenario.Dependency[]::new);
  }

  private void getListOfDuplicatedInjects(
      @NotNull Scenario scenario, @NotNull Scenario scenarioOrigin) {
    Set<Inject> injectListForScenario =
        scenarioOrigin.getInjects().stream()
            .map(inject -> injectDuplicateService.duplicateInjectForScenario(scenario, inject))
            .collect(Collectors.toSet());
    scenario.setInjects(new HashSet<>(injectListForScenario));
  }

  private void getListOfArticles(@NotNull Scenario scenario, @NotNull Scenario scenarioOrigin) {
    Map<String, String> mapIdArticleOriginNew = new HashMap<>();
    List<Article> articleList = new ArrayList<>();
    scenarioOrigin
        .getArticles()
        .forEach(
            article -> {
              Article scenarioArticle = new Article();
              scenarioArticle.setName(article.getName());
              scenarioArticle.setContent(article.getContent());
              scenarioArticle.setAuthor(article.getAuthor());
              scenarioArticle.setShares(article.getShares());
              scenarioArticle.setLikes(article.getLikes());
              scenarioArticle.setComments(article.getComments());
              scenarioArticle.setChannel(article.getChannel());
              scenarioArticle.setDocuments(new ArrayList<>(article.getDocuments()));
              scenarioArticle.setScenario(scenario);
              Article save = articleRepository.save(scenarioArticle);
              articleList.add(save);
              mapIdArticleOriginNew.put(article.getId(), scenarioArticle.getId());
            });
    scenario.setArticles(articleList);
    for (Inject inject : scenario.getInjects()) {
      if (ofNullable(inject.getContent()).map(c -> c.has(ARTICLES)).orElse(Boolean.FALSE)) {
        List<String> articleNode = new ArrayList<>();
        JsonNode articles = inject.getContent().findValue(ARTICLES);
        if (articles.isArray()) {
          for (final JsonNode node : articles) {
            if (mapIdArticleOriginNew.containsKey(node.textValue())) {
              articleNode.add(mapIdArticleOriginNew.get(node.textValue()));
            }
          }
        }
        inject.getContent().remove(ARTICLES);
        ArrayNode arrayNode = inject.getContent().putArray(ARTICLES);
        articleNode.forEach(arrayNode::add);
      }
    }
  }

  private void getListOfVariables(Scenario scenario, Scenario scenarioOrigin) {
    List<Variable> variables = variableService.variablesFromScenario(scenarioOrigin.getId());
    List<Variable> variableList =
        variables.stream()
            .map(
                variable -> {
                  Variable variable1 = new Variable();
                  variable1.setKey(variable.getKey());
                  variable1.setDescription(variable.getDescription());
                  variable1.setValue(variable.getValue());
                  variable1.setType(variable.getType());
                  variable1.setScenario(scenario);
                  return variable1;
                })
            .toList();
    variableService.createVariables(variableList);
  }

  private void getLessonsCategories(Scenario duplicatedScenario, Scenario originalScenario) {
    List<LessonsCategory> duplicatedCategories = new ArrayList<>();
    for (LessonsCategory originalCategory : originalScenario.getLessonsCategories()) {
      LessonsCategory duplicatedCategory = new LessonsCategory();
      duplicatedCategory.setName(originalCategory.getName());
      duplicatedCategory.setDescription(originalCategory.getDescription());
      duplicatedCategory.setOrder(originalCategory.getOrder());
      duplicatedCategory.setScenario(duplicatedScenario);
      duplicatedCategory.setTeams(new ArrayList<>(originalCategory.getTeams()));

      List<LessonsQuestion> duplicatedQuestions = new ArrayList<>();
      for (LessonsQuestion originalQuestion : originalCategory.getQuestions()) {
        LessonsQuestion duplicatedQuestion = new LessonsQuestion();
        duplicatedQuestion.setCategory(originalQuestion.getCategory());
        duplicatedQuestion.setContent(originalQuestion.getContent());
        duplicatedQuestion.setExplanation(originalQuestion.getExplanation());
        duplicatedQuestion.setOrder(originalQuestion.getOrder());
        duplicatedQuestion.setCategory(duplicatedCategory);

        List<LessonsAnswer> duplicatedAnswers = new ArrayList<>();
        for (LessonsAnswer originalAnswer : originalQuestion.getAnswers()) {
          LessonsAnswer duplicatedAnswer = new LessonsAnswer();
          duplicatedAnswer.setUser(originalAnswer.getUser());
          duplicatedAnswer.setScore(originalAnswer.getScore());
          duplicatedAnswer.setPositive(originalAnswer.getPositive());
          duplicatedAnswer.setNegative(originalAnswer.getNegative());
          duplicatedAnswer.setQuestion(duplicatedQuestion);
          duplicatedAnswers.add(duplicatedAnswer);
        }
        duplicatedQuestion.setAnswers(duplicatedAnswers);
        duplicatedQuestions.add(duplicatedQuestion);
      }
      duplicatedCategory.setQuestions(duplicatedQuestions);
      duplicatedCategories.add(duplicatedCategory);
    }
    duplicatedScenario.setLessonsCategories(duplicatedCategories);
  }

  private void getObjectives(Scenario scenario, Scenario scenarioOrigin) {
    List<Objective> duplicatedObjectives = new ArrayList<>();
    for (Objective originalObjective : scenarioOrigin.getObjectives()) {
      Objective duplicatedObjective = new Objective();
      duplicatedObjective.setTitle(originalObjective.getTitle());
      duplicatedObjective.setDescription(originalObjective.getDescription());
      duplicatedObjective.setPriority(originalObjective.getPriority());
      List<Evaluation> duplicatedEvaluations = new ArrayList<>();
      for (Evaluation originalEvaluation : originalObjective.getEvaluations()) {
        Evaluation duplicatedEvaluation = new Evaluation();
        duplicatedEvaluation.setScore(originalEvaluation.getScore());
        duplicatedEvaluation.setUser(originalEvaluation.getUser());
        duplicatedEvaluation.setObjective(duplicatedObjective);
        duplicatedEvaluations.add(duplicatedEvaluation);
      }
      duplicatedObjective.setEvaluations(duplicatedEvaluations);
      duplicatedObjective.setScenario(scenario);
      duplicatedObjectives.add(duplicatedObjective);
    }
    scenario.setObjectives(duplicatedObjectives);
  }

  /**
   * Verify all healthcheck for a given scenario id
   *
   * @param scenarioId to verify
   * @return founded healthcheck list
   */
  @Transactional(readOnly = true)
  public List<HealthCheck> runChecks(String scenarioId) {
    if (scenarioId == null) {
      return null;
    }

    List<HealthCheck> healthChecks = new ArrayList<>();

    Scenario scenario = this.scenario(scenarioId);

    // get the healthcheck for each injects, remove duplicate from injects HealthCheck results and
    // add them to the result
    List<HealthCheck> injectsHealthChecks =
        healthCheckUtils.removeDuplicates(
            scenario.getInjects().stream()
                .flatMap(inject -> injectService.runChecks(inject).stream())
                .toList());

    // Since Injects healthchecks now have the "Missing Content" (and maybe others) checks details,
    // we dont want them into the Scenario checks.
    // That's why we have to verify if there is existing checks necessary to the scenario into the
    // injects checks.
    healthChecks.addAll(
        healthCheckUtils.runInjectsChecksFor(
            HealthCheck.Type.SMTP,
            HealthCheck.Detail.SERVICE_UNAVAILABLE,
            HealthCheck.Status.ERROR,
            injectsHealthChecks));
    healthChecks.addAll(
        healthCheckUtils.runInjectsChecksFor(
            HealthCheck.Type.IMAP,
            HealthCheck.Detail.SERVICE_UNAVAILABLE,
            HealthCheck.Status.WARNING,
            injectsHealthChecks));
    healthChecks.addAll(
        healthCheckUtils.runInjectsChecksFor(
            HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR,
            HealthCheck.Detail.EMPTY,
            HealthCheck.Status.ERROR,
            injectsHealthChecks));
    healthChecks.addAll(
        healthCheckUtils.runInjectsChecksFor(
            HealthCheck.Type.NMAP,
            HealthCheck.Detail.SERVICE_UNAVAILABLE,
            HealthCheck.Status.ERROR,
            injectsHealthChecks));
    healthChecks.addAll(
        healthCheckUtils.runInjectsChecksFor(
            HealthCheck.Type.NUCLEI,
            HealthCheck.Detail.SERVICE_UNAVAILABLE,
            HealthCheck.Status.ERROR,
            injectsHealthChecks));
    healthChecks.addAll(
        healthCheckUtils.runInjectsChecksFor(
            HealthCheck.Type.AGENT_OR_EXECUTOR,
            HealthCheck.Detail.EMPTY,
            HealthCheck.Status.ERROR,
            injectsHealthChecks));
    healthChecks.addAll(healthCheckUtils.runMissingContentChecks(scenario));
    healthChecks.addAll(healthCheckUtils.runTeamsChecks(scenario));

    // Scope definition check
    try {
      workflowService
          .findWorkflowTemplateByScenarioId(scenarioId)
          .ifPresent(
              workflow -> healthChecks.addAll(healthCheckUtils.runScopeDefinitionChecks(workflow)));
    } catch (ChainingException e) {
      log.debug("Skipping scope definition check: {}", e.getMessage());
    }

    return healthChecks;
  }

  private Scenario computeAndCreateScenario(Scenario scenario) {
    computeEmails(scenario);
    this.actionMetricCollector.addScenarioCreatedCount();
    return this.scenarioRepository.save(scenario);
  }

  private Scenario prepareScenarioFromScenarioInput(
      @NotBlank final String tenantId, @NotNull final ScenarioInput input) {
    Scenario scenario = new Scenario();
    scenario.setUpdateAttributes(input);
    scenario.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    if (hasText(input.getCustomDashboard())) {
      scenario.setCustomDashboard(
          this.customDashboardService.customDashboard(input.getCustomDashboard()));
    } else {
      scenario.setCustomDashboard(
          this.tenantSettingsService
              .findSetting(tenantId, TenantSettingKeys.TENANT_SCENARIO_DASHBOARD.key())
              .map(Setting::getValue)
              .filter(v -> !v.isEmpty())
              .map(this.customDashboardService::customDashboard)
              .orElse(null));
    }
    return scenario;
  }
}
