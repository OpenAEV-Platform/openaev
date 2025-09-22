package io.openbas.rest.inject.service;

import static io.openbas.database.model.CollectExecutionStatus.COLLECTING;
import static io.openbas.database.model.ExecutionStatus.*;
import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY;
import static io.openbas.database.model.Payload.PAYLOAD_EXECUTION_ARCH.*;
import static io.openbas.database.specification.InjectSpecification.*;
import static io.openbas.helper.StreamHelper.fromIterable;
import static io.openbas.helper.StreamHelper.iterableToSet;
import static io.openbas.utils.AgentUtils.isPrimaryAgent;
import static io.openbas.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.openbas.utils.StringUtils.duplicateString;
import static io.openbas.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static java.time.Instant.now;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openbas.config.cache.LicenseCacheManager;
import io.openbas.database.model.*;
import io.openbas.database.repository.*;
import io.openbas.database.specification.InjectSpecification;
import io.openbas.database.specification.SpecificationUtils;
import io.openbas.ee.Ee;
import io.openbas.injector_contract.ContractTargetedProperty;
import io.openbas.injector_contract.fields.ContractFieldType;
import io.openbas.rest.atomic_testing.form.ExecutionTraceOutput;
import io.openbas.rest.atomic_testing.form.InjectResultOverviewOutput;
import io.openbas.rest.atomic_testing.form.InjectStatusOutput;
import io.openbas.rest.document.DocumentService;
import io.openbas.rest.exception.BadRequestException;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.exception.LicenseRestrictionException;
import io.openbas.rest.inject.form.*;
import io.openbas.rest.inject.output.AgentsAndAssetsAgentless;
import io.openbas.rest.injector_contract.InjectorContractContentUtils;
import io.openbas.rest.injector_contract.InjectorContractService;
import io.openbas.rest.security.SecurityExpression;
import io.openbas.rest.security.SecurityExpressionHandler;
import io.openbas.rest.tag.TagService;
import io.openbas.service.*;
import io.openbas.utils.FilterUtilsJpa;
import io.openbas.utils.InjectUtils;
import io.openbas.utils.JpaUtils;
import io.openbas.utils.TargetType;
import io.openbas.utils.mapper.InjectMapper;
import io.openbas.utils.mapper.InjectStatusMapper;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.hibernate.Hibernate;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@RequiredArgsConstructor
@Service
@Slf4j
public class InjectService {

  private final TeamRepository teamRepository;
  private final ExecutionTraceRepository executionTraceRepository;
  private final AssetService assetService;
  private final AssetGroupService assetGroupService;
  private final Ee eeService;
  private final EndpointService endpointService;
  private final InjectRepository injectRepository;
  private final InjectDocumentRepository injectDocumentRepository;
  private final InjectStatusRepository injectStatusRepository;
  private final InjectMapper injectMapper;
  private final MethodSecurityExpressionHandler methodSecurityExpressionHandler;
  private final UserService userService;
  private final InjectorContractService injectorContractService;
  private final TagRuleService tagRuleService;
  private final TagService tagService;
  private final DocumentService documentService;
  private final InjectStatusMapper injectStatusMapper;
  private final TagRepository tagRepository;
  private final DocumentRepository documentRepository;
  private final PayloadRepository payloadRepository;

  private final LicenseCacheManager licenseCacheManager;
  @Resource protected ObjectMapper mapper;

  private SecurityExpression getAmbientSecurityExpression() {
    return ((SecurityExpressionHandler) methodSecurityExpressionHandler).getSecurityExpression();
  }

  // -- CRUD --

  public Inject createInject(
      @Nullable final Exercise exercise,
      @Nullable final Scenario scenario,
      @NotNull final InjectInput input) {
    if (exercise == null && scenario == null || exercise != null && scenario != null) {
      throw new IllegalArgumentException("Exactly one of exercise or scenario should be present");
    }

    InjectorContract injectorContract =
        this.injectorContractService.injectorContract(input.getInjectorContract());
    // Get common attributes
    Inject inject = input.toInject(injectorContract);
    inject.setUser(this.userService.currentUser());
    inject.setTeams(fromIterable(teamRepository.findAllById(input.getTeams())));
    inject.setAssets(fromIterable(assetService.assets(input.getAssets())));
    inject.setTags(tagService.tagSet(input.getTagIds()));
    List<InjectDocument> injectDocuments =
        input.getDocuments().stream()
            .map(i -> i.toDocument(documentService.document(i.getDocumentId()), inject))
            .toList();
    inject.setDocuments(injectDocuments);
    // Set dependencies
    if (input.getDependsOn() != null) {
      inject
          .getDependsOn()
          .addAll(
              input.getDependsOn().stream()
                  .map(
                      injectDependencyInput ->
                          injectDependencyInput.toInjectDependency(
                              inject,
                              this.inject(
                                  injectDependencyInput.getRelationship().getInjectParentId())))
                  .toList());
    }

    Set<Tag> tags = new HashSet<>();
    // EXERCISE
    if (exercise != null) {
      tags = exercise.getTags();
      inject.setExercise(exercise);
      // Linked documents directly to the exercise
      inject
          .getDocuments()
          .forEach(
              document -> {
                if (!document.getDocument().getExercises().contains(exercise)) {
                  exercise.getDocuments().add(document.getDocument());
                }
              });
    }
    // SCENARIO
    if (scenario != null) {
      tags = scenario.getTags();
      inject.setScenario(scenario);
      // Linked documents directly to the scenario
      inject
          .getDocuments()
          .forEach(
              document -> {
                if (!document.getDocument().getScenarios().contains(scenario)) {
                  scenario.getDocuments().add(document.getDocument());
                }
              });
    }

    // verify if inject is not manual/sms/emails...
    if (this.canApplyTargetType(inject, TargetType.ASSETS_GROUPS)) {
      // add default asset groups
      inject.setAssetGroups(
          this.tagRuleService.applyTagRuleToInjectCreation(
              tags.stream().map(Tag::getId).toList(),
              assetGroupService.assetGroups(input.getAssetGroups())));
    }

    // if inject content is null we add the defaults from the injector contract
    // this is the case when creating an inject from OpenCti
    if (inject.getContent() == null || inject.getContent().isEmpty()) {
      inject.setContent(
          InjectorContractContentUtils.getDynamicInjectorContractFieldsForInject(injectorContract));
    }

    return injectRepository.save(inject);
  }

  public Inject inject(@NotBlank final String injectId) {
    return this.injectRepository
        .findById(injectId)
        .orElseThrow(() -> new ElementNotFoundException("Inject not found with id: " + injectId));
  }

  /**
   * Builds an Inject object based on the provided InjectorContract, title, description and enabled
   *
   * @param injectorContract the InjectorContract associated with the Inject
   * @param title the title of the Inject
   * @param description the description of the Inject
   * @param enabled indicates whether the Inject is enabled or not
   * @return the inject object built
   */
  public Inject buildInject(
      InjectorContract injectorContract, String title, String description, Boolean enabled) {
    Inject inject = new Inject();
    inject.setTitle(title);
    inject.setDescription(description);
    inject.setInjectorContract(injectorContract);
    inject.setDependsDuration(0L);
    inject.setEnabled(enabled);
    inject.setContent(
        InjectorContractContentUtils.getDynamicInjectorContractFieldsForInject(injectorContract));
    return inject;
  }

  /**
   * Builds a technical Inject object from the provided InjectorContract and AttackPattern.
   *
   * @param injectorContract the InjectorContract to build the Inject from
   * @param identifier the AttackPattern or Vulnerability associated with the Inject
   * @param name the AttackPattern or Vulnerability associated with the Inject
   * @return the built Inject object
   */
  public Inject buildTechnicalInject(
      InjectorContract injectorContract, String identifier, String name) {
    return buildInject(
        injectorContract,
        String.format("[%s] %s - %s", identifier, name, injectorContract.getLabels().get("en")),
        null,
        true);
  }

  @Transactional(rollbackOn = Exception.class)
  public void deleteAllByIds(List<String> injectIds) {
    if (!CollectionUtils.isEmpty(injectIds)) {
      injectRepository.deleteAllById(injectIds);
    }
  }

  /**
   * Delete all injects given as params
   *
   * @param injects the injects to delete
   */
  @Transactional(rollbackOn = Exception.class)
  public void deleteAll(List<Inject> injects) {
    if (!CollectionUtils.isEmpty(injects)) {
      injectRepository.deleteAll(injects);
    }
  }

  /**
   * Save all injects given as params
   *
   * @param injects the injects to save
   */
  @Transactional(rollbackOn = Exception.class)
  public List<Inject> saveAll(List<Inject> injects) {
    if (!CollectionUtils.isEmpty(injects)) {
      return injectRepository.saveAll(injects);
    }
    // empty collection
    return injects;
  }

  // -- SPECIFIC GETTER --

  public List<Inject> getExecutedAndNotFinished() {
    return this.injectRepository.findAll(
        hasStatus(List.of(SUCCESS, ERROR, MAYBE_PREVENTED, PARTIAL, MAYBE_PARTIAL_PREVENTED))
            .and(hasCollectingStatus(List.of(COLLECTING)))
            .and(fromRunningSimulation()));
  }

  // -- ASSETS --
  public List<AssetToExecute> resolveAllAssetsToExecute(@NotNull final Inject inject) {
    List<AssetToExecute> assetToExecutes = new ArrayList<>();

    inject.getAssets().forEach(asset -> assetToExecutes.add(new AssetToExecute(asset)));

    inject
        .getAssetGroups()
        .forEach(
            assetGroup -> {
              List<Asset> assetsFromGroup =
                  this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());

              assetsFromGroup.forEach(
                  asset -> {
                    AssetToExecute existingAssetToExecute =
                        assetToExecutes.stream()
                            .filter(as -> as.asset().getId().equals(asset.getId()))
                            .findFirst()
                            .orElse(null);

                    if (existingAssetToExecute != null) {
                      existingAssetToExecute.assetGroups().add(assetGroup);
                    } else {
                      AssetToExecute newAssetToExecute =
                          new AssetToExecute(asset, false, new ArrayList<>());
                      newAssetToExecute.assetGroups().add(assetGroup);
                      assetToExecutes.add(newAssetToExecute);
                    }
                  });
            });

    return assetToExecutes;
  }

  public void cleanInjectsDocExercise(String exerciseId, String documentId) {
    // Delete document from all exercise injects
    List<Inject> exerciseInjects =
        injectRepository.findAllForExerciseAndDoc(exerciseId, documentId);
    List<InjectDocument> updatedInjects =
        exerciseInjects.stream()
            .flatMap(
                inject -> {
                  @SuppressWarnings("UnnecessaryLocalVariable")
                  Stream<InjectDocument> filterDocuments =
                      inject.getDocuments().stream()
                          .filter(document -> document.getDocument().getId().equals(documentId));
                  return filterDocuments;
                })
            .toList();
    injectDocumentRepository.deleteAll(updatedInjects);
  }

  public <T> T convertInjectContent(@NotNull final Inject inject, @NotNull final Class<T> converter)
      throws Exception {
    ObjectNode content = inject.getContent();
    return this.mapper.treeToValue(content, converter);
  }

  public void cleanInjectsDocScenario(String scenarioId, String documentId) {
    // Delete document from all scenario injects
    List<Inject> scenarioInjects =
        injectRepository.findAllForScenarioAndDoc(scenarioId, documentId);
    List<InjectDocument> updatedInjects =
        scenarioInjects.stream()
            .flatMap(
                inject -> {
                  @SuppressWarnings("UnnecessaryLocalVariable")
                  Stream<InjectDocument> filterDocuments =
                      inject.getDocuments().stream()
                          .filter(document -> document.getDocument().getId().equals(documentId));
                  return filterDocuments;
                })
            .toList();
    injectDocumentRepository.deleteAll(updatedInjects);
  }

  @Transactional
  public InjectResultOverviewOutput duplicate(String id) {
    Inject duplicatedInject = findAndDuplicateInject(id);
    duplicatedInject.setTitle(duplicateString(duplicatedInject.getTitle()));
    Inject savedInject = injectRepository.save(duplicatedInject);
    return injectMapper.toInjectResultOverviewOutput(savedInject);
  }

  public void throwIfInjectNotLaunchable(Inject inject) {
    if (eeService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo())) {
      return;
    }
    List<Agent> agents = this.getAgentsByInject(inject);
    List<String> eeExecutors = eeService.detectEEExecutors(agents);

    if (!eeExecutors.isEmpty()) {
      throw new LicenseRestrictionException(
          "Some asset will be executed through " + String.join(" and ", eeExecutors));
    }
  }

  @Transactional
  public InjectResultOverviewOutput launch(String id) {
    Inject inject = injectRepository.findById(id).orElseThrow(ElementNotFoundException::new);
    this.throwIfInjectNotLaunchable(inject);
    inject.clean();
    inject.setUpdatedAt(Instant.now());
    Inject savedInject = saveInjectAndStatusAsQueuing(inject);
    return injectMapper.toInjectResultOverviewOutput(savedInject);
  }

  @Transactional
  public InjectResultOverviewOutput relaunch(String id) {
    Inject duplicatedInject = findAndDuplicateInject(id);
    this.throwIfInjectNotLaunchable(duplicatedInject);
    Inject savedInject = saveInjectAndStatusAsQueuing(duplicatedInject);
    delete(id);
    return injectMapper.toInjectResultOverviewOutput(savedInject);
  }

  @Transactional
  public void delete(String id) {
    injectDocumentRepository.deleteDocumentsFromInject(id);
    injectRepository.deleteById(id);
  }

  /**
   * Update an inject with default asset groups
   *
   * @param injectId
   * @param defaultAssetGroupsToAdd
   * @return
   */
  @Transactional
  public Inject applyDefaultAssetGroupsToInject(
      final String injectId, final List<AssetGroup> defaultAssetGroupsToAdd) {

    // fetch the inject
    Inject inject =
        this.injectRepository.findById(injectId).orElseThrow(ElementNotFoundException::new);

    // remove/add default asset groups and remove duplicates
    List<AssetGroup> currentAssetGroups = inject.getAssetGroups();

    Set<String> uniqueAssetGroupIds = new HashSet<>();
    List<AssetGroup> newListOfAssetGroups =
        Stream.concat(currentAssetGroups.stream(), defaultAssetGroupsToAdd.stream())
            .filter(assetGroup -> uniqueAssetGroupIds.add(assetGroup.getId()))
            .collect(Collectors.toList());

    if (new HashSet<>(currentAssetGroups).equals(new HashSet<>(newListOfAssetGroups))) {
      return inject;
    } else {
      inject.setAssetGroups(newListOfAssetGroups);
      return this.injectRepository.save(inject);
    }
  }

  public boolean canApplyTargetType(final Inject inject, TargetType targetType) {
    Optional<InjectorContract> ic = inject.getInjectorContract();
    if (ic.isEmpty()) {
      return false;
    }
    return injectorContractService.checkTargetSupport(ic.get(), targetType);
  }

  public void assignAssetGroup(final Inject inject, List<AssetGroup> assetGroups) {
    if (this.canApplyTargetType(inject, TargetType.ASSETS_GROUPS)) {
      inject.setAssetGroups(assetGroups);
    } else if (this.canApplyTargetType(inject, TargetType.ASSETS)) {
      inject.setAssets(
          assetGroupService.assetsFromAssetGroupMap(assetGroups).values().stream()
              .flatMap(endpoints -> endpoints.stream().map(e -> (Asset) e))
              .collect(Collectors.toSet())
              .stream()
              .toList());
    } else {
      log.warn("Injector contract does not support either Asset Groups or Assets.");
    }
  }

  private Inject findAndDuplicateInject(String id) {
    Inject injectOrigin = injectRepository.findById(id).orElseThrow(ElementNotFoundException::new);
    return InjectUtils.duplicateInject(injectOrigin);
  }

  private Inject saveInjectAndStatusAsQueuing(Inject inject) {
    Inject savedInject = injectRepository.save(inject);
    InjectStatus injectStatus = saveInjectStatusAsQueuing(savedInject);
    savedInject.setStatus(injectStatus);
    return savedInject;
  }

  private InjectStatus saveInjectStatusAsQueuing(Inject inject) {
    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setInject(inject);
    injectStatus.setTrackingSentDate(Instant.now());
    injectStatus.setName(ExecutionStatus.QUEUING);
    this.injectStatusRepository.save(injectStatus);
    return injectStatus;
  }

  /**
   * Get the inject specification for the search pagination input related where the user has grants
   * (through scenarios or simulations)
   *
   * @param input the search input
   * @param requestedGrantLevel the requested grant level to filter the injects
   * @return the inject specification to search in DB
   * @throws BadRequestException if neither of the searchPaginationInput or injectIDsToSearch is
   *     provided
   */
  public Specification<Inject> getInjectSpecification(
      final InjectBulkProcessingInput input, Grant.GRANT_TYPE requestedGrantLevel) {
    if ((CollectionUtils.isEmpty(input.getInjectIDsToProcess())
            && (input.getSearchPaginationInput() == null))
        || (!CollectionUtils.isEmpty(input.getInjectIDsToProcess())
            && (input.getSearchPaginationInput() != null))) {
      throw new BadRequestException(
          "Either inject_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }
    Specification<Inject> filterSpecifications =
        InjectSpecification.fromScenarioOrSimulation(input.getSimulationOrScenarioId());
    if (input.getSearchPaginationInput() == null) {
      filterSpecifications =
          filterSpecifications.and(
              JpaUtils.computeIn(Inject.ID_FIELD_NAME, input.getInjectIDsToProcess()));
    } else {
      filterSpecifications =
          filterSpecifications.and(
              computeFilterGroupJpa(input.getSearchPaginationInput().getFilterGroup()));
      filterSpecifications =
          filterSpecifications.and(
              computeSearchJpa(input.getSearchPaginationInput().getTextSearch()));
    }
    if (!CollectionUtils.isEmpty(input.getInjectIDsToIgnore())) {
      filterSpecifications =
          filterSpecifications.and(
              JpaUtils.computeNotIn(Inject.ID_FIELD_NAME, input.getInjectIDsToIgnore()));
    }
    // Filter out any injects not related to resources where the user is granted with the
    // appropriate level
    filterSpecifications = filterSpecifications.and(hasGrantAccessForInject(requestedGrantLevel));
    return filterSpecifications;
  }

  /**
   * Update injects in bulk corresponding to the given criteria with a list of operations
   *
   * @param injectsToUpdate list of injects to update
   * @param operations the operations to perform with fields and values to add, remove or replace
   * @return the list of updated injects
   */
  public List<Inject> bulkUpdateInject(
      final List<Inject> injectsToUpdate, final List<InjectBulkUpdateOperation> operations) {
    // We aggregate the different field values in distinct sets in order to avoid retrieving the
    // same data multiple times
    Set<String> teamsIDs = new HashSet<>();
    Set<String> assetsIDs = new HashSet<>();
    Set<String> assetGroupsIDs = new HashSet<>();
    for (var operation : operations) {
      if (CollectionUtils.isEmpty(operation.getValues())) {
        continue;
      }

      switch (operation.getField()) {
        case TEAMS -> teamsIDs.addAll(operation.getValues());
        case ASSETS -> assetsIDs.addAll(operation.getValues());
        case ASSET_GROUPS -> assetGroupsIDs.addAll(operation.getValues());
        default ->
            throw new BadRequestException("Invalid field to update: " + operation.getOperation());
      }
    }

    // We retrieve the data from DB for teams, assets and asset groups in the input values
    Map<String, Team> teamsFromDB =
        this.teamRepository.findAllById(teamsIDs).stream()
            .collect(Collectors.toMap(Team::getId, team -> team));
    Map<String, Asset> assetsFromDB =
        this.assetService.assets(assetsIDs.stream().toList()).stream()
            .collect(Collectors.toMap(Asset::getId, asset -> asset));
    Map<String, AssetGroup> assetGroupsFromDB =
        this.assetGroupService.assetGroups(assetGroupsIDs.stream().toList()).stream()
            .collect(Collectors.toMap(AssetGroup::getId, assetGroup -> assetGroup));

    // we update the injects values
    injectsToUpdate.forEach(
        inject -> {
          applyUpdateOperation(inject, operations, teamsFromDB, assetsFromDB, assetGroupsFromDB);
          inject.setUpdatedAt(now());
        });

    // Save updated injects and return them
    return this.injectRepository.saveAll(injectsToUpdate);
  }

  /**
   * Get the injects to update/delete and check if the user is allowed to update/delete them
   *
   * @param input the injects search input.
   * @return the injects to update/delete
   * @throws AccessDeniedException if the user is not allowed to update/delete the injects
   */
  public List<Inject> getInjectsAndCheckPermission(
      InjectBulkProcessingInput input, Grant.GRANT_TYPE requested_grant_level) {
    // Control and format inputs
    // Specification building
    Specification<Inject> filterSpecifications =
        getInjectSpecification(input, requested_grant_level);

    // Services calls
    // Bulk select, only on injects granted through scenario or simulation (or without grant for
    // atomic tests)
    return this.injectRepository.findAll(filterSpecifications);
  }

  /**
   * Check if the user is allowed to operate on the injects based on security challenge
   *
   * @param injects the injects to check
   * @param authoriseFunction the function to check if the user has the relevant privilege on
   *     injects
   * @return List of all authorised Injects
   */
  public InjectAuthorisationResult authorise(
      List<Inject> injects, BiFunction<SecurityExpression, String, Boolean> authoriseFunction) {
    InjectAuthorisationResult result = new InjectAuthorisationResult();
    for (Inject inject : injects) {
      if (authoriseFunction.apply(getAmbientSecurityExpression(), inject.getId())) {
        result.addAuthorised(inject);
      } else {
        result.addUnauthorised(inject);
      }
    }
    return result;
  }

  public Inject updateInject(
      @NotBlank final String injectId, @jakarta.validation.constraints.NotNull InjectInput input) {
    Inject inject =
        this.injectRepository.findById(injectId).orElseThrow(ElementNotFoundException::new);
    inject.setUpdateAttributes(input);

    // Set dependencies
    if (input.getDependsOn() != null) {
      input
          .getDependsOn()
          .forEach(
              entry -> {
                Optional<InjectDependency> existingDependency =
                    inject.getDependsOn().stream()
                        .filter(
                            injectDependency ->
                                injectDependency
                                    .getCompositeId()
                                    .getInjectParent()
                                    .getId()
                                    .equals(entry.getRelationship().getInjectParentId()))
                        .findFirst();
                if (existingDependency.isPresent()) {
                  existingDependency
                      .get()
                      .getInjectDependencyCondition()
                      .setConditions(entry.getConditions().getConditions());
                  existingDependency
                      .get()
                      .getInjectDependencyCondition()
                      .setMode(entry.getConditions().getMode());
                } else {
                  InjectDependency injectDependency = new InjectDependency();
                  injectDependency.getCompositeId().setInjectChildren(inject);
                  injectDependency
                      .getCompositeId()
                      .setInjectParent(
                          injectRepository
                              .findById(entry.getRelationship().getInjectParentId())
                              .orElse(null));
                  injectDependency.setInjectDependencyCondition(
                      new InjectDependencyConditions.InjectDependencyCondition());
                  injectDependency
                      .getInjectDependencyCondition()
                      .setConditions(entry.getConditions().getConditions());
                  injectDependency
                      .getInjectDependencyCondition()
                      .setMode(entry.getConditions().getMode());
                  inject.getDependsOn().add(injectDependency);
                }
              });
    }

    List<InjectDependency> injectDepencyToRemove = new ArrayList<>();
    if (inject.getDependsOn() != null && !inject.getDependsOn().isEmpty()) {
      if (input.getDependsOn() != null && !input.getDependsOn().isEmpty()) {
        inject
            .getDependsOn()
            .forEach(
                injectDependency -> {
                  if (!input.getDependsOn().stream()
                      .map(
                          (injectDependencyInput ->
                              injectDependencyInput.getRelationship().getInjectParentId()))
                      .toList()
                      .contains(injectDependency.getCompositeId().getInjectParent().getId())) {
                    injectDepencyToRemove.add(injectDependency);
                  }
                });
      } else {
        injectDepencyToRemove.addAll(inject.getDependsOn());
      }
      inject.getDependsOn().removeAll(injectDepencyToRemove);
    }

    inject.setTeams(fromIterable(this.teamRepository.findAllById(input.getTeams())));
    inject.setAssets(fromIterable(this.assetService.assets(input.getAssets())));
    inject.setAssetGroups(fromIterable(this.assetGroupService.assetGroups(input.getAssetGroups())));
    inject.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));

    // Set documents
    List<InjectDocumentInput> inputDocuments = input.getDocuments();
    List<InjectDocument> injectDocuments = inject.getDocuments();

    List<String> askedDocumentIds =
        inputDocuments.stream().map(InjectDocumentInput::getDocumentId).toList();
    List<String> currentDocumentIds =
        inject.getDocuments().stream().map(document -> document.getDocument().getId()).toList();
    // To delete
    List<InjectDocument> toRemoveDocuments =
        injectDocuments.stream()
            .filter(injectDoc -> !askedDocumentIds.contains(injectDoc.getDocument().getId()))
            .toList();
    injectDocuments.removeAll(toRemoveDocuments);
    // To add
    inputDocuments.stream()
        .filter(doc -> !currentDocumentIds.contains(doc.getDocumentId()))
        .forEach(
            in -> {
              Optional<Document> doc = this.documentRepository.findById(in.getDocumentId());
              if (doc.isPresent()) {
                InjectDocument injectDocument = new InjectDocument();
                injectDocument.setInject(inject);
                Document document = doc.get();
                injectDocument.setDocument(document);
                injectDocument.setAttached(in.isAttached());
                InjectDocument savedInjectDoc = this.injectDocumentRepository.save(injectDocument);
                injectDocuments.add(savedInjectDoc);
              }
            });
    // Remap the attached boolean
    injectDocuments.forEach(
        injectDoc -> {
          Optional<InjectDocumentInput> inputInjectDoc =
              input.getDocuments().stream()
                  .filter(id -> id.getDocumentId().equals(injectDoc.getDocument().getId()))
                  .findFirst();
          Boolean attached = inputInjectDoc.map(InjectDocumentInput::isAttached).orElse(false);
          injectDoc.setAttached(attached);
        });
    inject.setDocuments(injectDocuments);

    return inject;
  }

  public Inject updateInjectActivation(
      @NotBlank final String injectId,
      @jakarta.validation.constraints.NotNull final InjectUpdateActivationInput input) {
    Inject inject =
        this.injectRepository.findById(injectId).orElseThrow(ElementNotFoundException::new);
    inject.setEnabled(input.isEnabled());
    inject.setUpdatedAt(now());
    return injectRepository.save(inject);
  }

  /**
   * Update the inject with the given input
   *
   * @param injectToUpdate the inject to update
   * @param operations the operation to perform, with the values to add, remove or replace
   * @param teamsFromDB the teams from the DB, coming from the input values
   * @param assetsFromDB the assets from the DB, coming from the input values
   * @param assetGroupsFromDB the asset groups from the DB, coming from the input values
   */
  private void applyUpdateOperation(
      Inject injectToUpdate,
      List<InjectBulkUpdateOperation> operations,
      Map<String, Team> teamsFromDB,
      Map<String, Asset> assetsFromDB,
      Map<String, AssetGroup> assetGroupsFromDB) {
    if (CollectionUtils.isEmpty(operations)) {
      return;
    }

    for (var operation : operations) {
      switch (operation.getField()) {
        case TEAMS ->
            updateInjectEntities(
                injectToUpdate.getTeams(),
                operation.getValues(),
                teamsFromDB,
                operation.getOperation());
        case ASSETS ->
            updateInjectEntities(
                injectToUpdate.getAssets(),
                operation.getValues(),
                assetsFromDB,
                operation.getOperation());
        case ASSET_GROUPS ->
            updateInjectEntities(
                injectToUpdate.getAssetGroups(),
                operation.getValues(),
                assetGroupsFromDB,
                operation.getOperation());
        default ->
            throw new BadRequestException("Invalid field to update: " + operation.getField());
      }
    }
  }

  /**
   * Update the inject entities
   *
   * @param injectEntities the inject entities to update
   * @param newValuesIDs the IDs of the value to add, remove or replace
   * @param entitiesFromDB the entities from the DB
   * @param operation the operation to apply
   * @param <T> the type of the entities
   */
  private <T> void updateInjectEntities(
      List<T> injectEntities,
      List<String> newValuesIDs,
      Map<String, T> entitiesFromDB,
      InjectBulkUpdateSupportedOperations operation) {
    if (operation == InjectBulkUpdateSupportedOperations.REPLACE) {
      injectEntities.clear();
    }
    newValuesIDs.forEach(
        id -> {
          T entity = entitiesFromDB.get(id);
          if (entity == null) {
            log.warn("Inject update entity with ID {} not found in the DB", id);
            return;
          }

          switch (operation) {
            case REPLACE, ADD -> {
              if (!injectEntities.contains(entity)) {
                injectEntities.add(entity);
              }
            }
            case REMOVE -> injectEntities.remove(entity);
            default ->
                throw new BadRequestException(
                    "Invalid operation to update inject entities: " + operation);
          }
        });
  }

  public AgentsAndAssetsAgentless getAgentsAndAgentlessAssetsByInject(Inject inject) {
    Set<Agent> agents = new HashSet<>();
    Set<Asset> assetsAgentless = new HashSet<>();

    for (Asset asset : inject.getAssets()) {
      extractAgentsAndAssetsAgentless(agents, assetsAgentless, asset);
    }

    for (AssetGroup assetGroup : inject.getAssetGroups()) {
      for (Asset asset : assetGroupService.assetsFromAssetGroup(assetGroup.getId())) {
        extractAgentsAndAssetsAgentless(agents, assetsAgentless, asset);
      }
    }

    return new AgentsAndAssetsAgentless(agents, assetsAgentless);
  }

  private void extractAgentsAndAssetsAgentless(
      Set<Agent> agents, Set<Asset> assetsAgentless, Asset asset) {
    List<Agent> collectedAgents =
        Optional.ofNullable(((Endpoint) Hibernate.unproxy(asset)).getAgents())
            .orElse(Collections.emptyList());
    if (collectedAgents.isEmpty()) {
      assetsAgentless.add(asset);
    } else {
      for (Agent agent : collectedAgents) {
        if (isPrimaryAgent(agent)) {
          agents.add(agent);
        }
      }
    }
  }

  public List<Agent> getAgentsByInject(Inject inject) {
    List<Agent> agents = new ArrayList<>();
    Set<String> agentIds = new HashSet<>();

    Consumer<Asset> extractAgents =
        asset -> {
          List<Agent> collectedAgents =
              Optional.ofNullable(((Endpoint) Hibernate.unproxy(asset)).getAgents())
                  .orElse(Collections.emptyList());
          for (Agent agent : collectedAgents) {
            if (isPrimaryAgent(agent) && !agentIds.contains(agent.getId())) {
              agents.add(agent);
              agentIds.add(agent.getId());
            }
          }
        };

    new ArrayList<>(inject.getAssets()).forEach(extractAgents);
    inject.getAssetGroups().stream()
        .flatMap(assetGroup -> assetGroupService.assetsFromAssetGroup(assetGroup.getId()).stream())
        .forEach(extractAgents);

    return agents;
  }

  public List<FilterUtilsJpa.Option> getOptionsByNameLinkedToFindings(
      String searchText, String sourceId, Pageable pageable) {
    String trimmedSearchText = StringUtils.trimToNull(searchText);
    String trimmedSimulationOrScenarioId = StringUtils.trimToNull(sourceId);

    List<Object[]> results;

    if (trimmedSimulationOrScenarioId == null) {
      results = injectRepository.findAllByTitleLinkedToFindings(trimmedSearchText, pageable);
    } else {
      results =
          injectRepository.findAllByTitleLinkedToFindingsWithContext(
              trimmedSimulationOrScenarioId, trimmedSearchText, pageable);
    }

    return results.stream()
        .map(i -> new FilterUtilsJpa.Option((String) i[0], (String) i[1]))
        .toList();
  }

  public List<ExecutionTraceOutput> getInjectTracesFromInjectAndTarget(
      final String injectId, final String targetId, final TargetType targetType) {
    switch (targetType) {
      case AGENT:
        return injectStatusMapper.toExecutionTracesOutput(
            this.executionTraceRepository.findByInjectIdAndAgentId(injectId, targetId));
      case ASSETS:
        return injectStatusMapper.toExecutionTracesOutput(
            this.executionTraceRepository.findByInjectIdAndAssetId(injectId, targetId));
      case TEAMS:
        return injectStatusMapper.toExecutionTracesOutput(
            this.executionTraceRepository.findByInjectIdAndTeamId(injectId, targetId));
      case PLAYERS:
        return injectStatusMapper.toExecutionTracesOutput(
            this.executionTraceRepository.findByInjectIdAndPlayerId(injectId, targetId));
      default:
        throw new BadRequestException("Target type " + targetType + " is not supported");
    }
  }

  public InjectStatusOutput getInjectStatusWithGlobalExecutionTraces(String injectId) {
    return injectStatusMapper.toInjectStatusOutput(
        injectStatusRepository.findInjectStatusWithGlobalExecutionTraces(injectId));
  }

  /**
   * Function used to get the targeted property field of a targeted asset.
   *
   * @param injectorContractFields InjectorContract Fields from where to extract the targeted
   *     property
   * @param targetedAssetKey The key of the targeted Asset field
   * @return the object node of targetedProperty field
   */
  private ObjectNode getTargetedPropertyFieldOfTargetedAsset(
      List<ObjectNode> injectorContractFields, String targetedAssetKey) {
    return injectorContractFields.stream()
        .filter(
            f -> f.get("key").asText().startsWith(CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY))
        .filter(
            f -> {
              JsonNode linkedFieldsNode = f.get("linkedFields");
              return linkedFieldsNode != null
                  && linkedFieldsNode.isArray()
                  && StreamSupport.stream(linkedFieldsNode.spliterator(), false)
                      .anyMatch(
                          linkedField -> targetedAssetKey.equals(linkedField.get("key").asText()));
            })
        .findFirst()
        .orElse(null);
  }

  /**
   * Get a map of value (e.g., hostname, seen_ip ) for targeted assets of inject
   *
   * @param inject inject to extract the targeted assets from
   * @return a map where the key is the value of the targeted asset (e.g., hostname, seen_ip) and
   *     the value is the Endpoint object representing the targeted asset
   */
  public Map<String, Endpoint> getValueTargetedAssetMap(Inject inject) {
    Map<String, Endpoint> valueTargetedAssetsMap = new HashMap<>();
    InjectorContract injectorContract = inject.getInjectorContract().orElseThrow();

    JsonNode injectorContractFieldsNode = injectorContract.getConvertedContent().get("fields");
    if (injectorContractFieldsNode == null || !injectorContractFieldsNode.isArray()) {
      return valueTargetedAssetsMap;
    }

    List<ObjectNode> injectorContractFields =
        StreamSupport.stream(injectorContractFieldsNode.spliterator(), false)
            .map(ObjectNode.class::cast)
            .toList();

    // Get all fields of type TargetedAsset
    List<ObjectNode> targetedAssetFields =
        injectorContractFields.stream()
            .filter(
                node ->
                    node.has("type")
                        && ContractFieldType.TargetedAsset.label.equals(node.get("type").asText()))
            .toList();

    targetedAssetFields.forEach(
        f -> {
          // For each targeted asset field, retrieve the values of the targeted assets based on the
          // targeted property
          String keyField = f.get("key").asText();
          Map<String, Endpoint> valuesAssetsMap =
              this.retrieveValuesOfTargetedAssetFromInject(
                  injectorContractFields, inject.getContent(), keyField);
          valueTargetedAssetsMap.putAll(valuesAssetsMap);
        });

    return valueTargetedAssetsMap;
  }

  /**
   * Function used to retrieve the targetedAsset value from an Inject.
   *
   * @param injectorContractContentFields InjectorContract Content fields from which to retrieve all
   *     the fields set on the inject
   * @param injectContent Inject content to obtain the value set on an inject
   * @param targetedAssetKey The targeted asset key for which we want to retrieve values (can have
   *     many assets set on one targeted asset key)
   * @return a map where the key is the value of the targeted asset (e.g., hostname, seen_ip) and
   *     the value is the Endpoint object representing the targeted asset
   */
  public Map<String, Endpoint> retrieveValuesOfTargetedAssetFromInject(
      List<ObjectNode> injectorContractContentFields,
      ObjectNode injectContent,
      String targetedAssetKey) {
    Map<String, Endpoint> valueTargetedAssetsMap = new HashMap<>();
    List<String> assetIds =
        mapper.convertValue(
            injectContent.get(targetedAssetKey), new TypeReference<List<String>>() {});
    List<Endpoint> endpointList = endpointService.endpoints(assetIds);

    ObjectNode targetedPropertiesField =
        getTargetedPropertyFieldOfTargetedAsset(injectorContractContentFields, targetedAssetKey);

    if (targetedPropertiesField == null) {
      throw new BadRequestException(
          "No targeted property field found for key: " + targetedAssetKey);
    }

    String targetedPropertyKey = targetedPropertiesField.get("key").asText();
    String targetedPropertyValue =
        injectContent.has(targetedPropertyKey)
            ? injectContent.get(targetedPropertyKey).asText()
            : targetedPropertiesField.get("defaultValue").get(0).asText();

    ContractTargetedProperty contractTargetedProperty =
        ContractTargetedProperty.valueOf(targetedPropertyValue);

    endpointList.forEach(
        endpoint -> {
          String endpointValue = contractTargetedProperty.toEndpointValue.apply(endpoint);
          valueTargetedAssetsMap.put(endpointValue, endpoint);
        });

    return valueTargetedAssetsMap;
  }

  /**
   * Function used to fetch the detection remediations in a inject based on payload definition.
   *
   * @param injectId
   * @return a list of detection remediations
   */
  public List<DetectionRemediation> fetchDetectionRemediationsByInjectId(String injectId) {
    return payloadRepository.fetchDetectionRemediationsByInjectId(injectId);
  }

  /**
   * Check if a user is granted on an inject. A user can be granteed on an inject if: - The inject
   * is linked to a scenario or simulation that the user has access to - The inject is an atomic
   * testing and the user has access to it
   *
   * @param grantType the grant type to check
   * @return a Specification that checks if the user has access to the inject
   */
  public Specification<Inject> hasGrantAccessForInject(Grant.GRANT_TYPE grantType) {

    User currentUser = userService.currentUser();
    boolean hasCapabilityAccessAssessment =
        currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)
            || currentUser.getCapabilities().contains(Capability.BYPASS);

    return (root, query, cb) -> {
      if (currentUser.isAdmin() || hasCapabilityAccessAssessment) {
        return cb.conjunction();
      }

      // Ensure distinct results (joins/subqueries can otherwise produce duplicates).
      query.distinct(true);

      // Use the id expressions for parents (safer than testing the entity path itself)
      Expression<String> scenarioIdPath = root.get("scenario").get("id");
      Expression<String> exerciseIdPath = root.get("exercise").get("id");
      // Check if both are null -> atomic testing case
      Predicate bothParentsNull = cb.and(cb.isNull(scenarioIdPath), cb.isNull(exerciseIdPath));

      // Get allowed grant types
      List<Grant.GRANT_TYPE> allowedGrantTypes = grantType.andHigher();

      // Create subquery for accessible scenarios
      Subquery<String> accessibleScenarios =
          SpecificationUtils.accessibleResourcesSubquery(
              query,
              cb,
              currentUser.getId(),
              Grant.GRANT_RESOURCE_TYPE.SCENARIO,
              allowedGrantTypes);
      // Create subquery for accessible simulations
      Subquery<String> accessibleSimulations =
          SpecificationUtils.accessibleResourcesSubquery(
              query,
              cb,
              currentUser.getId(),
              Grant.GRANT_RESOURCE_TYPE.SIMULATION,
              allowedGrantTypes);
      // Create subquery for accessible atomic testings
      Subquery<String> accessibleAtomicTestings =
          SpecificationUtils.accessibleResourcesSubquery(
              query,
              cb,
              currentUser.getId(),
              Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING,
              allowedGrantTypes);

      return cb.or(
          // Case 1: atomic test (no parents, direct grant required)
          cb.and(
              cb.isNull(root.get("scenario")),
              cb.isNull(root.get("exercise")),
              root.get("id").in(accessibleAtomicTestings)),
          // Case 2: linked to a scenario, and user has access
          cb.and(
              cb.isNotNull(root.get("scenario")),
              root.get("scenario").get("id").in(accessibleScenarios)),
          // Case 3: linked to a simulation, and user has access
          cb.and(
              cb.isNotNull(root.get("exercise")),
              root.get("exercise").get("id").in(accessibleSimulations)));
    };
  }

  /**
   * Extracts the inject coverage from the scenario's injects, mapping each inject to its set of
   * (AttackPattern × Platform × Architecture) combinations.
   *
   * @param scenario the scenario containing injects
   * @return a map of injects to their AttackPattern-platform-architecture combinations
   */
  public Map<Inject, Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>>
      extractCombinationAttackPatternPlatformArchitecture(Scenario scenario) {

    return scenario.getInjects().stream()
        .map(inject -> inject.getInjectorContract().map(ic -> Map.entry(inject, ic)))
        .flatMap(Optional::stream)
        // Only keep attack patterns that specify both platform and architecture.
        // Other cases should be reviewed depending on the injector contract source: vulnerability,
        // placeholder, other
        .filter(
            entry ->
                entry.getValue().getArch() != null
                    && entry.getValue().getPlatforms() != null
                    && entry.getValue().getPlatforms().length > 0)
        .map(
            entry -> {
              Inject inject = entry.getKey();
              InjectorContract ic = entry.getValue();

              // Extract archs
              Set<String> archs =
                  ALL_ARCHITECTURES.equals(ic.getArch())
                      ? Set.of(arm64.name(), x86_64.name())
                      : Set.of(ic.getArch().name());

              // Extract platforms
              Set<Endpoint.PLATFORM_TYPE> platforms =
                  new HashSet<>(Arrays.asList(ic.getPlatforms()));

              // Generate combinations
              Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> combinations =
                  buildCombinationsAttackPatternPlatformsArchitectures(
                      ic.getAttackPatterns(), platforms, archs);

              return Map.entry(inject, combinations);
            })
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (v1, v2) -> {
                  Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> merged = new HashSet<>(v1);
                  merged.addAll(v2);
                  return v1;
                }));
  }

  /**
   * Builds the complete set of required combinations of TTPs and platform-architecture pairs.
   *
   * @param attackPatterns list of attack patterns (TTPs)
   * @param platforms set of platforms
   * @param architectures set of architecture
   * @return set of (TTP × Platform × Architecture) combinations
   */
  public static Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>
      buildCombinationsAttackPatternPlatformsArchitectures(
          List<AttackPattern> attackPatterns,
          Set<Endpoint.PLATFORM_TYPE> platforms,
          Set<String> architectures) {

    if (attackPatterns == null || platforms == null || architectures == null) {
      return Collections.emptySet();
    }

    return attackPatterns.stream()
        .flatMap(
            attackPattern -> {
              String id = attackPattern.getId();
              return platforms.stream()
                  .flatMap(
                      platform ->
                          architectures.stream()
                              .map(architecture -> Triple.of(id, platform, architecture)));
            })
        .collect(Collectors.toSet());
  }

  /**
   * Retrieve the payload linked to an inject
   *
   * @param injectId to search payload
   * @return found payload
   * @throws ElementNotFoundException if inject or payload is not found
   */
  public Payload getPayloadByInjectId(String injectId) {
    Inject inject = inject(injectId);
    return inject
        .getPayload()
        .orElseThrow(
            () ->
                new ElementNotFoundException("payload not found on inject with id : " + injectId));
  }
}
