package io.openaev.importer;

import static io.openaev.database.specification.InjectorContractSpecification.byPayloadExternalId;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;
import static io.openaev.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static io.openaev.rest.exercise.exports.ExerciseFileExport.EXERCISE_VARIABLES;
import static io.openaev.rest.payload.PayloadUtils.buildPayload;
import static io.openaev.rest.scenario.export.ScenarioFileExport.SCENARIO_VARIABLES;
import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.model.Scenario.SEVERITY;
import io.openaev.database.repository.*;
import io.openaev.injectors.challenge.model.ChallengeContent;
import io.openaev.injectors.channel.model.ChannelContent;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.rest.exercise.exports.VariableWithValueMixin;
import io.openaev.rest.inject.form.InjectDependencyInput;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import io.openaev.rest.injector_contract.InjectorContractMigrationUtils;
import io.openaev.rest.payload.contract_output_element.ContractOutputElementInput;
import io.openaev.rest.payload.form.*;
import io.openaev.rest.payload.output_parser.OutputParserInput;
import io.openaev.rest.payload.regex_group.RegexGroupInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.service.FileService;
import io.openaev.service.ImportEntry;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.WorkflowScopeRuleUtils;
import jakarta.activation.MimetypesFileTypeMap;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class V1_DataImporter implements Importer {

  // region variables
  @Resource protected ObjectMapper mapper;
  private final FileService documentService;
  private final ActionMetricCollector actionMetricCollector;
  private final DocumentRepository documentRepository;
  private final TagRepository tagRepository;
  private final AttackPatternRepository attackPatternRepository;
  private final KillChainPhaseRepository killChainPhaseRepository;
  private final ExerciseRepository exerciseRepository;
  private final ScenarioService scenarioService;
  private final TeamRepository teamRepository;
  private final ObjectiveRepository objectiveRepository;
  private final InjectRepository injectRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final InjectDocumentRepository injectDocumentRepository;
  private final ChallengeRepository challengeRepository;
  private final ChannelRepository channelRepository;
  private final ArticleRepository articleRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;
  private final LessonsQuestionRepository lessonsQuestionRepository;
  private final VariableRepository variableRepository;
  private final InjectDependenciesRepository injectDependenciesRepository;
  private final PayloadCreationService payloadCreationService;
  private final PayloadRepository payloadRepository;
  private final CollectorTypeRepository collectorTypeRepository;
  private final DomainService domainService;
  private final io.openaev.service.chaining.WorkflowService workflowService;
  private final io.openaev.service.chaining.StepService chainingStepService;
  private final io.openaev.service.chaining.ConditionService chainingConditionService;

  private final InjectorContractContentUtils injectorContractContentUtils;

  // endregion

  private String handleInjectContent(
      Map<String, Base> baseIds, String contract, JsonNode injectNode) {
    if (contract == null) {
      return null;
    }
    String content = injectNode.get("inject_content").toString();
    switch (contract) {
      // Challenges exists in exercise only through inject content definition
      // So we need to rewrite content for challenges to remap the challenge ids
      case CHALLENGE_PUBLISH -> {
        try {
          JsonNode jsonNode = mapper.readTree(content);
          ChallengeContent challengeContent = mapper.treeToValue(jsonNode, ChallengeContent.class);
          List<String> remappedIds =
              challengeContent.getChallenges().stream()
                  .map(baseIds::get)
                  .filter(Objects::nonNull)
                  .map(Base::getId)
                  .toList();
          challengeContent.setChallenges(remappedIds);
          content = mapper.writeValueAsString(challengeContent);
        } catch (Exception e) {
          // Error rewriting content, inject can't be created
          return null;
        }
      }
      // Channel articles exists in exercise only through inject content definition
      // So we need to rewrite content for channels to remap the channel ids
      case CHANNEL_PUBLISH -> {
        try {
          JsonNode jsonNode = mapper.readTree(content);
          ChannelContent channelContent = mapper.treeToValue(jsonNode, ChannelContent.class);
          List<String> remappedIds =
              channelContent.getArticles().stream()
                  .map(baseIds::get)
                  .filter(Objects::nonNull)
                  .map(Base::getId)
                  .toList();
          channelContent.setArticles(remappedIds);
          content = mapper.writeValueAsString(channelContent);
        } catch (Exception e) {
          // Error rewriting content, inject can't be created
          return null;
        }
      }
      // For payload injects, remap Document-type argument values so the inject references the
      // newly uploaded document UUID rather than the original export UUID.
      default -> {
        try {
          Optional<InjectorContract> contractOpt = injectorContractRepository.findById(contract);
          if (contractOpt.isEmpty() || contractOpt.get().getPayload() == null) {
            break;
          }
          List<String> docArgKeys =
              contractOpt.get().getPayload().getArguments().stream()
                  .filter(arg -> PrimitiveType.Document == arg.getType())
                  .map(PayloadArgument::getKey)
                  .toList();
          if (docArgKeys.isEmpty()) {
            break;
          }
          ObjectNode contentNode = (ObjectNode) mapper.readTree(content);
          for (String key : docArgKeys) {
            JsonNode valueNode = contentNode.get(key);
            if (valueNode != null && valueNode.isTextual()) {
              Base newBase = baseIds.get(valueNode.asText());
              if (newBase != null) {
                contentNode.put(key, newBase.getId());
              }
            }
          }
          content = mapper.writeValueAsString(contentNode);
        } catch (Exception e) {
          // Error rewriting content, keep original
        }
      }
    }
    return content;
  }

  private Set<Tag> computeTagsCompletion(
      Set<Tag> existingTags, List<String> lookingIds, Map<String, Base> baseIds) {
    Set<Tag> tags = new HashSet<>(existingTags);
    Set<Tag> tagsForOrganization =
        lookingIds.stream().map(baseIds::get).map(Tag.class::cast).collect(Collectors.toSet());
    tags.addAll(tagsForOrganization);
    return tags;
  }

  @Override
  @Transactional
  public void importData(
      JsonNode importNode,
      Map<String, ImportEntry> docReferences,
      Exercise exercise,
      Scenario scenario,
      Asset asset,
      AssetGroup assetGroup,
      String suffix) {
    Map<String, Base> baseIds = new HashMap<>();

    String prefix = "inject_";
    if (importNode.has("exercise_information")) {
      prefix = "exercise_";
    } else if (importNode.has("scenario_information")) {
      prefix = "scenario_";
    } else if (importNode.has("payload_information")) {
      prefix = "payload_";
    }
    importTags(importNode, prefix, baseIds);
    Exercise savedExercise =
        Optional.ofNullable(importExercise(importNode, baseIds, suffix)).orElse(exercise);
    Scenario savedScenario =
        Optional.ofNullable(importScenario(importNode, baseIds, suffix)).orElse(scenario);
    importDocuments(importNode, prefix, docReferences, savedExercise, savedScenario, baseIds);
    importDocument(importNode, prefix, docReferences, savedExercise, savedScenario, baseIds);

    // Should be done after tags & documents
    if (prefix.equals("payload_")) {
      importPayloadAsMain(importNode, baseIds);
    }

    importOrganizations(importNode, prefix, baseIds);
    importUsers(importNode, prefix, baseIds);
    importTeams(importNode, prefix, savedExercise, savedScenario, baseIds);
    importChallenges(importNode, prefix, baseIds);
    importChannels(importNode, prefix, baseIds);
    importArticles(importNode, prefix, savedExercise, savedScenario, baseIds);
    importObjectives(importNode, prefix, savedExercise, savedScenario, baseIds);
    importLessons(importNode, prefix, savedExercise, savedScenario, baseIds);
    // Shared map tracking original → resolved injector contract IDs across inject and workflow
    // import. This prevents duplicate payload creation when exercise_injects and workflow_steps
    // reference the same (missing) injector contract.
    Map<String, String> resolvedContracts = new HashMap<>();
    if (!hasWorkflowImport(importNode, prefix)) {
      importInjects(
          importNode,
          prefix,
          savedExercise,
          savedScenario,
          asset,
          assetGroup,
          baseIds,
          resolvedContracts);
    }
    importVariables(importNode, savedExercise, savedScenario, baseIds);
    importWorkflow(importNode, prefix, savedExercise, savedScenario, baseIds, resolvedContracts);
  }

  private boolean hasWorkflowImport(JsonNode importNode, String prefix) {
    String workflowKey =
        switch (prefix) {
          case "scenario_" -> "scenario_workflow";
          case "exercise_" -> "exercise_workflow";
          default -> null;
        };
    if (workflowKey == null) {
      return false;
    }
    JsonNode workflowNode = importNode.get(workflowKey);
    return workflowNode != null && !workflowNode.isNull() && !workflowNode.isEmpty();
  }

  // -- TAGS --

  private void importTags(JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "tags")
        .forEach(
            nodeTag -> {
              String id = nodeTag.get("tag_id").textValue();
              if (baseIds.get(id) != null) {
                // Already import
                return;
              }
              String name = nodeTag.get("tag_name").textValue();

              List<Tag> existingTags = this.tagRepository.findByNameIgnoreCase(name);
              if (!existingTags.isEmpty()) {
                baseIds.put(id, existingTags.getFirst());
              } else {
                baseIds.put(id, this.tagRepository.save(createTag(nodeTag)));
              }
            });
  }

  private Tag createTag(JsonNode jsonNode) {
    Tag tag = new Tag();
    tag.setName(jsonNode.get("tag_name").textValue());
    tag.setColor(jsonNode.get("tag_color").textValue());
    return tag;
  }

  // -- DOMAINS --
  /**
   * Imports domains from a single JSON node. Does not apply the "ToClassify" fallback — use {@code
   * mergeDomains} when merging from multiple sources with fallback.
   *
   * @param importNode the JSON node to extract domains from
   * @param prefix the field prefix (e.g. "payload_")
   * @param baseIds the shared ID-to-entity mapping used for deduplication and caching
   * @return a list of resolved domains (may be empty if no domains found in the node)
   */
  @VisibleForTesting
  private List<Domain> importDomains(
      JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    List<Domain> domains = new ArrayList<>();
    resolveJsonElements(importNode, prefix + "domains")
        .forEach(
            nodeDomain -> {
              if (nodeDomain == null || nodeDomain.isNull()) {
                return;
              }

              String id =
                  nodeDomain.isTextual()
                      ? nodeDomain.asText()
                      : ofNullable(nodeDomain.get("domain_id"))
                          .map(JsonNode::textValue)
                          .orElse(null);
              if (!hasText(id)) {
                return;
              }

              if (baseIds.get(id) != null) {
                domains.add((Domain) baseIds.get(id));
                return;
              }

              Optional<Domain> existingDomain = this.domainService.findOptionalById(id);
              if (existingDomain.isPresent()) {
                baseIds.put(id, existingDomain.get());
                domains.add(existingDomain.get());
              } else {
                if (nodeDomain.isTextual()) {
                  return;
                }
                Domain createdDomain =
                    this.domainService.upsert(
                        nodeDomain.get("domain_name").textValue(),
                        nodeDomain.get("domain_color").textValue(),
                        new Tenant(TenantContext.getCurrentTenant()));
                baseIds.put(id, createdDomain);
                domains.add(createdDomain);
              }
            });

    return domains;
  }

  // -- ATTACK PATTERN --
  private List<AttackPattern> importAttackPattern(
      JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    ArrayList<AttackPattern> attackPatterns = new ArrayList<>();
    resolveJsonElements(importNode, prefix + "attack_patterns")
        .forEach(
            nodeAttackPattern -> {
              JsonNode idNode = nodeAttackPattern.get("attack_pattern_id");
              if (idNode == null) {
                return;
              }
              String id = idNode.textValue();

              if (baseIds.get(id) != null) {
                attackPatterns.add((AttackPattern) baseIds.get(id));
                return;
              }
              String name = nodeAttackPattern.get("attack_pattern_external_id").textValue();

              List<AttackPattern> existingAttackPattern =
                  this.attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(
                      List.of(name), TenantContext.getCurrentTenant());
              if (!existingAttackPattern.isEmpty()) {
                baseIds.put(id, existingAttackPattern.getFirst());
                attackPatterns.add(existingAttackPattern.getFirst());
              } else {
                AttackPattern attackPatternCreated =
                    this.attackPatternRepository.save(
                        createAttackPattern(
                            nodeAttackPattern,
                            importKillChainPhase(nodeAttackPattern, "attack_pattern_", baseIds)));
                baseIds.put(id, attackPatternCreated);
                attackPatterns.add(attackPatternCreated);
              }
            });
    return attackPatterns;
  }

  // -- MERGE HELPERS (tags, domains, attack patterns from two nodes) --

  /**
   * Resolves and merges tags from two JSON nodes using their respective field keys.
   *
   * @param baseIds shared ID-to-entity mapping
   * @param node1 first JSON node (may be {@code null})
   * @param key1 tag field key for the first node (e.g. "payload_tags")
   * @param node2 second JSON node (may be {@code null})
   * @param key2 tag field key for the second node (e.g. "injector_contract_tags")
   * @return a deduplicated set of resolved tags
   */
  private Set<Tag> mergeTagIds(
      Map<String, Base> baseIds,
      JsonNode node1,
      String key1,
      @Nullable JsonNode node2,
      @Nullable String key2) {
    Set<Tag> tags = new LinkedHashSet<>();
    resolveJsonIds(node1, key1).stream()
        .map(baseIds::get)
        .filter(Objects::nonNull)
        .map(Tag.class::cast)
        .forEach(tags::add);
    if (node2 != null) {
      resolveJsonIds(node2, key2).stream()
          .map(baseIds::get)
          .filter(Objects::nonNull)
          .map(Tag.class::cast)
          .forEach(tags::add);
    }
    return tags;
  }

  /**
   * Imports and merges domains from two JSON nodes. Falls back to the "ToClassify" domain if no
   * domain is found from either source.
   *
   * @param baseIds shared ID-to-entity mapping
   * @param node1 first JSON node (may be {@code null})
   * @param prefix1 field prefix for the first node (e.g. "payload_")
   * @param node2 second JSON node (may be {@code null})
   * @param prefix2 field prefix for the second node (e.g. "injector_contract_")
   * @return a deduplicated set of resolved domains, never empty
   */
  protected Set<Domain> mergeDomains(
      Map<String, Base> baseIds,
      JsonNode node1,
      String prefix1,
      @Nullable JsonNode node2,
      @Nullable String prefix2) {
    Set<Domain> domains = new LinkedHashSet<>(importDomains(node1, prefix1, baseIds));
    if (node2 != null) {
      domains.addAll(importDomains(node2, prefix2, baseIds));
    }
    if (domains.isEmpty()) {
      domains.add(
          domainService.findOptionalByName(PresetDomain.getToClassify().getName()).orElseThrow());
    }
    return domains;
  }

  /**
   * Imports and merges attack patterns from two JSON nodes.
   *
   * @param baseIds shared ID-to-entity mapping
   * @param node1 first JSON node (may be {@code null})
   * @param prefix1 field prefix for the first node (e.g. "payload_")
   * @param node2 second JSON node (may be {@code null})
   * @param prefix2 field prefix for the second node (e.g. "injector_contract_")
   * @return a deduplicated set of resolved attack patterns
   */
  private Set<AttackPattern> mergeAttackPatterns(
      Map<String, Base> baseIds,
      JsonNode node1,
      String prefix1,
      @Nullable JsonNode node2,
      @Nullable String prefix2) {
    Set<AttackPattern> patterns = new LinkedHashSet<>(importAttackPattern(node1, prefix1, baseIds));
    if (node2 != null) {
      patterns.addAll(importAttackPattern(node2, prefix2, baseIds));
    }
    return patterns;
  }

  private AttackPattern createAttackPattern(
      JsonNode jsonNode, List<KillChainPhase> killChainPhases) {
    AttackPattern attackPattern = new AttackPattern();
    attackPattern.setStixId("attack-pattern--" + UUID.randomUUID());
    attackPattern.setName(jsonNode.get("attack_pattern_name").textValue());
    attackPattern.setDescription(jsonNode.get("attack_pattern_description").textValue());
    attackPattern.setExternalId(jsonNode.get("attack_pattern_external_id").textValue());
    attackPattern.setKillChainPhases(killChainPhases);
    return attackPattern;
  }

  private List<KillChainPhase> importKillChainPhase(
      JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    List<KillChainPhase> killChainPhases = new ArrayList<>();
    resolveJsonElements(importNode, prefix + "kill_chain_phases")
        .forEach(
            nodeKillChainPhase -> {
              JsonNode idNode = nodeKillChainPhase.get("phase_external_id");
              if (idNode == null) {
                return;
              }
              String id = idNode.textValue();

              if (baseIds.get(id) != null) {
                // Already imported
                return;
              }
              String name = nodeKillChainPhase.get("phase_external_id").textValue();

              List<KillChainPhase> existingKillChainPhases =
                  this.killChainPhaseRepository.findAllByExternalIdInIgnoreCase(List.of(name));
              if (!existingKillChainPhases.isEmpty()) {
                baseIds.put(id, existingKillChainPhases.getFirst());
                killChainPhases.add(existingKillChainPhases.getFirst());
              } else {
                KillChainPhase killChainPhaseCreated =
                    this.killChainPhaseRepository.save(createKillChainPhase(nodeKillChainPhase));
                baseIds.put(id, killChainPhaseCreated);
                killChainPhases.add(killChainPhaseCreated);
              }
            });
    return killChainPhases;
  }

  private KillChainPhase createKillChainPhase(JsonNode killChainPhaseNode) {
    KillChainPhase killChainPhase = new KillChainPhase();
    killChainPhase.setKillChainName(killChainPhaseNode.get("phase_kill_chain_name").textValue());
    killChainPhase.setShortName(killChainPhaseNode.get("phase_shortname").textValue());
    killChainPhase.setDescription(killChainPhaseNode.get("phase_description").textValue());
    killChainPhase.setName(killChainPhaseNode.get("phase_name").textValue());
    killChainPhase.setStixId(killChainPhaseNode.get("phase_stix_id").textValue());
    killChainPhase.setExternalId(killChainPhaseNode.get("phase_external_id").textValue());
    killChainPhase.setOrder(killChainPhaseNode.get("phase_order").asLong());
    return killChainPhase;
  }

  // -- EXERCISE --

  private Exercise importExercise(JsonNode importNode, Map<String, Base> baseIds, String suffix) {
    JsonNode exerciseNode = importNode.get("exercise_information");
    if (exerciseNode == null) {
      return null;
    }

    Exercise exercise = new Exercise();
    exercise.setName(exerciseNode.get("exercise_name").textValue() + suffix);
    exercise.setDescription(exerciseNode.get("exercise_description").textValue());
    exercise.setSubtitle(exerciseNode.get("exercise_subtitle").textValue());
    ofNullable(exerciseNode.get("exercise_category"))
        .map(JsonNode::textValue)
        .ifPresent(exercise::setCategory);
    ofNullable(exerciseNode.get("exercise_main_focus"))
        .map(JsonNode::textValue)
        .ifPresent(exercise::setMainFocus);
    ofNullable(exerciseNode.get("exercise_severity"))
        .map(JsonNode::textValue)
        .ifPresent(severity -> exercise.setSeverity(SEVERITY.valueOf(severity)));
    exercise.setHeader(exerciseNode.get("exercise_message_header").textValue());
    exercise.setFooter(exerciseNode.get("exercise_message_footer").textValue());
    exercise.setFrom(exerciseNode.get("exercise_mail_from").textValue());
    exercise.setTags(
        resolveJsonIds(exerciseNode, "exercise_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));
    actionMetricCollector.addSimulationCreatedCount();
    return exerciseRepository.save(exercise);
  }

  // -- SCENARIO --

  private Scenario importScenario(JsonNode importNode, Map<String, Base> baseIds, String suffix) {
    JsonNode scenarioNode = importNode.get("scenario_information");
    if (scenarioNode == null) {
      return null;
    }

    Scenario scenario = new Scenario();
    scenario.setName(scenarioNode.get("scenario_name").textValue() + suffix);
    scenario.setDescription(scenarioNode.get("scenario_description").textValue());
    scenario.setSubtitle(scenarioNode.get("scenario_subtitle").textValue());
    scenario.setCategory(scenarioNode.get("scenario_category").textValue());
    scenario.setMainFocus(scenarioNode.get("scenario_main_focus").textValue());
    ofNullable(scenarioNode.get("scenario_severity"))
        .map(JsonNode::textValue)
        .ifPresent(severity -> scenario.setSeverity(SEVERITY.valueOf(severity)));
    ofNullable(scenarioNode.get("scenario_recurrence"))
        .map(JsonNode::textValue)
        .ifPresent(scenario::setRecurrence);
    ofNullable(scenarioNode.get("scenario_recurrence_start"))
        .map(JsonNode::textValue)
        .ifPresent(recurrenceStart -> scenario.setRecurrenceStart(Instant.parse(recurrenceStart)));
    ofNullable(scenarioNode.get("scenario_recurrence_end"))
        .map(JsonNode::textValue)
        .ifPresent(recurrenceEnd -> scenario.setRecurrenceEnd(Instant.parse(recurrenceEnd)));
    scenario.setHeader(scenarioNode.get("scenario_message_header").textValue());
    scenario.setFooter(scenarioNode.get("scenario_message_footer").textValue());
    scenario.setFrom(scenarioNode.get("scenario_mail_from").textValue());
    scenario.setTags(
        resolveJsonIds(scenarioNode, "scenario_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));
    scenario.setDependencies(
        ofNullable(scenarioNode.get("scenario_dependencies"))
            .filter(JsonNode::isArray)
            .map(
                dependencies ->
                    StreamSupport.stream(dependencies.spliterator(), false)
                        .map(node -> Scenario.Dependency.valueOf(node.textValue()))
                        .toArray(Scenario.Dependency[]::new))
            .orElse(new Scenario.Dependency[0]));

    return scenarioService.createScenario(scenario);
  }

  private void importDocuments(
      JsonNode importNode,
      String prefix,
      Map<String, ImportEntry> docReferences,
      Exercise savedExercise,
      Scenario savedScenario,
      Map<String, Base> baseIds) {
    Stream<JsonNode> documentsStream = resolveJsonElements(importNode, prefix + "documents");
    documentsStream.forEach(
        nodeDoc -> {
          String target = nodeDoc.get("document_target").textValue();
          ImportEntry entry = docReferences.get(target);

          if (entry != null) {
            handleDocumentWithEntry(nodeDoc, entry, target, savedExercise, savedScenario, baseIds);
          }
        });
    // Handle argument documents
    Stream<JsonNode> argumentDcumentsStream =
        resolveJsonElements(importNode, prefix + "arguments_documents");
    argumentDcumentsStream.forEach(
        nodeDoc -> {
          String target = nodeDoc.get("document_target").textValue();
          ImportEntry entry = docReferences.get(target);

          if (entry != null) {
            handleDocumentWithEntry(nodeDoc, entry, target, savedExercise, savedScenario, baseIds);
          }
        });
  }

  private void importDocument(
      JsonNode importNode,
      String prefix,
      Map<String, ImportEntry> docReferences,
      Exercise savedExercise,
      Scenario savedScenario,
      Map<String, Base> baseIds) {

    if (importNode == null) {
      return;
    }

    JsonNode nodeDoc = importNode.path(prefix + "document");
    String target = nodeDoc.path("document_target").textValue();

    if (target != null) {
      ImportEntry entry = docReferences.get(target);
      if (entry != null) {
        handleDocumentWithEntry(nodeDoc, entry, target, savedExercise, savedScenario, baseIds);
      }
    }
  }

  private void handleDocumentWithEntry(
      JsonNode nodeDoc,
      ImportEntry entry,
      String target,
      Exercise savedExercise,
      Scenario savedScenario,
      Map<String, Base> baseIds) {
    String contentType = new MimetypesFileTypeMap().getContentType(entry.getEntry().getName());
    Optional<Document> targetDocument = this.documentRepository.findByTarget(target);

    if (targetDocument.isPresent()) {
      updateExistingDocument(nodeDoc, targetDocument.get(), savedExercise, savedScenario, baseIds);
    } else {
      uploadNewDocument(nodeDoc, entry, target, savedExercise, savedScenario, contentType, baseIds);
    }
  }

  private void updateExistingDocument(
      JsonNode nodeDoc,
      Document document,
      Exercise savedExercise,
      Scenario savedScenario,
      Map<String, Base> baseIds) {
    if (savedExercise != null) {
      Set<Exercise> exercises = new HashSet<>(document.getExercises());
      exercises.add(savedExercise);
      document.setExercises(exercises);
    } else if (savedScenario != null) {
      Set<Scenario> scenarios = new HashSet<>(document.getScenarios());
      scenarios.add(savedScenario);
      document.setScenarios(scenarios);
    }
    document.setTags(
        computeTagsCompletion(
            document.getTags(), resolveJsonIds(nodeDoc, "document_tags"), baseIds));
    Document savedDocument = this.documentRepository.save(document);
    baseIds.put(nodeDoc.get("document_id").textValue(), savedDocument);
  }

  private void uploadNewDocument(
      JsonNode nodeDoc,
      ImportEntry entry,
      String target,
      Exercise savedExercise,
      Scenario savedScenario,
      String contentType,
      Map<String, Base> baseIds) {
    try {
      this.documentService.uploadFile(
          target, entry.getData(), entry.getContentLength(), contentType);
    } catch (Exception e) {
      throw new ImportException(e);
    }

    Document document = new Document();
    document.setTarget(target);
    document.setName(nodeDoc.get("document_name").textValue());
    document.setDescription(nodeDoc.get("document_description").textValue());
    if (savedExercise != null) {
      document.setExercises(new HashSet<>(Set.of(savedExercise)));
    } else if (savedScenario != null) {
      document.setScenarios(new HashSet<>(Set.of(savedScenario)));
    }
    // need to get real database-bound ids for tags
    List<String> tagIds =
        resolveJsonIds(nodeDoc, "document_tags").stream()
            .filter(baseIds::containsKey)
            .map(tid -> baseIds.get(tid).getId())
            .toList();
    document.setTags(iterableToSet(tagRepository.findAllById(tagIds)));
    document.setType(contentType);
    Document savedDocument = this.documentRepository.save(document);
    baseIds.put(nodeDoc.get("document_id").textValue(), savedDocument);
  }

  // -- ORGANIZATION --

  private void importOrganizations(JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "organizations")
        .forEach(
            nodeOrganization -> {
              String id = nodeOrganization.get("organization_id").textValue();
              if (baseIds.get(id) != null) {
                // Already import
                return;
              }
              String name = nodeOrganization.get("organization_name").textValue();

              List<Organization> existingOrganizations =
                  this.organizationRepository.findByNameIgnoreCase(name);

              if (!existingOrganizations.isEmpty()) {
                baseIds.put(id, existingOrganizations.getFirst());
              } else {
                baseIds.put(
                    id,
                    this.organizationRepository.save(
                        createOrganization(nodeOrganization, baseIds)));
              }
            });
  }

  private Organization createOrganization(JsonNode importNode, Map<String, Base> baseIds) {
    Organization organization = new Organization();
    organization.setName(importNode.get("organization_name").textValue());
    organization.setDescription(getNodeValue(importNode.get("organization_description")));
    organization.setTags(
        resolveJsonIds(importNode, "organization_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));
    return organization;
  }

  // -- USERS --

  private void importUsers(JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "users")
        .forEach(
            nodeUser -> {
              String id = nodeUser.get("user_id").textValue();
              if (baseIds.get(id) != null) {
                // Already import
                return;
              }
              String email = nodeUser.get("user_email").textValue();

              User existingUser = this.userRepository.findByEmailIgnoreCase(email).orElse(null);

              baseIds.put(
                  id,
                  Objects.requireNonNullElseGet(
                      existingUser, () -> this.userRepository.save(createUser(nodeUser, baseIds))));
            });
  }

  private User createUser(JsonNode jsonNode, Map<String, Base> baseIds) {
    User user = new User();
    user.setEmail(jsonNode.get("user_email").textValue());
    user.setFirstname(jsonNode.get("user_firstname").textValue());
    user.setLastname(jsonNode.get("user_lastname").textValue());
    user.setLang(getNodeValue(jsonNode.get("user_lang")));
    user.setPhone(getNodeValue(jsonNode.get("user_phone")));
    user.setPgpKey(getNodeValue(jsonNode.get("user_pgp_key")));
    user.setCountry(getNodeValue(jsonNode.get("user_country")));
    user.setCity(getNodeValue(jsonNode.get("user_city")));
    Base userOrganization = baseIds.get(jsonNode.get("user_organization").textValue());
    if (userOrganization != null) {
      user.setOrganization((Organization) userOrganization);
    }
    user.setTags(
        resolveJsonIds(jsonNode, "user_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));
    return user;
  }

  // -- TEAMS --

  private void importTeams(
      JsonNode importNode,
      String prefix,
      Exercise savedExercise,
      Scenario savedScenario,
      Map<String, Base> baseIds) {
    Map<String, Team> baseTeams =
        handlingTeams(importNode, prefix, baseIds, savedExercise, savedScenario);
    baseTeams
        .values()
        .forEach(
            (team) -> {
              if (savedExercise != null) {
                Set<Exercise> exercises = new HashSet<>(team.getExercises());
                exercises.add(savedExercise);
                team.setExercises(exercises.stream().toList());
              } else if (savedScenario != null) {
                Set<Scenario> scenarios = new HashSet<>(team.getScenarios());
                scenarios.add(savedScenario);
                team.setScenarios(scenarios.stream().toList());
              }
            });
    baseIds.putAll(baseTeams);
  }

  private Map<String, Team> handlingTeams(
      JsonNode importNode,
      String prefix,
      Map<String, Base> baseIds,
      Exercise savedExercise,
      Scenario savedScenario) {
    Map<String, Team> baseTeams = new HashMap<>();

    resolveJsonElements(importNode, prefix + "teams")
        .forEach(
            nodeTeam -> {
              String id = nodeTeam.get("team_id").textValue();
              if (baseIds.get(id) != null) {
                // Already import
                return;
              }
              String name = nodeTeam.get("team_name").textValue();

              // Prevent duplication of team, based on the team name and not contextual
              List<Team> existingTeams =
                  this.teamRepository.findByNameIgnoreCaseAndNotContextual(name);

              if (!existingTeams.isEmpty()) {
                baseTeams.put(id, existingTeams.getFirst());
              } else {
                // skip creating contextual team if atomic testing
                if (nodeTeam.has("team_contextual")) {
                  boolean isContextual = nodeTeam.get("team_contextual").booleanValue();
                  if (isContextual && savedExercise == null && savedScenario == null) {
                    return;
                  }
                }

                Team team = createTeam(nodeTeam, baseIds);
                // Tags
                List<String> teamTagIds = resolveJsonIds(nodeTeam, "team_tags");
                Set<Tag> tagsForTeam =
                    teamTagIds.stream()
                        .map(baseIds::get)
                        .filter(Objects::nonNull)
                        .map(Tag.class::cast)
                        .collect(Collectors.toSet());
                team.setTags(tagsForTeam);
                // Users
                List<String> teamUserIds = resolveJsonIds(nodeTeam, "team_users");
                List<User> usersForTeam =
                    teamUserIds.stream()
                        .map(baseIds::get)
                        .filter(Objects::nonNull)
                        .map(User.class::cast)
                        .toList();
                team.setUsers(usersForTeam);
                Team savedTeam = this.teamRepository.save(team);
                baseTeams.put(id, savedTeam);
              }
            });
    return baseTeams;
  }

  private Team createTeam(JsonNode jsonNode, Map<String, Base> baseIds) {
    Team team = new Team();
    team.setName(jsonNode.get("team_name").textValue());
    team.setDescription(jsonNode.get("team_description").textValue());
    if (jsonNode.get("team_organization") != null) {
      Base teamOrganization = baseIds.get(jsonNode.get("team_organization").textValue());
      if (teamOrganization != null) {
        team.setOrganization((Organization) teamOrganization);
      }
    }
    if (jsonNode.has("team_contextual")) {
      team.setContextual(jsonNode.get("team_contextual").booleanValue());
    }
    return team;
  }

  // -- CHALLENGES --

  private void importChallenges(JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "challenges")
        .forEach(
            nodeChallenge -> {
              String id = nodeChallenge.get("challenge_id").textValue();
              if (baseIds.get(id) != null) {
                // Already import
                return;
              }
              String name = nodeChallenge.get("challenge_name").textValue();

              List<Challenge> existingChallenges =
                  this.challengeRepository.findByNameIgnoreCase(name);
              if (!existingChallenges.isEmpty()) {
                baseIds.put(id, existingChallenges.getFirst());
              } else {
                baseIds.put(
                    id, this.challengeRepository.save(createChallenge(nodeChallenge, baseIds)));
              }
            });
  }

  private Challenge createChallenge(JsonNode nodeChallenge, Map<String, Base> baseIds) {
    Challenge challenge = new Challenge();
    challenge.setName(nodeChallenge.get("challenge_name").textValue());
    challenge.setCategory(nodeChallenge.get("challenge_category").textValue());
    challenge.setContent(nodeChallenge.get("challenge_content").textValue());
    challenge.setScore(nodeChallenge.get("challenge_score").asDouble(0.0));
    challenge.setMaxAttempts(nodeChallenge.get("challenge_max_attempts").asInt(0));
    challenge.setDocuments(
        resolveJsonIds(nodeChallenge, "challenge_documents").stream()
            .map(docId -> (Document) baseIds.get(docId))
            .filter(Objects::nonNull)
            .toList());
    challenge.setFlags(
        resolveJsonElements(nodeChallenge, "challenge_flags")
            .map(node -> this.createChallengeFlag(node, challenge))
            .toList());
    challenge.setTags(
        resolveJsonIds(nodeChallenge, "challenge_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));

    return challenge;
  }

  private ChallengeFlag createChallengeFlag(JsonNode flagNode, Challenge challenge) {
    ChallengeFlag flag = new ChallengeFlag();
    flag.setValue(flagNode.get("flag_value").textValue());
    flag.setType(ChallengeFlag.FLAG_TYPE.valueOf(flagNode.get("flag_type").textValue()));
    flag.setChallenge(challenge);
    return flag;
  }

  // -- CHANNELS --

  private void importChannels(JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "channels")
        .forEach(
            nodeChannel -> {
              String id = nodeChannel.get("channel_id").textValue();
              if (baseIds.get(id) != null) {
                // Already import
                return;
              }
              String channelName = nodeChannel.get("channel_name").textValue();

              List<Channel> existingChannels =
                  this.channelRepository.findByNameIgnoreCase(channelName);
              if (!existingChannels.isEmpty()) {
                baseIds.put(id, existingChannels.getFirst());
              } else {
                baseIds.put(id, this.channelRepository.save(createChannel(nodeChannel, baseIds)));
              }
            });
  }

  private Channel createChannel(JsonNode nodeChannel, Map<String, Base> baseIds) {
    Channel channel = new Channel();
    channel.setName(nodeChannel.get("channel_name").textValue());
    channel.setType(nodeChannel.get("channel_type").textValue());
    channel.setDescription(nodeChannel.get("channel_description").textValue());
    channel.setMode(nodeChannel.get("channel_mode").textValue());
    channel.setPrimaryColorDark(nodeChannel.get("channel_primary_color_dark").textValue());
    channel.setPrimaryColorLight(nodeChannel.get("channel_primary_color_light").textValue());
    channel.setSecondaryColorDark(nodeChannel.get("channel_secondary_color_dark").textValue());
    channel.setSecondaryColorLight(nodeChannel.get("channel_secondary_color_light").textValue());

    String channelLogoDark = nodeChannel.get("channel_logo_dark").textValue();
    if (channelLogoDark != null) {
      channel.setLogoDark((Document) baseIds.get(channelLogoDark));
    }
    String channelLogoLight = nodeChannel.get("channel_logo_light").textValue();
    if (channelLogoLight != null) {
      channel.setLogoLight((Document) baseIds.get(channelLogoLight));
    }

    return channel;
  }

  private void importArticles(
      JsonNode importNode,
      String prefix,
      Exercise savedExercise,
      Scenario savedScenario,
      Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "articles")
        .forEach(
            nodeArticle -> {
              String id = nodeArticle.get("article_id").textValue();
              Article article = createArticle(nodeArticle, savedExercise, savedScenario, baseIds);
              baseIds.put(id, this.articleRepository.save(article));
            });
  }

  private Article createArticle(
      JsonNode nodeArticle,
      Exercise savedExercise,
      Scenario savedScenario,
      Map<String, Base> baseIds) {
    Article article = new Article();
    article.setName(nodeArticle.get("article_name").textValue());
    article.setContent(nodeArticle.get("article_content").textValue());
    article.setAuthor(nodeArticle.get("article_author").textValue());
    article.setShares(nodeArticle.get("article_shares").intValue());
    article.setLikes(nodeArticle.get("article_likes").intValue());
    article.setComments(nodeArticle.get("article_comments").intValue());
    if (savedExercise != null) {
      article.setExercise(savedExercise);
    } else if (savedScenario != null) {
      article.setScenario(savedScenario);
    }
    article.setDocuments(
        resolveJsonIds(nodeArticle, "article_documents").stream()
            .map(docId -> (Document) baseIds.get(docId))
            .filter(Objects::nonNull)
            .toList());
    article.setChannel((Channel) baseIds.get(nodeArticle.get("article_channel").textValue()));

    return article;
  }

  private void importObjectives(
      JsonNode importNode,
      String prefix,
      Exercise savedExercise,
      Scenario savedScenario,
      Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "objectives")
        .forEach(
            nodeObjective -> {
              String id = nodeObjective.get("objective_id").textValue();
              Objective objective = createObjective(nodeObjective, savedExercise, savedScenario);
              baseIds.put(id, this.objectiveRepository.save(objective));
            });
  }

  private Objective createObjective(
      JsonNode nodeObjective, Exercise savedExercise, Scenario savedScenario) {
    Objective objective = new Objective();
    objective.setTitle(nodeObjective.get("objective_title").textValue());
    objective.setDescription(nodeObjective.get("objective_description").textValue());
    objective.setPriority((short) nodeObjective.get("objective_priority").asInt(0));
    if (savedExercise != null) {
      objective.setExercise(savedExercise);
    } else if (savedScenario != null) {
      objective.setScenario(savedScenario);
    }

    return objective;
  }

  private void importLessons(
      JsonNode importNode,
      String prefix,
      Exercise savedExercise,
      Scenario savedScenario,
      Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "lessons_categories")
        .forEach(
            nodeLessonCategory -> {
              String id = nodeLessonCategory.get("lessonscategory_id").textValue();
              LessonsCategory lessonsCategory =
                  createLessonsCategory(nodeLessonCategory, savedExercise, savedScenario, baseIds);
              baseIds.put(id, this.lessonsCategoryRepository.save(lessonsCategory));
            });
    resolveJsonElements(importNode, prefix + "lessons_questions")
        .forEach(
            nodeLessonQuestion -> {
              String id = nodeLessonQuestion.get("lessonsquestion_id").textValue();
              LessonsQuestion lessonsQuestion = createLessonsQuestion(nodeLessonQuestion, baseIds);
              baseIds.put(id, this.lessonsQuestionRepository.save(lessonsQuestion));
            });
  }

  private LessonsCategory createLessonsCategory(
      JsonNode nodeLessonCategory,
      Exercise savedExercise,
      Scenario savedScenario,
      Map<String, Base> baseIds) {
    LessonsCategory lessonsCategory = new LessonsCategory();
    lessonsCategory.setName(nodeLessonCategory.get("lessons_category_name").textValue());
    lessonsCategory.setDescription(
        nodeLessonCategory.get("lessons_category_description").textValue());
    lessonsCategory.setOrder(nodeLessonCategory.get("lessons_category_order").intValue());
    if (savedExercise != null) {
      lessonsCategory.setExercise(savedExercise);
    } else if (savedScenario != null) {
      lessonsCategory.setScenario(savedScenario);
    }
    lessonsCategory.setTeams(
        resolveJsonIds(nodeLessonCategory, "lessons_category_teams").stream()
            .map(teamId -> (Team) baseIds.get(teamId))
            .filter(Objects::nonNull)
            .toList());

    return lessonsCategory;
  }

  private LessonsQuestion createLessonsQuestion(
      JsonNode nodeLessonQuestion, Map<String, Base> baseIds) {
    LessonsQuestion lessonsQuestion = new LessonsQuestion();
    lessonsQuestion.setContent(nodeLessonQuestion.get("lessons_question_content").textValue());
    lessonsQuestion.setExplanation(
        nodeLessonQuestion.get("lessons_question_explanation").textValue());
    lessonsQuestion.setOrder(nodeLessonQuestion.get("lessons_question_order").intValue());
    lessonsQuestion.setCategory(
        (LessonsCategory)
            baseIds.get(nodeLessonQuestion.get("lessons_question_category").textValue()));
    String categoryId = nodeLessonQuestion.get("lessons_question_category").asText();
    LessonsCategory lessonsCategory = (LessonsCategory) baseIds.get(categoryId);
    lessonsQuestion.setCategory(lessonsCategory);

    return lessonsQuestion;
  }

  private void importInjects(
      JsonNode importNode,
      String prefix,
      Exercise savedExercise,
      Scenario savedScenario,
      Asset asset,
      AssetGroup assetGroup,
      Map<String, Base> baseIds,
      Map<String, String> resolvedContracts) {
    Supplier<Stream<JsonNode>> injectsStream =
        () ->
            importNode.has(prefix + "injects")
                ? resolveJsonElements(importNode, prefix + "injects")
                : Objects.equals(prefix, "inject_")
                    ? resolveJsonElements(importNode, prefix + "information")
                    : Stream.of();

    // Getting a list of all the children of the dependency
    List<String> children =
        injectsStream
            .get()
            .flatMap(
                jsonNode -> {
                  // List of dependencies of the inject
                  List<JsonNode> dependsOn =
                      StreamSupport.stream(jsonNode.get("inject_depends_on").spliterator(), false)
                          .toList();

                  // We return a stream containing all the children of the dependencies of the
                  // inject
                  return dependsOn.stream()
                      .map(
                          dependency ->
                              dependency
                                  .get("dependency_relationship")
                                  .get("inject_children_id")
                                  .asText());
                })
            .toList();

    // Getting a list of all the injects that have no parents
    Stream<JsonNode> injectsNoParent =
        injectsStream
            .get()
            .filter(jsonNode -> !children.contains(jsonNode.get("inject_id").asText()));

    importInjects(
        baseIds,
        savedExercise,
        savedScenario,
        asset,
        assetGroup,
        injectsNoParent.toList(),
        injectsStream.get().toList(),
        resolvedContracts);
  }

  private void importInjects(
      Map<String, Base> baseIds,
      Exercise exercise,
      Scenario scenario,
      Asset asset,
      AssetGroup assetGroup,
      List<JsonNode> injectsToAdd,
      List<JsonNode> allInjects,
      Map<String, String> resolvedContracts) {
    List<String> originalIds = new ArrayList<>();
    injectsToAdd.forEach(
        injectNode -> {
          String injectId = UUID.randomUUID().toString();
          String id = injectNode.get("inject_id").textValue();
          String title = injectNode.get("inject_title").textValue();
          String description = injectNode.get("inject_description").textValue();
          String country = injectNode.get("inject_country").textValue();
          String city = injectNode.get("inject_city").textValue();
          boolean enabled =
              ofNullable(injectNode.get("inject_enabled")).map(JsonNode::booleanValue).orElse(true);
          String injectorContractIdFromNode = null;
          JsonNode injectContractNode = injectNode.get("inject_injector_contract");
          if (injectContractNode != null && !injectContractNode.isNull()) {
            injectorContractIdFromNode = injectContractNode.get("injector_contract_id").textValue();
          }

          // Check If inject contract exists
          if (injectorContractIdFromNode == null) {
            log.warn("Import Inject Failed: Missing injector contract ID on inject: {}", injectId);
            return;
          }
          Optional<InjectorContract> injectorContract =
              this.injectorContractRepository.findById(injectorContractIdFromNode);

          String injectorContractId;

          if (injectorContract.isPresent()) {
            injectorContractId = injectorContract.get().getId();
          } else {
            injectorContractId = resolveInjectorContract(injectContractNode, baseIds);
          }

          // Record the mapping so importWorkflowSteps can reuse the resolved contract
          if (injectorContractId != null
              && !injectorContractIdFromNode.equals(injectorContractId)) {
            resolvedContracts.put(injectorContractIdFromNode, injectorContractId);
          }

          if (injectorContractId == null) {
            if (scenario != null
                && scenario.getDependencies() != null
                && Arrays.asList(scenario.getDependencies())
                    .contains(Scenario.Dependency.STARTERPACK)) {
              // if we are importing the starter pack, we will create the injector contract so the
              // injects are created before the injector registered
              // once the injector register the contract will be overriden and will be the one
              // provided by the injector
              Payload createdPayload = injectorContract.map(ic -> ic.getPayload()).orElse(null);
              injectorContractId =
                  importInjectorContractFromStarterPack(injectContractNode, createdPayload, baseIds)
                      .getId();
            } else {
              log.warn(
                  "Import Inject Failed: Unresolved injector contract ID on inject: {}", injectId);
            }
          }

          // If contract is not know, inject can't be imported
          String content = handleInjectContent(baseIds, injectorContractId, injectNode);
          Long dependsDuration = injectNode.get("inject_depends_duration").asLong();
          boolean allTeams = injectNode.get("inject_all_teams").booleanValue();
          if (exercise != null) {
            injectRepository.importSaveForExercise(
                injectId,
                title,
                description,
                country,
                city,
                injectorContractId,
                allTeams,
                enabled,
                exercise.getId(),
                dependsDuration,
                content);
          } else if (scenario != null) {
            injectRepository.importSaveForScenario(
                injectId,
                title,
                description,
                country,
                city,
                injectorContractId,
                allTeams,
                enabled,
                scenario.getId(),
                dependsDuration,
                content);
          } else {
            injectRepository.importSaveStandAlone(
                injectId,
                title,
                description,
                country,
                city,
                injectorContractId,
                allTeams,
                enabled,
                dependsDuration,
                content);
          }
          baseIds.put(id, new BaseHolder(injectId));
          originalIds.add(id);

          // Once the inject has been saved, we deal with the dependencies
          ArrayNode injectDependsOn = (ArrayNode) injectNode.get("inject_depends_on");
          for (JsonNode dependsOnNode : injectDependsOn) {
            // If there are dependencies where the added inject is the children, we add it to the
            // database
            if (id.equals(
                dependsOnNode.get("dependency_relationship").get("inject_children_id").asText())) {
              InjectDependencyInput dependency =
                  mapper.convertValue(dependsOnNode, InjectDependencyInput.class);

              Optional<Inject> injectParent =
                  injectRepository.findById(
                      baseIds.get(dependency.getRelationship().getInjectParentId()).getId());
              Optional<Inject> injectChildren =
                  injectRepository.findById(
                      baseIds.get(dependency.getRelationship().getInjectChildrenId()).getId());

              if (injectParent.isPresent() && injectChildren.isPresent()) {
                InjectDependency injectDependency = new InjectDependency();
                injectDependency.getCompositeId().setInjectParent(injectParent.get());
                injectDependency.getCompositeId().setInjectChildren(injectChildren.get());
                injectDependency.setInjectDependencyCondition(dependency.getConditions());
                injectDependenciesRepository.save(injectDependency);
              }
            }
          }
          // Tags
          List<String> injectTagIds = resolveJsonIds(injectNode, "inject_tags");
          injectTagIds.forEach(
              tagId -> {
                Base base = baseIds.get(tagId);
                if (base == null || base.getId() == null) {
                  return;
                }
                injectRepository.addTag(injectId, base.getId());
              });
          // Teams
          List<String> injectTeamIds = resolveJsonIds(injectNode, "inject_teams");
          injectTeamIds.forEach(
              teamId -> {
                Base base = baseIds.get(teamId);
                if (base == null || base.getId() == null) {
                  return;
                }
                injectRepository.addTeam(injectId, base.getId());
              });
          // Documents
          List<JsonNode> injectDocuments =
              resolveJsonElements(injectNode, "inject_documents").toList();
          injectDocuments.forEach(
              jsonNode -> {
                String docId = jsonNode.get("document_id").textValue();
                if (hasText(docId) && baseIds.get(docId) != null) {
                  String documentId = baseIds.get(docId).getId();
                  boolean docAttached = jsonNode.get("document_attached").booleanValue();
                  injectDocumentRepository.addInjectDoc(injectId, documentId, docAttached);
                } else {
                  log.warn("Missing document in the exercise_documents property");
                }
              });

          // Define default AssetsGroup or Assets
          Optional<Inject> injectOpt = injectRepository.findById(injectId);
          if (injectOpt.isPresent() && injectOpt.get().getInjectorContract().isPresent()) {
            Inject inject = injectOpt.get();
            if (assetGroup != null
                && injectorContractContentUtils.hasField(
                    inject.getInjectorContract().get(), "asset_groups")) {
              inject.getAssetGroups().add(assetGroup);
            } else if (asset != null
                && injectorContractContentUtils.hasField(
                    inject.getInjectorContract().get(), "assets")) {
              inject.getAssets().add(asset);
            }
            injectRepository.save(inject);
          }
        });
    // Looking for children of created injects
    List<JsonNode> childInjects =
        allInjects.stream()
            .filter(
                jsonNode -> {
                  ArrayNode injectDependsOn = (ArrayNode) jsonNode.get("inject_depends_on");

                  // We're getting the parents of this inject
                  List<String> parents =
                      StreamSupport.stream(injectDependsOn.spliterator(), false)
                          .map(
                              dependency ->
                                  dependency
                                      .get("dependency_relationship")
                                      .get("inject_parent_id")
                                      .asText())
                          .toList();

                  // If the parents have been created in this pass, we need to take care of the
                  // children now
                  return originalIds.stream().anyMatch(parents::contains);
                })
            .toList();
    if (!childInjects.isEmpty()) {
      importInjects(
          baseIds,
          exercise,
          scenario,
          asset,
          assetGroup,
          childInjects,
          allInjects,
          resolvedContracts);
    }
  }

  /**
   * Import injector contract from the starterpack before the real contract is created by the real
   * injector. The contract is created without any injector link: it is adopted (merged and linked)
   * by the real injector when it registers with the same contract id.
   *
   * @param importNode contract node
   * @param payload to set on contract
   * @return
   */
  private InjectorContract importInjectorContractFromStarterPack(
      JsonNode importNode, Payload payload, Map<String, Base> baseIds) {
    InjectorContract injectorContract = new InjectorContract();

    injectorContract.setId(importNode.get("injector_contract_id").textValue());
    injectorContract.setCustom(false);
    injectorContract.setContent(importNode.get("injector_contract_content").textValue());
    injectorContract.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    injectorContract.setConvertedContent((ObjectNode) importNode.get("convertedContent"));
    injectorContract.setExternalId(importNode.get("injector_contract_external_id").textValue());

    // Tags
    injectorContract.setTags(
        mergeTagIds(
            baseIds,
            importNode,
            "injector_contract_tags",
            importNode.get("injector_contract_payload"),
            "payload_tags"));

    // Domains
    injectorContract.setDomains(
        mergeDomains(
            baseIds,
            importNode,
            "injector_contract_",
            importNode.get("injector_contract_payload"),
            "payload_"));

    // Attack patterns
    injectorContract.setAttackPatterns(
        new ArrayList<>(
            mergeAttackPatterns(
                baseIds,
                importNode,
                "injector_contract_",
                importNode.get("injector_contract_payload"),
                "payload_")));

    injectorContract.setAtomicTesting(
        importNode.get("injector_contract_atomic_testing").booleanValue());
    injectorContract.setManual(importNode.get("injector_contract_manual").booleanValue());
    injectorContract.setNeedsExecutor(
        importNode.get("injector_contract_needs_executor").booleanValue());
    injectorContract.setPlatforms(
        Endpoint.PLATFORM_TYPE.fromJsonNode(importNode.get("injector_contract_platforms")));
    injectorContract.setLabels(
        new ObjectMapper()
            .convertValue(importNode.get("injector_contract_labels"), new TypeReference<>() {}));
    injectorContract.setPayload(payload);
    InjectorContractMigrationUtils.migratePredefinedExpectations(injectorContract);
    return injectorContractRepository.save(injectorContract);
  }

  public static ContractOutputType formatStringToContractOutputType(String value) {
    for (ContractOutputType type : ContractOutputType.values()) {
      if (type.getLabel().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown ContractOutputType: " + value);
  }

  private ContractOutputElementInput buildOuputElementFromJsonNode(
      JsonNode node, Map<String, Base> baseIds) {
    ContractOutputElementInput outputElement = new ContractOutputElementInput();
    outputElement.setFinding(node.get("contract_output_element_is_finding").asBoolean());
    outputElement.setRule(node.get("contract_output_element_rule").textValue());
    outputElement.setName(node.get("contract_output_element_name").textValue());
    outputElement.setKey(node.get("contract_output_element_key").textValue());
    outputElement.setType(
        formatStringToContractOutputType(node.get("contract_output_element_type").textValue()));
    importTags(node, "contract_output_element_tags", baseIds);
    outputElement.setTagIds(
        resolveJsonIds(node, "contract_output_element_tags").stream()
            .filter(baseIds::containsKey)
            .map(tid -> baseIds.get(tid).getId())
            .toList());
    ArrayNode regexGroupNodes = (ArrayNode) node.get("contract_output_element_regex_groups");
    for (JsonNode regexGroupNode : regexGroupNodes) {
      RegexGroupInput regexGroup = new RegexGroupInput();
      regexGroup.setField(regexGroupNode.get("regex_group_field").textValue());
      regexGroup.setIndexValues(regexGroupNode.get("regex_group_index_values").textValue());
      outputElement.getRegexGroups().add(regexGroup);
    }
    return outputElement;
  }

  private OutputParserInput buildOutputParserFromJsonNode(
      JsonNode node, Map<String, Base> baseIds) {
    OutputParserInput parser = new OutputParserInput();
    parser.setType(ParserType.valueOf(node.get("output_parser_type").textValue()));
    parser.setMode(ParserMode.valueOf(node.get("output_parser_mode").textValue()));
    ArrayNode outputElementNodes = (ArrayNode) node.get("output_parser_contract_output_elements");
    for (JsonNode outputElementNode : outputElementNodes) {
      parser
          .getContractOutputElements()
          .add(buildOuputElementFromJsonNode(outputElementNode, baseIds));
    }
    return parser;
  }

  private Set<OutputParserInput> buildOutputParsersFromPayloadJsonNode(
      JsonNode payloadNode, Map<String, Base> baseIds) {
    Set<OutputParserInput> outputParserInputs = new HashSet<>();
    if (!payloadNode.has("payload_output_parsers")) {
      return outputParserInputs;
    }

    ArrayNode outputParserNodes = (ArrayNode) payloadNode.get("payload_output_parsers");
    for (JsonNode outputParserNode : outputParserNodes) {
      outputParserInputs.add(buildOutputParserFromJsonNode(outputParserNode, baseIds));
    }
    return outputParserInputs;
  }

  private PayloadCreateInput buildPayloadCreateInput(
      Map<String, Base> baseIds, JsonNode payloadNode, @Nullable JsonNode injectorContractNode) {
    PayloadCreateInput payloadCreateInput = buildPayload(payloadNode);
    payloadCreateInput.setOutputParsers(
        buildOutputParsersFromPayloadJsonNode(payloadNode, baseIds));
    payloadCreateInput.setDetectionRemediations(buildDetectionRemediationsJsonNode(payloadNode));

    // Tags — merge from payload and injector contract nodes
    Set<Tag> tags =
        mergeTagIds(
            baseIds, payloadNode, "payload_tags", injectorContractNode, "injector_contract_tags");
    payloadCreateInput.setTagIds(tags.stream().map(Tag::getId).collect(Collectors.toList()));

    // Domains — merge from payload and injector contract nodes, fallback to ToClassify
    Set<Domain> domains =
        mergeDomains(baseIds, payloadNode, "payload_", injectorContractNode, "injector_contract_");
    payloadCreateInput.setDomainIds(
        domains.stream().map(Domain::getId).collect(Collectors.toList()));

    // Attack patterns — merge from payload and injector contract nodes
    Set<AttackPattern> attackPatterns =
        mergeAttackPatterns(
            baseIds, payloadNode, "payload_", injectorContractNode, "injector_contract_");
    payloadCreateInput.setAttackPatternsIds(
        attackPatterns.stream().map(AttackPattern::getId).collect(Collectors.toList()));

    return payloadCreateInput;
  }

  private String importPayloadAsMain(
      @NotNull final JsonNode importNode, Map<String, Base> baseIds) {
    JsonNode payloadNode = importNode.get("payload_information");
    if (payloadNode == null) {
      return null;
    }

    if (payloadNode.has("executable_file")) {
      ((ObjectNode) payloadNode)
          .put(
              "executable_file",
              baseIds.get(payloadNode.get("executable_file").textValue()).getId());
    }
    if (payloadNode.has("file_drop_file")) {
      ((ObjectNode) payloadNode)
          .put(
              "file_drop_file", baseIds.get(payloadNode.get("file_drop_file").textValue()).getId());
    }

    if (payloadNode.has("payload_arguments")) {
      for (JsonNode argNode : payloadNode.get("payload_arguments")) {
        if (argNode.has("type") && "document".equals(argNode.get("type").asText())) {
          JsonNode defaultValueNode = argNode.get("default_value");
          if (defaultValueNode != null
              && !defaultValueNode.asText().isBlank()
              && baseIds.containsKey(defaultValueNode.asText())) {
            ((ObjectNode) argNode)
                .put("default_value", baseIds.get(defaultValueNode.asText()).getId());
          }
        }
      }
    }
    PayloadCreateInput payloadCreateInput = buildPayloadCreateInput(baseIds, payloadNode, null);

    PayloadCreationService.PayloadInjectorContractCreationResult result =
        this.payloadCreationService.createPayload(payloadCreateInput);
    if (result.injectorContract() != null) {
      return result.injectorContract().getId();
    } else {
      log.warn("An error has occurred when importing the payload: {}", result.payload().getName());
      return null;
    }
  }

  /**
   * Resolves an injector contract from an inject_injector_contract JSON node. Tries in order:
   *
   * <ol>
   *   <li>Find by payload external_id (collector-created payloads)
   *   <li>Find by payload external_id via payload repository fallback
   *   <li>Create via importPayload (new payload)
   * </ol>
   *
   * @return the resolved injector contract ID, or null if resolution failed
   */
  private String resolveInjectorContract(
      @NotNull JsonNode injectContractNode, Map<String, Base> baseIds) {
    JsonNode payloadNode = injectContractNode.get("injector_contract_payload");
    if (payloadNode == null || payloadNode.isNull() || payloadNode.isEmpty()) {
      return null;
    }

    // Try to find by payload external_id first
    String externalId =
        payloadNode.has("payload_external_id")
            ? payloadNode.get("payload_external_id").textValue()
            : null;

    if (hasText(externalId)) {
      Optional<InjectorContract> contractFromPayload =
          injectorContractRepository.findOne(byPayloadExternalId(externalId));
      if (contractFromPayload.isPresent()) {
        return contractFromPayload.get().getId();
      }

      Optional<Payload> existingPayload = payloadRepository.findByExternalId(externalId);
      if (existingPayload.isPresent()) {
        Optional<InjectorContract> contractFromExternalId =
            injectorContractRepository.findInjectorContractByPayload(existingPayload.get());
        if (contractFromExternalId.isPresent()) {
          return contractFromExternalId.get().getId();
        }
      }
    }

    // Not found then create the payload and its contract
    InjectorContract created = importPayload(payloadNode, injectContractNode, baseIds);
    return created != null ? created.getId() : null;
  }

  private InjectorContract importPayload(
      @NotNull final JsonNode payloadNode,
      @NotNull final JsonNode injectContractNode,
      Map<String, Base> baseIds) {
    // swap executable file id or file drop file id
    if (payloadNode.has("executable_file")) {
      ((ObjectNode) payloadNode)
          .put(
              "executable_file",
              baseIds.get(payloadNode.get("executable_file").textValue()).getId());
    }
    if (payloadNode.has("file_drop_file")) {
      ((ObjectNode) payloadNode)
          .put(
              "file_drop_file", baseIds.get(payloadNode.get("file_drop_file").textValue()).getId());
    }

    PayloadCreateInput payloadCreateInput =
        buildPayloadCreateInput(baseIds, payloadNode, injectContractNode);
    PayloadCreationService.PayloadInjectorContractCreationResult result =
        this.payloadCreationService.createPayload(payloadCreateInput);

    if (result.injectorContract() != null) {
      return result.injectorContract();
    } else {
      log.warn("An error has occurred when importing the payload: {}", result.payload().getName());
      InjectorContract injectorContract = new InjectorContract();
      injectorContract.setPayload(result.payload());
      return injectorContract;
    }
  }

  private List<DetectionRemediationInput> buildDetectionRemediationsJsonNode(JsonNode payloadNode) {
    List<DetectionRemediationInput> detectionRemediationInputs = new ArrayList<>();

    JsonNode remediationsNode = payloadNode.get("payload_detection_remediations");
    if (remediationsNode == null || !remediationsNode.isArray()) {
      return detectionRemediationInputs;
    }

    for (JsonNode detectionNode : remediationsNode) {
      String valuesText = getTextValue(detectionNode, "detection_remediation_values");
      String type = getTextValue(detectionNode, "detection_remediation_collector_type");

      if (valuesText.isEmpty()) {
        continue;
      }

      Optional<CollectorType> collectorType = collectorTypeRepository.findByName(type);
      if (collectorType.isPresent()) {
        detectionRemediationInputs.add(buildDetectionRemediationFromJsonNode(detectionNode));
      } else {
        log.warn("Import Detection Remediations: Missing Collector type: {}", type);
      }
    }

    return detectionRemediationInputs;
  }

  private DetectionRemediationInput buildDetectionRemediationFromJsonNode(JsonNode node) {
    DetectionRemediationInput detectionRemediation = new DetectionRemediationInput();
    detectionRemediation.setValues((node.get("detection_remediation_values").textValue()));
    detectionRemediation.setCollectorType(
        (node.get("detection_remediation_collector_type").textValue()));
    return detectionRemediation;
  }

  private String getTextValue(JsonNode node, String fieldName) {
    JsonNode fieldNode = node.get(fieldName);
    return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText().trim() : "";
  }

  private void importVariables(
      JsonNode importNode,
      Exercise savedExercise,
      Scenario savedScenario,
      Map<String, Base> baseIds) {
    Optional<Iterator<JsonNode>> variableNodesOpt = Optional.empty();
    if (ofNullable(importNode.get(EXERCISE_VARIABLES)).isPresent()) {
      variableNodesOpt = ofNullable(importNode.get(EXERCISE_VARIABLES)).map(JsonNode::elements);
    } else if (ofNullable(importNode.get(SCENARIO_VARIABLES)).isPresent()) {
      variableNodesOpt = ofNullable(importNode.get(SCENARIO_VARIABLES)).map(JsonNode::elements);
    }
    variableNodesOpt.ifPresent(
        variableNodes ->
            variableNodes.forEachRemaining(
                variableNode -> {
                  String id = VariableWithValueMixin.getId(variableNode);
                  Variable variable = VariableWithValueMixin.build(variableNode);
                  if (savedExercise != null) {
                    variable.setExercise(savedExercise);
                  } else if (savedScenario != null) {
                    variable.setScenario(savedScenario);
                  }
                  Variable variableSaved = this.variableRepository.save(variable);
                  baseIds.put(id, variableSaved);
                }));
  }

  // -- WORKFLOW (CHAINING) --

  private void importWorkflow(
      JsonNode importNode,
      String prefix,
      Exercise savedExercise,
      Scenario savedScenario,
      Map<String, Base> baseIds,
      Map<String, String> resolvedContracts) {
    // Check for workflow node in both scenario and exercise exports
    String workflowKey = prefix.equals("scenario_") ? "scenario_workflow" : "exercise_workflow";
    JsonNode workflowNode = importNode.get(workflowKey);
    if (workflowNode == null || workflowNode.isNull() || workflowNode.isEmpty()) {
      return;
    }

    try {
      // Create the workflow
      Workflow workflow = new Workflow();
      workflow.setStatus(WorkflowStatus.TEMPLATE);
      workflow.setVersion(
          workflowNode.has("workflow_version") ? workflowNode.get("workflow_version").asInt(1) : 1);
      workflow.setEdited(false);

      // Configuration
      workflow.setRateLimitEnabled(
          workflowNode.has("workflow_rate_limit_enabled")
              && workflowNode.get("workflow_rate_limit_enabled").asBoolean());
      if (workflowNode.has("workflow_max_attempts")
          && !workflowNode.get("workflow_max_attempts").isNull()) {
        workflow.setMaxAttempts(workflowNode.get("workflow_max_attempts").asInt());
      }
      if (workflowNode.has("workflow_max_temporal_rate_seconds")
          && !workflowNode.get("workflow_max_temporal_rate_seconds").isNull()) {
        workflow.setMaxTemporalRateSeconds(
            workflowNode.get("workflow_max_temporal_rate_seconds").asLong());
      }
      workflow.setTimeoutEnabled(
          workflowNode.has("workflow_timeout_enabled")
              && workflowNode.get("workflow_timeout_enabled").asBoolean());
      if (workflowNode.has("workflow_timeout_seconds")
          && !workflowNode.get("workflow_timeout_seconds").isNull()) {
        workflow.setTimeoutSeconds(workflowNode.get("workflow_timeout_seconds").asLong());
      }
      workflow.setSafeModeEnabled(
          workflowNode.has("workflow_safe_mode_enabled")
              && workflowNode.get("workflow_safe_mode_enabled").asBoolean());

      // Associate with scenario or simulation
      if (savedScenario != null) {
        workflow.setScenario(savedScenario);
      } else if (savedExercise != null) {
        workflow.setSimulation(savedExercise);
      }

      // Import scope rules
      if (workflowNode.has("workflow_scope_rules")) {
        List<WorkflowScopeRule> scopeRules = new ArrayList<>();
        for (JsonNode ruleNode : workflowNode.get("workflow_scope_rules")) {
          ScopeRuleSource ruleSource =
              ruleNode.has("workflow_scope_rule_source")
                      && !ruleNode.get("workflow_scope_rule_source").isNull()
                  ? ScopeRuleSource.valueOf(ruleNode.get("workflow_scope_rule_source").asText())
                  : null;
          ScopeRuleValueType ruleValueType =
              ruleNode.has("workflow_scope_rule_value_type")
                      && !ruleNode.get("workflow_scope_rule_value_type").isNull()
                  ? ScopeRuleValueType.valueOf(
                      ruleNode.get("workflow_scope_rule_value_type").asText())
                  : null;
          if (WorkflowScopeRuleUtils.isAssetScopeRule(ruleSource, ruleValueType)) {
            continue;
          }
          WorkflowScopeRule rule =
              WorkflowScopeRule.builder()
                  .selectedMode(
                      ruleNode.has("workflow_scope_rule_selected_mode")
                              && !ruleNode.get("workflow_scope_rule_selected_mode").isNull()
                          ? ScopeRuleSelectedMode.valueOf(
                              ruleNode.get("workflow_scope_rule_selected_mode").asText())
                          : null)
                  .ruleSource(ruleSource)
                  .ruleValue(
                      ruleNode.has("workflow_scope_rule_value")
                          ? ruleNode.get("workflow_scope_rule_value").asText()
                          : null)
                  .valueType(ruleValueType)
                  .workflow(workflow)
                  .build();
          scopeRules.add(rule);
        }
        workflow.setWorkflowScopeRules(scopeRules);
      }

      // Import scope variables
      if (workflowNode.has("workflow_scope_variables")) {
        List<ScopeVariable> scopeVariables = new ArrayList<>();
        for (JsonNode varNode : workflowNode.get("workflow_scope_variables")) {
          ScopeVariable scopeVar =
              ScopeVariable.builder()
                  .key(
                      varNode.has("scope_variable_key")
                          ? varNode.get("scope_variable_key").asText()
                          : null)
                  .type(
                      varNode.has("scope_variable_type")
                              && !varNode.get("scope_variable_type").isNull()
                          ? PrimitiveType.fromLabel(varNode.get("scope_variable_type").asText())
                          : null)
                  .value(
                      varNode.has("scope_variable_value")
                          ? varNode.get("scope_variable_value").asText()
                          : null)
                  .description(
                      varNode.has("scope_variable_description")
                          ? varNode.get("scope_variable_description").asText()
                          : null)
                  .workflow(workflow)
                  .build();
          scopeVariables.add(scopeVar);
        }
        workflow.setWorkflowScopeVariables(scopeVariables);
      }

      // Save the workflow first
      workflowService.saveAll(List.of(workflow));

      // Import steps and conditions
      if (workflowNode.has("workflow_steps")) {
        importWorkflowSteps(
            workflowNode.get("workflow_steps"), workflow, resolvedContracts, baseIds);
      }

      // Import standalone events (root conditions not linked to any step)
      if (workflowNode.has("workflow_standalone_conditions")) {
        importConditionNodes(
            workflowNode.get("workflow_standalone_conditions"), workflow, null, Map.of());
      }
    } catch (Exception e) {
      log.warn("Failed to import workflow (chaining)", e);
      throw new ImportException(e);
    }
  }

  private void importWorkflowSteps(
      JsonNode stepsNode,
      Workflow workflow,
      Map<String, String> resolvedContracts,
      Map<String, Base> baseIds) {
    // Map from original step ID to new Step
    Map<String, Step> stepIdMap = new LinkedHashMap<>();
    // resolvedContracts is shared with importInjects to avoid creating duplicate payloads
    // when exercise_injects and workflow_steps reference the same missing injector contract

    // First pass: create all steps
    for (JsonNode stepNode : stepsNode) {
      String originalStepId = stepNode.has("step_id") ? stepNode.get("step_id").asText() : null;

      // Resolve injector contract in step_data if present
      String stepData = resolveStepData(stepNode, resolvedContracts, baseIds, workflow);

      Step step =
          Step.builder()
              .stepAction(
                  stepNode.has("step_action_class") && !stepNode.get("step_action_class").isNull()
                      ? StepActionClass.valueOf(stepNode.get("step_action_class").asText())
                      : null)
              .output(
                  stepNode.has("step_output") && !stepNode.get("step_output").isNull()
                      ? stepNode.get("step_output").asText()
                      : null)
              .outputParser(
                  stepNode.has("step_output_parser") && !stepNode.get("step_output_parser").isNull()
                      ? stepNode.get("step_output_parser").asText()
                      : null)
              .input(
                  stepNode.has("step_input") && !stepNode.get("step_input").isNull()
                      ? stepNode.get("step_input").asText()
                      : null)
              .data(stepData)
              .limitExecution(
                  stepNode.has("step_limit_execution")
                      ? stepNode.get("step_limit_execution").asInt(0)
                      : 0)
              .conditionExecuted(
                  stepNode.has("step_condition_executed")
                          && !stepNode.get("step_condition_executed").isNull()
                      ? stepNode.get("step_condition_executed").asText()
                      : null)
              .status(StepStatus.TEMPLATE)
              .workflow(workflow)
              .build();

      step = chainingStepService.saveStep(step);

      if (originalStepId != null) {
        stepIdMap.put(originalStepId, step);
      }
    }

    // Second pass: create conditions for each step
    for (JsonNode stepNode : stepsNode) {
      String originalStepId = stepNode.has("step_id") ? stepNode.get("step_id").asText() : null;
      Step step = originalStepId != null ? stepIdMap.get(originalStepId) : null;
      if (step == null || !stepNode.has("step_conditions")) {
        continue;
      }

      JsonNode conditionsNode = stepNode.get("step_conditions");
      if (conditionsNode == null || conditionsNode.isNull() || !conditionsNode.isArray()) {
        continue;
      }

      importConditionNodes(conditionsNode, workflow, step, stepIdMap);
    }
  }

  /**
   * Imports a list of condition nodes, optionally linking them to a step.
   *
   * <p>Pass {@code step = null} for standalone conditions (not linked to any step).
   */
  private void importConditionNodes(
      JsonNode conditionsNode,
      Workflow workflow,
      @Nullable Step step,
      Map<String, Step> stepIdMap) {
    Map<String, Condition> conditionIdMap = new LinkedHashMap<>();

    // Build conditions in parent-first order even when import arrays are unsorted.
    List<JsonNode> pendingNodes = new ArrayList<>();
    conditionsNode.forEach(pendingNodes::add);

    boolean progressed;
    do {
      progressed = false;
      Iterator<JsonNode> iterator = pendingNodes.iterator();
      while (iterator.hasNext()) {
        JsonNode condNode = iterator.next();

        String originalCondId =
            condNode.has("condition_id") ? condNode.get("condition_id").asText() : null;
        String parentId =
            condNode.has("condition_parent_id") && !condNode.get("condition_parent_id").isNull()
                ? condNode.get("condition_parent_id").asText()
                : null;
        String stepFromId =
            condNode.has("condition_step_from_id")
                    && !condNode.get("condition_step_from_id").isNull()
                ? condNode.get("condition_step_from_id").asText()
                : null;
        boolean isRoot =
            condNode.has("condition_is_root") && condNode.get("condition_is_root").asBoolean();

        Step stepFrom = stepFromId != null ? stepIdMap.get(stepFromId) : null;
        Condition parentCondition = parentId != null ? conditionIdMap.get(parentId) : null;
        ConditionType conditionType =
            condNode.has("condition_type") && !condNode.get("condition_type").isNull()
                ? ConditionType.valueOf(condNode.get("condition_type").asText())
                : null;

        // Standalone conditions are workflow events and must never be MAPPER conditions.
        if (step == null && ConditionType.MAPPER.equals(conditionType)) {
          iterator.remove();
          progressed = true;
          continue;
        }

        // Wait until the parent condition has been created before importing this child.
        if (parentId != null && parentCondition == null) {
          continue;
        }

        Condition condition =
            Condition.builder()
                .workflowId(workflow.getId())
                .key(
                    condNode.has("condition_key") && !condNode.get("condition_key").isNull()
                        ? condNode.get("condition_key").asText()
                        : null)
                .keyType(
                    condNode.has("condition_key_type")
                            && !condNode.get("condition_key_type").isNull()
                        ? mapper.convertValue(
                            condNode.get("condition_key_type").asText(), PrimitiveType.class)
                        : null)
                .type(conditionType)
                .mappingType(
                    condNode.has("condition_mapping_type")
                            && !condNode.get("condition_mapping_type").isNull()
                        ? MappingType.valueOf(condNode.get("condition_mapping_type").asText())
                        : null)
                .value(
                    condNode.has("condition_value") && !condNode.get("condition_value").isNull()
                        ? condNode.get("condition_value").asText()
                        : null)
                .name(
                    condNode.has("condition_name") && !condNode.get("condition_name").isNull()
                        ? condNode.get("condition_name").asText()
                        : null)
                .description(
                    condNode.has("condition_description")
                            && !condNode.get("condition_description").isNull()
                        ? condNode.get("condition_description").asText()
                        : null)
                .conditionParent(parentCondition)
                .stepFrom(stepFrom)
                .build();

        if (step != null && isRoot) {
          chainingConditionService.linkToStep(condition, step, isRoot);
        }
        condition = chainingConditionService.saveCondition(condition);

        if (originalCondId != null) {
          conditionIdMap.put(originalCondId, condition);
        }
        iterator.remove();
        progressed = true;
      }
    } while (progressed && !pendingNodes.isEmpty());

    if (!pendingNodes.isEmpty()) {
      log.warn(
          "Skipped {} condition(s) during import because parent condition was unresolved",
          pendingNodes.size());
    }
  }

  private String getNodeValue(JsonNode importNode) {
    return ofNullable(importNode).map(JsonNode::textValue).orElse(null);
  }

  /**
   * Resolves the step_data field by ensuring the referenced injector contract and payload exist. If
   * the injector contract doesn't exist in the target environment, creates the payload using the
   * same logic as importInjects (via importPayload) and updates the step_data with the new
   * injector_contract_id.
   */
  private String resolveStepData(
      JsonNode stepNode,
      Map<String, String> resolvedContracts,
      Map<String, Base> baseIds,
      Workflow workflow) {
    if (!stepNode.has("step_data") || stepNode.get("step_data").isNull()) {
      return null;
    }

    JsonNode stepDataNode = stepNode.get("step_data");

    String stepDataRaw;
    JsonNode dataJson;
    try {
      if (stepDataNode.isTextual()) {
        stepDataRaw = stepDataNode.asText();
        dataJson = mapper.readTree(stepDataRaw);
      } else {
        dataJson = stepDataNode;
        stepDataRaw = mapper.writeValueAsString(stepDataNode);
      }
    } catch (Exception e) {
      log.warn("Failed to parse step_data JSON, skipping resolution", e);
      return stepDataNode.isTextual() ? stepDataNode.asText() : stepDataNode.toString();
    }

    // Extract injector contract info from step_data
    JsonNode injectContractNode = dataJson.get("inject_injector_contract");
    if (injectContractNode == null || injectContractNode.isNull()) {
      return sanitizateStepData(dataJson, stepDataRaw, workflow);
    }

    String injectorContractId = extractInjectorContractId(injectContractNode);
    if (!hasText(injectorContractId)) {
      return sanitizateStepData(dataJson, stepDataRaw, workflow);
    }

    // Contract already exists in DB, no resolution needed
    if (injectorContractRepository.existsByContractId(injectorContractId)
        && !shouldResolveContractFromStepData(injectContractNode, injectorContractId)) {
      return sanitizateStepData(dataJson, stepDataRaw, workflow);
    }

    // Already resolved by a previous step or by importInjects — reuse
    String alreadyResolved = resolvedContracts.get(injectorContractId);
    if (alreadyResolved != null) {
      if (!updateContractIdInStepData(dataJson, alreadyResolved)) {
        return stepDataRaw;
      }
      return sanitizateStepData(dataJson, stepDataRaw, workflow);
    }

    if (!(injectContractNode instanceof ObjectNode injectContractObject)) {
      log.warn(
          "Step data references missing injector contract {} in textual form with no payload to recreate",
          injectorContractId);
      return sanitizateStepData(dataJson, stepDataRaw, workflow);
    }

    // Contract is missing then resolve using the same logic as importInjects
    JsonNode payloadNode = injectContractObject.get("injector_contract_payload");
    if (payloadNode == null || payloadNode.isNull() || payloadNode.isEmpty()) {
      log.warn(
          "Step data references missing injector contract {} with no payload to recreate",
          injectorContractId);
      return sanitizateStepData(dataJson, stepDataRaw, workflow);
    }

    String newContractId = resolveInjectorContract(injectContractObject, baseIds);

    // Update step_data and cache the mapping
    if (newContractId != null) {
      resolvedContracts.put(injectorContractId, newContractId);
      if (!updateContractIdInStepData(dataJson, newContractId)) {
        return stepDataRaw;
      }
      return sanitizateStepData(dataJson, stepDataRaw, workflow);
    }

    return sanitizateStepData(dataJson, stepDataRaw, workflow);
  }

  private boolean shouldResolveContractFromStepData(
      JsonNode injectContractNode, String injectorContractId) {
    if (!(injectContractNode instanceof ObjectNode injectContractObject)) {
      return false;
    }

    JsonNode payloadNode = injectContractObject.get("injector_contract_payload");
    if (payloadNode == null || payloadNode.isNull() || !payloadNode.has("payload_output_parsers")) {
      return false;
    }
    JsonNode outputParsersNode = payloadNode.get("payload_output_parsers");
    if (outputParsersNode == null || !outputParsersNode.isArray() || outputParsersNode.isEmpty()) {
      return false;
    }

    Optional<InjectorContract> existingContractOpt =
        injectorContractRepository.findById(injectorContractId);
    if (existingContractOpt.isEmpty()) {
      return true;
    }

    InjectorContract existingContract = existingContractOpt.get();
    Payload existingPayload = existingContract.getPayload();
    return existingPayload == null
        || existingPayload.getOutputParsers() == null
        || existingPayload.getOutputParsers().isEmpty();
  }

  private boolean updateContractIdInStepData(JsonNode dataJson, String newContractId) {
    try {
      ObjectNode dataObject = (ObjectNode) dataJson;
      JsonNode injectContractNode = dataObject.get("inject_injector_contract");
      if (injectContractNode instanceof ObjectNode injectContractObjectNode) {
        injectContractObjectNode.put("injector_contract_id", newContractId);
      } else {
        ObjectNode normalizedInjectContractNode = mapper.createObjectNode();
        normalizedInjectContractNode.put("injector_contract_id", newContractId);
        dataObject.set("inject_injector_contract", normalizedInjectContractNode);
      }
      return true;
    } catch (Exception e) {
      log.warn("Failed to update step_data with resolved injector contract", e);
      return false;
    }
  }

  private String serializeStepData(JsonNode dataJson, String fallback) {
    try {
      return mapper.writeValueAsString(dataJson);
    } catch (Exception e) {
      log.warn("Failed to serialize workflow step_data after contract resolution", e);
      return fallback;
    }
  }

  private String sanitizateStepData(JsonNode dataJson, String fallback, Workflow workflow) {
    if (!(dataJson instanceof ObjectNode dataObject) || workflow == null) {
      return fallback;
    }
    dataObject.remove("inject_assets");
    dataObject.remove("inject_asset_groups");
    dataObject.remove("inject_exercise");
    dataObject.remove("inject_scenario");
    if (workflow.getSimulation() != null) {
      dataObject.put("inject_exercise", workflow.getSimulation().getId());
      dataObject.putNull("inject_scenario");
    } else if (workflow.getScenario() != null) {
      dataObject.put("inject_scenario", workflow.getScenario().getId());
      dataObject.putNull("inject_exercise");
    }
    return serializeStepData(dataObject, fallback);
  }

  private static String extractInjectorContractId(JsonNode injectContractNode) {
    if (injectContractNode == null || injectContractNode.isNull()) {
      return null;
    }
    if (injectContractNode.isTextual()) {
      return injectContractNode.asText();
    }
    JsonNode contractIdNode = injectContractNode.get("injector_contract_id");
    return contractIdNode != null && !contractIdNode.isNull() ? contractIdNode.asText() : null;
  }

  private static class BaseHolder implements Base {

    private String id;

    public BaseHolder(String id) {
      this.id = id;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public void setId(String id) {
      this.id = id;
    }
  }
}
