package io.openaev.importer;

import static io.openaev.database.specification.InjectorContractSpecification.byPayloadExternalId;
import static io.openaev.database.specification.InjectorContractSpecification.byPayloadId;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;
import static io.openaev.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static io.openaev.rest.exercise.exports.ExerciseFileExport.EXERCISE_VARIABLES;
import static io.openaev.rest.payload.PayloadUtils.buildPayload;
import static io.openaev.rest.scenario.export.ScenarioFileExport.SCENARIO_VARIABLES;
import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.model.Scenario.SEVERITY;
import io.openaev.database.repository.*;
import io.openaev.injectors.challenge.model.ChallengeContent;
import io.openaev.injectors.channel.model.ChannelContent;
import io.openaev.rest.exercise.exports.VariableWithValueMixin;
import io.openaev.rest.inject.form.InjectDependencyInput;
import io.openaev.rest.payload.contract_output_element.ContractOutputElementInput;
import io.openaev.rest.payload.form.*;
import io.openaev.rest.payload.output_parser.OutputParserInput;
import io.openaev.rest.payload.regex_group.RegexGroupInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.service.FileService;
import io.openaev.service.ImportEntry;
import io.openaev.service.ScenarioService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.constants.Constants;
import jakarta.activation.MimetypesFileTypeMap;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
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
  @Autowired private CollectorRepository collectorRepository;

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
          // Error rewriting content, inject cant be created
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
          // Error rewriting content, inject cant be created
          return null;
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
      Scenario scenario) {
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
        Optional.ofNullable(importExercise(importNode, baseIds)).orElse(exercise);
    Scenario savedScenario =
        Optional.ofNullable(importScenario(importNode, baseIds)).orElse(scenario);
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
    importInjects(importNode, prefix, savedExercise, savedScenario, baseIds);
    importVariables(importNode, savedExercise, savedScenario, baseIds);
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

  // -- ATTACK PATTERN --
  private List<String> importAttackPattern(
      JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    ArrayList<String> attackPatternIds = new ArrayList<>();
    resolveJsonElements(importNode, prefix + "attack_patterns")
        .forEach(
            nodeAttackPattern -> {
              JsonNode idNode = nodeAttackPattern.get("attack_pattern_id");
              if (idNode == null) {
                return;
              }
              String id = idNode.textValue();

              if (baseIds.get(id) != null) {
                // Already import
                return;
              }
              String name = nodeAttackPattern.get("attack_pattern_external_id").textValue();

              List<AttackPattern> existingAttackPattern =
                  this.attackPatternRepository.findAllByExternalIdInIgnoreCase(List.of(name));
              if (!existingAttackPattern.isEmpty()) {
                baseIds.put(id, existingAttackPattern.getFirst());
                attackPatternIds.add(existingAttackPattern.getFirst().getId());
              } else {
                AttackPattern attackPatternCreated =
                    this.attackPatternRepository.save(
                        createAttackPattern(
                            nodeAttackPattern,
                            importKillChainPhase(nodeAttackPattern, "attack_pattern_", baseIds)));
                baseIds.put(id, attackPatternCreated);
                attackPatternIds.add(attackPatternCreated.getId());
              }
            });
    return attackPatternIds;
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

  private Exercise importExercise(JsonNode importNode, Map<String, Base> baseIds) {
    JsonNode exerciseNode = importNode.get("exercise_information");
    if (exerciseNode == null) {
      return null;
    }

    Exercise exercise = new Exercise();
    exercise.setName(
        exerciseNode.get("exercise_name").textValue()
            + " %s".formatted(Constants.IMPORTED_OBJECT_NAME_SUFFIX));
    exercise.setDescription(exerciseNode.get("exercise_description").textValue());
    exercise.setSubtitle(exerciseNode.get("exercise_subtitle").textValue());
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

  private Scenario importScenario(JsonNode importNode, Map<String, Base> baseIds) {
    JsonNode scenarioNode = importNode.get("scenario_information");
    if (scenarioNode == null) {
      return null;
    }

    Scenario scenario = new Scenario();
    scenario.setName(
        scenarioNode.get("scenario_name").textValue()
            + " %s".formatted(Constants.IMPORTED_OBJECT_NAME_SUFFIX));
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
      document.setExercises(Set.of(savedExercise));
    } else if (savedScenario != null) {
      document.setScenarios(Set.of(savedScenario));
    }
    // need to get real database-bound ids for tags
    List<String> tagIds =
        resolveJsonIds(nodeDoc, "document_tags").stream()
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
      Map<String, Base> baseIds) {
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
        injectsNoParent.toList(),
        injectsStream.get().toList());
  }

  private void importInjects(
      Map<String, Base> baseIds,
      Exercise exercise,
      Scenario scenario,
      List<JsonNode> injectsToAdd,
      List<JsonNode> allInjects) {
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

          String injectorContractId = null;

          // If not, rely on payload
          if (injectorContract.isEmpty()) {
            JsonNode payloadNode = injectContractNode.get("injector_contract_payload");
            if (!payloadNode.isNull() && !payloadNode.isEmpty()) {
              String externalId = payloadNode.get("payload_external_id").textValue();
              // Rely on external collector
              if (hasText(externalId)) {
                Optional<InjectorContract> injectorContractFromPayload =
                    this.injectorContractRepository.findOne(byPayloadExternalId(externalId));
                if (injectorContractFromPayload.isPresent()) {
                  injectorContractId = injectorContractFromPayload.get().getId();
                  // Create new payload
                } else {
                  log.info(
                      "Inject comes from a collector not set up in your environment, a new payload has been created.");
                  injectorContractId = importPayload(payloadNode, baseIds);
                }
                // Create new payload
              } else {
                injectorContractId = importPayload(payloadNode, baseIds);
              }
            }
          } else {
            injectorContractId = injectorContract.get().getId();
          }

          if (injectorContractId == null) {
            log.warn(
                "Import Inject Failed: Unresolved injector contract ID on inject: {}", injectId);
            return;
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
      importInjects(baseIds, exercise, scenario, childInjects, allInjects);
    }
  }

  public static ContractOutputType formatStringToContractOutputType(String value) {
    for (ContractOutputType type : ContractOutputType.values()) {
      if (type.label.equalsIgnoreCase(value)) {
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

    PayloadCreateInput payloadCreateInput = buildPayload(payloadNode);
    payloadCreateInput.setOutputParsers(
        buildOutputParsersFromPayloadJsonNode(payloadNode, baseIds));

    List<String> attackPatternIds = importAttackPattern(payloadNode, "payload_", baseIds);
    payloadCreateInput.setAttackPatternsIds(attackPatternIds);
    payloadCreateInput.setDetectionRemediations(buildDetectionRemediationsJsonNode(payloadNode));
    Payload payload = this.payloadCreationService.createPayload(payloadCreateInput);
    payload.setTags(
        resolveJsonIds(payloadNode, "payload_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));
    Optional<InjectorContract> injectorContractFromPayload =
        this.injectorContractRepository.findOne(byPayloadId(payload.getId()));
    if (injectorContractFromPayload.isPresent()) {
      return injectorContractFromPayload.get().getId();
    } else {
      log.warn("An error has occurred when importing the payload: {}", payload.getName());
      return null;
    }
  }

  private String importPayload(@NotNull final JsonNode payloadNode, Map<String, Base> baseIds) {
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

    PayloadCreateInput payloadCreateInput = buildPayload(payloadNode);
    payloadCreateInput.setOutputParsers(
        buildOutputParsersFromPayloadJsonNode(payloadNode, baseIds));

    List<String> attackPatternIds = importAttackPattern(payloadNode, "payload_", baseIds);
    payloadCreateInput.setAttackPatternsIds(attackPatternIds);
    payloadCreateInput.setDetectionRemediations(buildDetectionRemediationsJsonNode(payloadNode));
    Payload payload = this.payloadCreationService.createPayload(payloadCreateInput);
    payload.setTags(
        resolveJsonIds(payloadNode, "payload_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));

    Optional<InjectorContract> injectorContractFromPayload =
        this.injectorContractRepository.findOne(byPayloadId(payload.getId()));

    if (injectorContractFromPayload.isPresent()) {
      return injectorContractFromPayload.get().getId();
    } else {
      log.warn("An error has occurred when importing the payload: {}", payload.getName());
      return null;
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

      Optional<Collector> collector = collectorRepository.findByType(type);
      if (collector.isPresent()) {
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

  private String getNodeValue(JsonNode importNode) {
    return ofNullable(importNode).map(JsonNode::textValue).orElse(null);
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
