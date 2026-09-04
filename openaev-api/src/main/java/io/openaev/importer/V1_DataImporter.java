package io.openaev.importer;

import static io.openaev.database.specification.InjectorContractSpecification.byPayloadExternalId;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;
import static io.openaev.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static io.openaev.rest.exercise.exports.ExerciseFileExport.EXERCISE_VARIABLES;
import static io.openaev.rest.payload.PayloadUtils.buildPayload;
import static io.openaev.rest.scenario.export.ScenarioFileExport.SCENARIO_VARIABLES;
import static io.openaev.service.chaining.WorkflowService.DEFAULT_TIMEOUT_SECONDS;
import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.model.Scenario.SEVERITY;
import io.openaev.database.repository.*;
import io.openaev.ee.EnterpriseEditionException;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.injectors.challenge.model.ChallengeContent;
import io.openaev.injectors.channel.model.ChannelContent;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.rest.exercise.exports.VariableWithValueMixin;
import io.openaev.rest.inject.form.InjectDependencyInput;
import io.openaev.rest.kill_chain_phase.service.KillChainPhaseService;
import io.openaev.rest.payload.contract_output_element.ContractOutputElementInput;
import io.openaev.rest.payload.form.DetectionRemediationInput;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.output_parser.OutputParserInput;
import io.openaev.rest.payload.regex_group.RegexGroupInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.service.*;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.CollectorTypeHumanizer;
import io.openaev.utils.ConditionKeyTypesUtils;
import io.openaev.utils.WorkflowScopeRuleUtils;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import io.openaev.utils.injector_contract.InjectorContractMigrationUtils;
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
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

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
  private final KillChainPhaseService killChainPhaseService;
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
  private final SecurityPlatformRepository securityPlatformRepository;
  private final DomainService domainService;
  private final io.openaev.service.chaining.WorkflowService workflowService;
  private final io.openaev.service.chaining.StepService chainingStepService;
  private final io.openaev.service.chaining.ConditionService chainingConditionService;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;

  private final InjectorContractContentUtils injectorContractContentUtils;

  private final UserService userService;

  private final PermissionService permissionService;

  private final InjectorService injectorService;

  private final TenantWriteScopeResolver tenantWriteScopeResolver;

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
          // Tenant-scoped: the id is already resolved to the current tenant, but a bare findById
          // matches only compositeId.id and can throw on ids duplicated across tenants (e.g.
          // starter-pack contracts imported into every tenant with the same id).
          Optional<InjectorContract> contractOpt =
              injectorContractRepository.findByContractIdAndTenant(
                  contract, TenantContext.getCurrentTenant());
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
  public ImportResult importData(
      TxCtx ctx,
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
      importPayloadAsMain(ctx, importNode, baseIds);
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
          ctx,
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
    List<SkippedWorkflowStep> skippedSteps =
        importWorkflow(
            ctx, importNode, prefix, savedExercise, savedScenario, baseIds, resolvedContracts);
    List<MissingImportedAction> missingActions =
        skippedSteps.stream().map(V1_DataImporter::toMissingImportedAction).toList();
    return new ImportResult(new ArrayList<>(missingActions));
  }

  private static MissingImportedAction toMissingImportedAction(SkippedWorkflowStep step) {
    String type =
        step.type() == SkippedWorkflowStepType.INJECTOR ? "Injector" : "InjectorContract/Payload";
    String name = hasText(step.injectTitle()) ? step.injectTitle() : step.resourceName();
    return new MissingImportedAction(type, name);
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

              // Tenant-scoped on purpose: the id comes from the import file and a PK load bypasses
              // the Hibernate tenant filter, so a foreign tenant's domain must never be reused.
              Optional<Domain> existingDomain =
                  this.domainService.findOptionalByIdAndTenantId(
                      id, TenantContext.getCurrentTenant());
              if (existingDomain.isPresent()) {
                baseIds.put(id, existingDomain.get());
                domains.add(existingDomain.get());
              } else {
                if (nodeDomain.isTextual()) {
                  return;
                }
                Domain createdDomain = upsertDomainFromNode(nodeDomain);
                baseIds.put(id, createdDomain);
                domains.add(createdDomain);
              }
            });

    return domains;
  }

  /**
   * Upserts a {@link Domain} from an object-shaped JSON node (find-by-name or create). Shared by
   * {@link #importDomains} (object-format entries) and {@link
   * #resolveInjectContractDomainsFromInjectFormat} so the upsert logic is defined once.
   *
   * @param nodeDomain an object node carrying {@code domain_name} and {@code domain_color}
   * @return the existing (matched by name) or newly created domain for the current tenant
   */
  private Domain upsertDomainFromNode(JsonNode nodeDomain) {
    return this.domainService.upsert(
        nodeDomain.get("domain_name").textValue(),
        nodeDomain.get("domain_color").textValue(),
        new Tenant(TenantContext.getCurrentTenant()));
  }

  /**
   * Pre-populates {@code baseIds} with the domains carried by the inject-level, RICH-format field
   * {@code inject_contract_domains} ({@code [{domain_id, domain_name, ...}]}), so the
   * contract-level {@code injector_contract_domains} field — serialized by {@code
   * MultiIdSetSerializer} as bare SOURCE ids only — can be resolved cross-instance by {@link
   * #importDomains} via its {@code baseIds.get(id) != null} cache hit.
   *
   * <p>Domain entities are created per tenant with a DIFFERENT UUID on each instance, so resolving
   * a bare source id by id always fails cross-instance and {@link #importDomains} would otherwise
   * drop it (no name available at the contract level to upsert by name). The inject-level rich
   * format provides that name.
   *
   * <p><b>Chaining-only:</b> {@code step_data} is serialized without the export mixins (see {@code
   * InjectExecutionStep.stepData()}), so it always carries both {@code inject_contract_domains}
   * (rich) and {@code inject_injector_contract.injector_contract_domains} (bare ids), both derived
   * from the same {@code InjectorContract.getDomains()} — hence synchronized on the same source
   * ids. The classic pipeline ({@code importInjects}) intentionally does NOT benefit from this: its
   * exported injects strip {@code inject_contract_domains} via {@code Mixins.Inject}, so its
   * documented drop behaviour for {@code injector_contract_domains} is left unchanged (separate
   * technical debt).
   *
   * @param dataJson the parsed step_data node
   * @param baseIds the shared ID-to-entity cache consumed by {@link #importDomains}
   */
  private void resolveInjectContractDomainsFromInjectFormat(
      JsonNode dataJson, Map<String, Base> baseIds) {
    resolveJsonElements(dataJson, "inject_contract_domains")
        .forEach(
            nodeDomain -> {
              if (nodeDomain == null || !nodeDomain.isObject()) {
                return;
              }
              String id =
                  ofNullable(nodeDomain.get("domain_id")).map(JsonNode::textValue).orElse(null);
              if (!hasText(id) || baseIds.get(id) != null) {
                return;
              }
              // Same resolution order as importDomains' object branch: exact id (same instance)
              // first, then upsert by name (cross-instance).
              Domain resolved =
                  this.domainService
                      .findOptionalByIdAndTenantId(id, TenantContext.getCurrentTenant())
                      .orElseGet(() -> upsertDomainFromNode(nodeDomain));
              baseIds.put(id, resolved);
            });
  }

  // -- ATTACK PATTERN --
  /**
   * Resolves the {@code <prefix>attack_patterns} array into target-instance attack patterns. OBJECT
   * entries (the export shape) are resolved via the {@code baseIds} cache, then by {@code
   * attack_pattern_external_id} (MITRE ATT&amp;CK id), then created. SCALAR entries (bare UUIDs,
   * e.g. step_data arrays already normalized on the source instance) are resolved via {@code
   * baseIds}, then kept when the id still exists on the target tenant — tenant-scoped on purpose, a
   * PK load would bypass the Hibernate tenant filter. Scalar ids resolving to nothing are dropped:
   * there is no name/external id to recreate them from.
   */
  private List<AttackPattern> importAttackPattern(
      TxCtx ctx, JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    ArrayList<AttackPattern> attackPatterns = new ArrayList<>();
    String tenantId = TenantContext.getCurrentTenant();
    resolveJsonElements(importNode, prefix + "attack_patterns")
        .forEach(
            nodeAttackPattern -> {
              if (nodeAttackPattern != null && nodeAttackPattern.isTextual()) {
                String rawId = nodeAttackPattern.asText();
                if (!hasText(rawId)) {
                  return;
                }
                if (baseIds.get(rawId) instanceof AttackPattern cached) {
                  attackPatterns.add(cached);
                  return;
                }
                this.attackPatternRepository
                    .findByIdAndTenantId(rawId, tenantId)
                    .ifPresent(
                        existing -> {
                          baseIds.put(rawId, existing);
                          attackPatterns.add(existing);
                        });
                return;
              }
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
                            importKillChainPhase(
                                ctx, nodeAttackPattern, "attack_pattern_", baseIds)));
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
   * <p>Resolution order per bare id: the {@code baseIds} cache first (seeded by {@link #importTags}
   * from the root-level tag objects), then a tenant-scoped DB lookup for ids that already exist on
   * the target tenant (e.g. re-import on the same instance, where the export carries no root tag
   * object to seed {@code baseIds}). Ids resolving to nothing are dropped. The DB fallback is
   * tenant-scoped on purpose: the id comes from the import file and a PK load would bypass the
   * Hibernate tenant filter.
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
    resolveTagIds(node1, key1, baseIds, tags);
    if (node2 != null) {
      resolveTagIds(node2, key2, baseIds, tags);
    }
    return tags;
  }

  private void resolveTagIds(
      JsonNode node, String key, Map<String, Base> baseIds, Set<Tag> collector) {
    String tenantId = TenantContext.getCurrentTenant();
    for (String rawId : resolveJsonIds(node, key)) {
      if (!hasText(rawId)) {
        continue;
      }
      Base cached = baseIds.get(rawId);
      if (cached instanceof Tag tag) {
        collector.add(tag);
        continue;
      }
      if (cached != null) {
        continue;
      }
      this.tagRepository
          .findByIdAndTenantId(rawId, tenantId)
          .ifPresent(
              existing -> {
                baseIds.put(rawId, existing);
                collector.add(existing);
              });
    }
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
      TxCtx ctx,
      Map<String, Base> baseIds,
      JsonNode node1,
      String prefix1,
      @Nullable JsonNode node2,
      @Nullable String prefix2) {
    Set<AttackPattern> patterns =
        new LinkedHashSet<>(importAttackPattern(ctx, node1, prefix1, baseIds));
    if (node2 != null) {
      patterns.addAll(importAttackPattern(ctx, node2, prefix2, baseIds));
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
      TxCtx ctx, JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    List<KillChainPhase> killChainPhases = new ArrayList<>();
    String writeTenant = tenantWriteScopeResolver.tenantForWrite(ctx, null);
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
              KillChainPhase killChainPhase =
                  this.killChainPhaseService.resolveOrCreateForImport(
                      writeTenant, createKillChainPhase(nodeKillChainPhase, writeTenant));
              baseIds.put(id, killChainPhase);
              killChainPhases.add(killChainPhase);
            });
    return killChainPhases;
  }

  private KillChainPhase createKillChainPhase(JsonNode killChainPhaseNode, String tenantId) {
    KillChainPhase killChainPhase = new KillChainPhase();
    killChainPhase.setTenant(new Tenant(tenantId));
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
    ofNullable(exerciseNode.get("exercise_default_kill_chain"))
        .map(JsonNode::textValue)
        .ifPresent(exercise::setDefaultKillChain);
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
    ofNullable(scenarioNode.get("scenario_default_kill_chain"))
        .map(JsonNode::textValue)
        .ifPresent(scenario::setDefaultKillChain);
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
    Optional<Document> targetDocument =
        this.documentRepository.findFirstByTargetOrderByIdAsc(target);

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
      TxCtx ctx,
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
        ctx,
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
      TxCtx ctx,
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
          // Tenant-scoped on purpose: the contract id comes from the import file and
          // InjectorContract's PK is composite (tenant_id, id), so a bare findById could resolve
          // another tenant's contract (cross-tenant reuse) or throw on duplicate ids across
          // tenants. A foreign-only id must fall through to resolveInjectorContract instead.
          Optional<InjectorContract> injectorContract =
              this.injectorContractRepository.findByContractIdAndTenant(
                  injectorContractIdFromNode, TenantContext.getCurrentTenant());

          String injectorContractId;
          InjectorContract resolvedContract = null;

          if (injectorContract.isPresent()) {
            injectorContractId = injectorContract.get().getId();
          } else {
            resolvedContract = resolveInjectorContract(ctx, injectContractNode, baseIds);
            injectorContractId = resolvedContract != null ? resolvedContract.getId() : null;
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
              // resolveInjectorContract already created and persisted the payload; it just could
              // not build a contract because no payload-supporting injector is registered yet on a
              // fresh platform. Carry that payload onto the starter-pack contract so it is not
              // orphaned (otherwise the inject shows a question mark and "no payload attached").
              Payload createdPayload =
                  resolvedContract != null ? resolvedContract.getPayload() : null;
              injectorContractId =
                  importInjectorContractFromStarterPack(
                          ctx, injectContractNode, createdPayload, baseIds)
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
          ctx,
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
      TxCtx ctx, JsonNode importNode, Payload payload, Map<String, Base> baseIds) {
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
                ctx,
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
    importTags(node, "contract_output_element_", baseIds);
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
      TxCtx ctx,
      Map<String, Base> baseIds,
      JsonNode payloadNode,
      @Nullable JsonNode injectorContractNode) {
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
            ctx, baseIds, payloadNode, "payload_", injectorContractNode, "injector_contract_");
    payloadCreateInput.setAttackPatternsIds(
        attackPatterns.stream().map(AttackPattern::getId).collect(Collectors.toList()));

    return payloadCreateInput;
  }

  private String importPayloadAsMain(
      TxCtx ctx, @NotNull final JsonNode importNode, Map<String, Base> baseIds) {
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
    PayloadCreateInput payloadCreateInput =
        buildPayloadCreateInput(ctx, baseIds, payloadNode, null);

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
   * <p>The returned contract may be a transient, id-less contract that only carries the freshly
   * created payload: this happens when no payload-supporting injector is registered yet (fresh
   * platform starter-pack import). Callers must handle a null id and, in the starter-pack path,
   * propagate {@link InjectorContract#getPayload()} so the created payload is not orphaned.
   *
   * @return the resolved injector contract (possibly transient, carrying only the payload), or null
   *     if resolution failed
   */
  private InjectorContract resolveInjectorContract(
      TxCtx ctx, @NotNull JsonNode injectContractNode, Map<String, Base> baseIds) {
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
        return contractFromPayload.get();
      }

      Optional<Payload> existingPayload = payloadRepository.findByExternalId(externalId);
      if (existingPayload.isPresent()) {
        Optional<InjectorContract> contractFromExternalId =
            injectorContractRepository.findInjectorContractByPayload(existingPayload.get());
        if (contractFromExternalId.isPresent()) {
          return contractFromExternalId.get();
        }
      }
    }

    // Not found then create the payload and its contract
    return importPayload(ctx, payloadNode, injectContractNode, baseIds);
  }

  /**
   * Read-only counterpart of {@link #resolveInjectorContract} used exclusively by the chaining
   * (workflow_steps) pipeline. It reproduces ONLY the two {@code payload_external_id} lookup steps
   * of {@link #resolveInjectorContract} and NEVER falls back to {@link #importPayload}: a step
   * whose contract/payload cannot be resolved on the target instance must be skipped, not recreated
   * as an orphan. {@link #resolveInjectorContract} itself and its use in {@code importInjects} (the
   * time-based pipeline) are intentionally left untouched.
   *
   * @return the already-existing injector contract resolvable from the payload external id, or
   *     empty when nothing matches (caller must treat the step as unresolvable)
   */
  private Optional<InjectorContract> resolveInjectorContractReadOnly(JsonNode injectContractNode) {
    if (injectContractNode == null || injectContractNode.isNull()) {
      return Optional.empty();
    }
    JsonNode payloadNode = injectContractNode.get("injector_contract_payload");
    if (payloadNode == null || payloadNode.isNull() || payloadNode.isEmpty()) {
      return Optional.empty();
    }
    String externalId =
        payloadNode.has("payload_external_id")
            ? payloadNode.get("payload_external_id").textValue()
            : null;
    if (!hasText(externalId)) {
      return Optional.empty();
    }
    Optional<InjectorContract> contractFromPayload =
        injectorContractRepository.findOne(byPayloadExternalId(externalId));
    if (contractFromPayload.isPresent()) {
      return contractFromPayload;
    }
    Optional<Payload> existingPayload = payloadRepository.findByExternalId(externalId);
    if (existingPayload.isPresent()) {
      return injectorContractRepository.findInjectorContractByPayload(existingPayload.get());
    }
    return Optional.empty();
  }

  private InjectorContract importPayload(
      TxCtx ctx,
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

    // De-duplication (chaining pipeline): before creating a brand-new payload + injector contract,
    // try to reuse an existing equivalent payload the current user is allowed to read. RBAC is a
    // priority filter, never a blocker: a denied/absent candidate is skipped and the cascade
    // continues, ultimately falling back to creation (Step 3) below.
    Optional<InjectorContract> reusableContract =
        findReusableContractForImportedPayload(payloadNode);
    if (reusableContract.isPresent()) {
      return reusableContract.get();
    }

    PayloadCreateInput payloadCreateInput =
        buildPayloadCreateInput(ctx, baseIds, payloadNode, injectContractNode);
    PayloadCreationService.PayloadInjectorContractCreationResult result =
        this.payloadCreationService.createPayload(payloadCreateInput);

    if (result.injectorContract() != null) {
      return result.injectorContract();
    } else {
      log.warn(
          "Payload '{}' (id={}) was created but no injector contract could be synchronised for it:"
              + " no payload-capable injector is registered in tenant {}",
          result.payload().getName(),
          result.payload().getId(),
          TenantContext.getCurrentTenant());
      InjectorContract injectorContract = new InjectorContract();
      injectorContract.setPayload(result.payload());
      return injectorContract;
    }
  }

  /**
   * Attempts to find an existing payload equivalent to the imported one so re-importing the same
   * simulation does not pile up duplicate payloads/contracts. Cascade (STRICT order):
   *
   * <ol>
   *   <li>Step 1 — match by SOURCE payload UUID ({@code payload_id}).
   *   <li>Step 2 — match by name + type-specific content (Executable/FileDrop by the resolved
   *       TARGET document id), then by execution-relevant semantics (platforms, arch, elevation,
   *       cleanup, arguments, prerequisites, expectations) via {@link #hasSameExecutionSemantics}.
   *   <li>Step 3 — no reusable candidate: caller creates a new payload.
   * </ol>
   *
   * <p>For every candidate, RBAC is enforced through {@link PermissionService} on the candidate's
   * injector contract (INJECTOR_CONTRACT/READ, routed to THREAT_ARSENAL). RBAC is a priority
   * filter, never a blocker: a denied/absent candidate is skipped and the cascade continues. When
   * no current user is available (unauthenticated system/scenario imports) dedup is skipped
   * entirely.
   */
  private Optional<InjectorContract> findReusableContractForImportedPayload(JsonNode payloadNode) {
    User currentUser = userService.currentUserOrNull();
    if (currentUser == null) {
      return Optional.empty();
    }

    // Step 1 — match by SOURCE UUID. Tenant-scoped on purpose: findById is a PK load that bypasses
    // the Hibernate tenant filter, so a source UUID colliding with another tenant's payload must
    // never be reused (cross-tenant leak). The UUID alone is NOT trusted as an equivalence proof:
    // the id comes from the import file and a same-tenant collision could otherwise swap in an
    // unrelated payload, so the candidate must also pass the same name + type-specific content +
    // execution-semantics equivalence used by the content-based Step 2.
    String sourcePayloadId = getTextValue(payloadNode, "payload_id");
    if (hasText(sourcePayloadId)) {
      Optional<InjectorContract> reusable =
          payloadRepository
              .findByIdAndTenantId(sourcePayloadId, TenantContext.getCurrentTenant())
              .filter(candidate -> matchesImportedPayloadIdentity(payloadNode, candidate))
              .flatMap(candidate -> readableContractForPayload(candidate, currentUser));
      if (reusable.isPresent()) {
        return reusable;
      }
    }

    // Step 2 — match by NAME + type-specific CONTENT. First RBAC-readable candidate wins.
    for (Payload candidate : findEquivalentPayloadsByNameAndContent(payloadNode)) {
      Optional<InjectorContract> reusable = readableContractForPayload(candidate, currentUser);
      if (reusable.isPresent()) {
        return reusable;
      }
    }

    // Step 3 — no reusable candidate: caller creates a new payload.
    return Optional.empty();
  }

  /**
   * Whether a UUID-matched candidate is genuinely the same payload as the imported node: it must be
   * found by the same coarse name + type-specific content queries AND pass {@link
   * #hasSameExecutionSemantics} (both applied by {@link #findEquivalentPayloadsByNameAndContent}).
   * Used to harden the Step 1 fast path against same-tenant UUID collisions from a crafted or stale
   * import file.
   */
  private boolean matchesImportedPayloadIdentity(JsonNode payloadNode, Payload candidate) {
    return findEquivalentPayloadsByNameAndContent(payloadNode).stream()
        .anyMatch(equivalent -> Objects.equals(equivalent.getId(), candidate.getId()));
  }

  /**
   * Resolves a candidate payload's injector contract and returns it ONLY when the user is allowed
   * to read it. A payload with no injector contract (edge case) or a denied read permission yields
   * empty, treated as "RBAC refused" so the cascade continues. Permission is evaluated via {@link
   * PermissionService} on the INJECTOR_CONTRACT resource (routed to THREAT_ARSENAL grants), i.e.
   * the same transverse RBAC used across the platform — not the deprecated {@code
   * Payload.getGrants()}.
   */
  private Optional<InjectorContract> readableContractForPayload(Payload candidate, User user) {
    return injectorContractRepository
        .findInjectorContractByPayload(candidate)
        .filter(
            contract ->
                permissionService.hasPermission(
                    user,
                    Optional.empty(),
                    contract.getId(),
                    ResourceType.INJECTOR_CONTRACT,
                    Action.READ));
  }

  /**
   * Looks up existing payloads of the same {@code payload_type} discriminator sharing the imported
   * payload's identifying fields. Executable/FileDrop are matched by the attached document ID (the
   * imported file has already been resolved to a TARGET document at this point): document names are
   * non-unique, so a name match could reuse a payload wrapping a different binary. The
   * type-specific queries are a coarse pre-filter; candidates are then narrowed by {@link
   * #hasSameExecutionSemantics} so a payload differing on any execution-relevant field is never
   * reused.
   */
  private List<Payload> findEquivalentPayloadsByNameAndContent(JsonNode payloadNode) {
    String type = getTextValue(payloadNode, "payload_type");
    String name = textOrNull(payloadNode, "payload_name");
    if (!hasText(type) || !hasText(name)) {
      return List.of();
    }
    String tenantId = TenantContext.getCurrentTenant();
    List<Payload> candidates =
        switch (type) {
          case Command.COMMAND_TYPE ->
              payloadRepository.findCommandDuplicates(
                  name,
                  textOrNull(payloadNode, "command_executor"),
                  textOrNull(payloadNode, "command_content"),
                  tenantId);
          case Executable.EXECUTABLE_TYPE -> {
            // executable_file has already been swapped to the TARGET document id above.
            String documentId = textOrNull(payloadNode, "executable_file");
            yield hasText(documentId)
                ? payloadRepository.findExecutableDuplicates(name, documentId, tenantId)
                : List.of();
          }
          case FileDrop.FILE_DROP_TYPE -> {
            // file_drop_file has already been swapped to the TARGET document id above.
            String documentId = textOrNull(payloadNode, "file_drop_file");
            yield hasText(documentId)
                ? payloadRepository.findFileDropDuplicates(name, documentId, tenantId)
                : List.of();
          }
          case DnsResolution.DNS_RESOLUTION_TYPE ->
              payloadRepository.findDnsResolutionDuplicates(
                  name, textOrNull(payloadNode, "dns_resolution_hostname"), tenantId);
          case NetworkTraffic.NETWORK_TRAFFIC_TYPE ->
              payloadRepository.findNetworkTrafficDuplicates(
                  name,
                  textOrNull(payloadNode, "network_traffic_ip_src"),
                  textOrNull(payloadNode, "network_traffic_ip_dst"),
                  intOrNull(payloadNode, "network_traffic_port_src"),
                  intOrNull(payloadNode, "network_traffic_port_dst"),
                  textOrNull(payloadNode, "network_traffic_protocol"),
                  tenantId);
          default -> List.of();
        };
    return candidates.stream()
        .filter(candidate -> hasSameExecutionSemantics(payloadNode, candidate))
        .toList();
  }

  /**
   * Whether an existing candidate payload matches the imported node on the execution-relevant
   * fields that the coarse name + type-specific content queries cannot express: platforms,
   * execution architecture, elevation requirement, cleanup executor/command, arguments,
   * prerequisites and expectations. Reusing a payload that differs on any of these would silently
   * change the runtime behaviour of the imported chain, so any mismatch falls through to creation
   * (safe direction: worst case a duplicate payload is created, never a wrong execution).
   */
  private boolean hasSameExecutionSemantics(JsonNode payloadNode, Payload candidate) {
    if (!jsonStringSet(payloadNode, "payload_platforms")
        .equals(enumNames(candidate.getPlatforms()))) {
      return false;
    }
    String importedArch =
        ofNullable(textOrNull(payloadNode, "payload_execution_arch"))
            .orElse(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES.name());
    if (!importedArch.equals(candidate.getExecutionArch().name())) {
      return false;
    }
    boolean importedElevation = payloadNode.path("payload_elevation_required").asBoolean(false);
    if (importedElevation != candidate.isElevationRequired()) {
      return false;
    }
    if (!Objects.equals(
            textOrNull(payloadNode, "payload_cleanup_executor"), candidate.getCleanupExecutor())
        || !Objects.equals(
            textOrNull(payloadNode, "payload_cleanup_command"), candidate.getCleanupCommand())) {
      return false;
    }
    if (!jsonStringSet(payloadNode, "payload_expectations")
        .equals(enumNames(candidate.getExpectations()))) {
      return false;
    }
    // The expected security platform map drives which collectors are pre-seeded for
    // prevention/detection expectations (PayloadService / InjectExpectationService): two payloads
    // differing only on this map have different runtime expectation behaviour.
    if (!expectedSecurityPlatformsFromNode(payloadNode)
        .equals(expectedSecurityPlatformsFromEntity(candidate.getExpectedSecurityPlatforms()))) {
      return false;
    }
    if (!jsonArrayEquals(payloadNode, "payload_arguments", candidate.getArguments())
        || !jsonArrayEquals(payloadNode, "payload_prerequisites", candidate.getPrerequisites())) {
      return false;
    }
    // Output parsers drive runtime result processing (StructuredOutputUtils): two payloads with
    // identical content but different parsers have different execution semantics. Compared through
    // normalized signatures (type, mode, per-element key/type/rule/name/finding flag and regex
    // groups) so instance-specific ids/timestamps/tags never break the equivalence.
    return outputParserSignaturesFromNode(payloadNode.get("payload_output_parsers"))
        .equals(outputParserSignaturesFromEntities(candidate.getOutputParsers()));
  }

  /** Normalized, order-independent signatures of the imported payload_output_parsers array. */
  private List<String> outputParserSignaturesFromNode(JsonNode parsersNode) {
    List<String> signatures = new ArrayList<>();
    if (parsersNode != null && parsersNode.isArray()) {
      for (JsonNode parser : parsersNode) {
        List<String> elements = new ArrayList<>();
        JsonNode elementsNode = parser.get("output_parser_contract_output_elements");
        if (elementsNode != null && elementsNode.isArray()) {
          for (JsonNode element : elementsNode) {
            List<String> regexGroups = new ArrayList<>();
            JsonNode groupsNode = element.get("contract_output_element_regex_groups");
            if (groupsNode != null && groupsNode.isArray()) {
              for (JsonNode group : groupsNode) {
                regexGroups.add(
                    regexGroupSignature(
                        textOrNull(group, "regex_group_field"),
                        textOrNull(group, "regex_group_index_values")));
              }
            }
            elements.add(
                outputElementSignature(
                    textOrNull(element, "contract_output_element_key"),
                    normalizeOutputElementType(textOrNull(element, "contract_output_element_type")),
                    textOrNull(element, "contract_output_element_rule"),
                    textOrNull(element, "contract_output_element_name"),
                    element.path("contract_output_element_is_finding").asBoolean(false),
                    regexGroups));
          }
        }
        signatures.add(
            outputParserSignature(
                textOrNull(parser, "output_parser_type"),
                textOrNull(parser, "output_parser_mode"),
                elements));
      }
    }
    Collections.sort(signatures);
    return signatures;
  }

  /** Normalized, order-independent signatures of a candidate payload's output parser entities. */
  private List<String> outputParserSignaturesFromEntities(Set<OutputParser> parsers) {
    List<String> signatures = new ArrayList<>();
    if (parsers != null) {
      for (OutputParser parser : parsers) {
        List<String> elements = new ArrayList<>();
        if (parser.getContractOutputElements() != null) {
          for (ContractOutputElement element : parser.getContractOutputElements()) {
            List<String> regexGroups = new ArrayList<>();
            if (element.getRegexGroups() != null) {
              for (RegexGroup group : element.getRegexGroups()) {
                regexGroups.add(regexGroupSignature(group.getField(), group.getIndexValues()));
              }
            }
            elements.add(
                outputElementSignature(
                    element.getKey(),
                    element.getType() != null
                        ? element.getType().getLabel().toLowerCase(Locale.ROOT)
                        : null,
                    element.getRule(),
                    element.getName(),
                    element.isFinding(),
                    regexGroups));
          }
        }
        signatures.add(
            outputParserSignature(
                parser.getType() != null ? parser.getType().name() : null,
                parser.getMode() != null ? parser.getMode().name() : null,
                elements));
      }
    }
    Collections.sort(signatures);
    return signatures;
  }

  /**
   * The export writes {@code contract_output_element_type} as the type LABEL (matched
   * case-insensitively by {@code formatStringToContractOutputType}); normalize to lowercase so the
   * node side aligns with the entity's {@code getType().getLabel()}.
   */
  private static String normalizeOutputElementType(String rawType) {
    return rawType != null ? rawType.toLowerCase(Locale.ROOT) : null;
  }

  private static String outputParserSignature(String type, String mode, List<String> elements) {
    List<String> sorted = new ArrayList<>(elements);
    Collections.sort(sorted);
    return type + "|" + mode + "|" + sorted;
  }

  private static String outputElementSignature(
      String key,
      String type,
      String rule,
      String name,
      boolean isFinding,
      List<String> regexGroups) {
    List<String> sorted = new ArrayList<>(regexGroups);
    Collections.sort(sorted);
    return key + "|" + type + "|" + rule + "|" + name + "|" + isFinding + "|" + sorted;
  }

  private static String regexGroupSignature(String field, String indexValues) {
    return field + ":" + indexValues;
  }

  /**
   * Normalizes the imported {@code payload_expected_security_platforms} object into a map of
   * expectation-type name to platform-type name set; absent/null yields an empty map and empty
   * platform lists are dropped so "absent" and "explicitly empty" compare equal.
   */
  private Map<String, Set<String>> expectedSecurityPlatformsFromNode(JsonNode payloadNode) {
    Map<String, Set<String>> normalized = new LinkedHashMap<>();
    JsonNode mapNode = payloadNode.get("payload_expected_security_platforms");
    if (mapNode != null && mapNode.isObject()) {
      mapNode
          .fields()
          .forEachRemaining(
              entry -> {
                if (entry.getValue() == null || !entry.getValue().isArray()) {
                  return;
                }
                Set<String> platforms = new LinkedHashSet<>();
                entry
                    .getValue()
                    .forEach(
                        value -> {
                          if (value != null && value.isTextual()) {
                            platforms.add(value.asText());
                          }
                        });
                if (!platforms.isEmpty()) {
                  normalized.put(entry.getKey(), platforms);
                }
              });
    }
    return normalized;
  }

  /** Entity-side counterpart of {@link #expectedSecurityPlatformsFromNode}. */
  private Map<String, Set<String>> expectedSecurityPlatformsFromEntity(
      Map<BaseInjectExpectation.EXPECTATION_TYPE, List<SecurityPlatform.SECURITY_PLATFORM_TYPE>>
          expected) {
    Map<String, Set<String>> normalized = new LinkedHashMap<>();
    if (expected != null) {
      expected.forEach(
          (type, platforms) -> {
            if (type == null || platforms == null || platforms.isEmpty()) {
              return;
            }
            Set<String> names = new LinkedHashSet<>();
            platforms.forEach(
                platform -> {
                  if (platform != null) {
                    names.add(platform.name());
                  }
                });
            if (!names.isEmpty()) {
              normalized.put(type.name(), names);
            }
          });
    }
    return normalized;
  }

  /** Reads a JSON array of strings into a set; absent/null fields yield an empty set. */
  private Set<String> jsonStringSet(JsonNode node, String field) {
    JsonNode arrayNode = node.get(field);
    Set<String> values = new LinkedHashSet<>();
    if (arrayNode != null && arrayNode.isArray()) {
      arrayNode.forEach(
          entry -> {
            if (entry != null && entry.isTextual()) {
              values.add(entry.asText());
            }
          });
    }
    return values;
  }

  /** Enum array to name set; null arrays yield an empty set. */
  private Set<String> enumNames(Enum<?>[] values) {
    Set<String> names = new LinkedHashSet<>();
    if (values != null) {
      for (Enum<?> value : values) {
        if (value != null) {
          names.add(value.name());
        }
      }
    }
    return names;
  }

  /**
   * Compares an imported JSON array field with an entity list serialized through the shared mapper,
   * treating absent/null as empty. A structural mismatch (including a serialization-shape
   * difference) yields false, which makes the dedup fall through to creation - the safe direction.
   */
  private boolean jsonArrayEquals(JsonNode node, String field, Object entityValue) {
    JsonNode imported = node.get(field);
    JsonNode importedArray =
        imported != null && imported.isArray() ? imported : mapper.createArrayNode();
    JsonNode entityArray = mapper.valueToTree(entityValue == null ? List.of() : entityValue);
    return importedArray.equals(entityArray);
  }

  /** Reads a text field returning null (not "") for absent/null nodes, without trimming. */
  private String textOrNull(JsonNode node, String fieldName) {
    JsonNode fieldNode = node.get(fieldName);
    return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.textValue() : null;
  }

  /** Reads an int field returning null for absent/null nodes. */
  private Integer intOrNull(JsonNode node, String fieldName) {
    JsonNode fieldNode = node.get(fieldName);
    return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.intValue() : null;
  }

  private List<DetectionRemediationInput> buildDetectionRemediationsJsonNode(JsonNode payloadNode) {
    List<DetectionRemediationInput> detectionRemediationInputs = new ArrayList<>();

    JsonNode remediationsNode = payloadNode.get("payload_detection_remediations");
    if (remediationsNode == null || !remediationsNode.isArray()) {
      return detectionRemediationInputs;
    }

    for (JsonNode detectionNode : remediationsNode) {
      String valuesText = getTextValue(detectionNode, "detection_remediation_values");

      if (valuesText.isEmpty()) {
        continue;
      }

      Optional<SecurityPlatform> securityPlatform =
          resolveDetectionRemediationSecurityPlatform(detectionNode);
      if (securityPlatform.isPresent()) {
        DetectionRemediationInput detectionRemediation = new DetectionRemediationInput();
        detectionRemediation.setValues(valuesText);
        detectionRemediation.setSecurityPlatformId(securityPlatform.get().getId());
        detectionRemediationInputs.add(detectionRemediation);
      } else {
        log.warn("Import Detection Remediations: unresolvable security platform, skipping entry");
      }
    }

    return detectionRemediationInputs;
  }

  /**
   * Resolves the security platform of an imported detection remediation. Recent exports carry the
   * platform id ({@code detection_remediation_security_platform}); legacy exports carry a collector
   * type name ({@code detection_remediation_collector_type}, e.g. {@code openaev_crowdstrike})
   * which is humanized to a platform name, resolved case-insensitively and created as a manual
   * platform when absent - so old exports keep importing without any collector installed.
   */
  private Optional<SecurityPlatform> resolveDetectionRemediationSecurityPlatform(
      JsonNode detectionNode) {
    String platformId = getTextValue(detectionNode, "detection_remediation_security_platform");
    if (!platformId.isEmpty()) {
      Optional<SecurityPlatform> byId = securityPlatformRepository.findById(platformId);
      if (byId.isPresent()) {
        return byId;
      }
    }
    String collectorTypeName = getTextValue(detectionNode, "detection_remediation_collector_type");
    if (collectorTypeName.isEmpty()) {
      return Optional.empty();
    }
    CollectorTypeHumanizer.HumanizedPlatform humanized =
        CollectorTypeHumanizer.humanize(collectorTypeName);
    Optional<SecurityPlatform> byName =
        securityPlatformRepository.findFirstByNameIgnoreCaseOrderByIdAsc(humanized.name());
    if (byName.isPresent()) {
      return byName;
    }
    SecurityPlatform created = new SecurityPlatform();
    created.setName(humanized.name());
    created.setSecurityPlatformType(humanized.type());
    return Optional.of(securityPlatformRepository.save(created));
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

  private List<SkippedWorkflowStep> importWorkflow(
      TxCtx ctx,
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
      return new ArrayList<>();
    }
    // return an exception if the enterprise license is inactive when trying to create a chaining
    // simulation
    if (enterpriseEditionService.isEnterpriseLicenseInactive(
        licenseCacheManager.getEnterpriseEditionInfo())) {
      throw new EnterpriseEditionException("Enterprise Edition license required");
    }

    try {
      // Create the workflow
      Workflow workflow = new Workflow();
      workflow.setStatus(WorkflowStatus.TEMPLATE);
      workflow.setVersion(
          workflowNode.has("workflow_version") ? workflowNode.get("workflow_version").asInt(1) : 1);
      workflow.setEdited(false);

      // Configuration
      // Boolean/int config flags must distinguish "field absent from the JSON or explicitly null"
      // (older/partial export -> apply the business default) from "field explicitly false" (honour
      // it). A plain has(X) && get(X).asBoolean() collapses absent, null and false into false,
      // silently disabling features (asBoolean() on a NullNode returns false).
      //
      // workflow_rate_limit_enabled is INTENTIONALLY the exception: there is no business "enabled
      // by default" constant for rate limiting, so an absent/null field must stay false (not fall
      // back to true). Do not "align" its default with the two true-defaulted fields below.
      workflow.setRateLimitEnabled(
          booleanOrDefault(workflowNode, "workflow_rate_limit_enabled", false));
      if (workflowNode.has("workflow_max_attempts")
          && !workflowNode.get("workflow_max_attempts").isNull()) {
        workflow.setMaxAttempts(workflowNode.get("workflow_max_attempts").asInt());
      }
      if (workflowNode.has("workflow_max_temporal_rate_seconds")
          && !workflowNode.get("workflow_max_temporal_rate_seconds").isNull()) {
        workflow.setMaxTemporalRateSeconds(
            workflowNode.get("workflow_max_temporal_rate_seconds").asLong());
      }
      // Absent/null -> business default true (feature enabled unless the export explicitly
      // disables it).
      workflow.setTimeoutEnabled(booleanOrDefault(workflowNode, "workflow_timeout_enabled", true));
      // Absent -> reuse WorkflowService.DEFAULT_TIMEOUT_SECONDS rather than leaving 0 / entity
      // default.
      workflow.setTimeoutSeconds(
          workflowNode.has("workflow_timeout_seconds")
                  && !workflowNode.get("workflow_timeout_seconds").isNull()
              ? workflowNode.get("workflow_timeout_seconds").asLong()
              : DEFAULT_TIMEOUT_SECONDS);
      // Absent/null -> business default true (matches defaultValue="true" documented on
      // WorkflowConfigurationInput).
      workflow.setSafeModeEnabled(
          booleanOrDefault(workflowNode, "workflow_safe_mode_enabled", true));

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
      List<SkippedWorkflowStep> skippedSteps = new ArrayList<>();
      if (workflowNode.has("workflow_steps")) {
        skippedSteps =
            importWorkflowSteps(
                ctx, workflowNode.get("workflow_steps"), workflow, resolvedContracts, baseIds);
      }
      if (!skippedSteps.isEmpty()) {
        // Unresolvable steps are surfaced to the caller (importData -> ImportResult) so the API can
        // report a partial import to the front.
        log.warn(
            "Chaining import left {} workflow step(s) unresolved and skipped", skippedSteps.size());
      }

      // Import standalone events (root conditions not linked to any step)
      if (workflowNode.has("workflow_standalone_conditions")) {
        // Standalone conditions are never shared with a step: use a fresh per-call map.
        importConditionNodes(
            workflowNode.get("workflow_standalone_conditions"),
            workflow,
            null,
            Map.of(),
            new LinkedHashMap<>());
      }
      return skippedSteps;
    } catch (Exception e) {
      log.warn("Failed to import workflow (chaining)", e);
      throw new ImportException(e);
    }
  }

  private List<SkippedWorkflowStep> importWorkflowSteps(
      TxCtx ctx,
      JsonNode stepsNode,
      Workflow workflow,
      Map<String, String> resolvedContracts,
      Map<String, Base> baseIds) {
    // Map from original step ID to new Step
    Map<String, Step> stepIdMap = new LinkedHashMap<>();
    // resolvedContracts is shared with importInjects to avoid creating duplicate payloads
    // when exercise_injects and workflow_steps reference the same missing injector contract

    // Steps whose injector / injector contract / payload cannot be resolved on the target instance
    // are collected here (surfaced to the caller — subject of the follow-up prompt) instead of
    // being
    // recreated as orphans.
    List<SkippedWorkflowStep> skippedSteps = new ArrayList<>();

    // First pass: create all steps
    for (JsonNode stepNode : stepsNode) {
      String originalStepId = stepNode.has("step_id") ? stepNode.get("step_id").asText() : null;

      // A chaining step referencing an injector contract / payload / injector absent from the
      // target
      // instance must NOT be recreated: skip it (no saveStep, no stepIdMap entry) so no orphan
      // contract/payload is created. Dependent steps may stay blocked — accepted by product.
      Optional<SkippedWorkflowStep> skipReason =
          evaluateChainingStepResolvability(stepNode, resolvedContracts);
      if (skipReason.isPresent()) {
        SkippedWorkflowStep skipped = skipReason.get();
        skippedSteps.add(skipped);
        log.warn(
            "Skipping unresolvable chaining step (title='{}', reason={}, resource='{}')",
            skipped.injectTitle(),
            skipped.type(),
            skipped.resourceName());
        continue;
      }

      // Resolve injector contract in step_data if present. A silent payload-creation failure
      // (transient id-less contract returned by resolveInjectorContract -> importPayload) is
      // surfaced here as a skip, so the step is treated EXACTLY like one rejected upfront by
      // evaluateChainingStepResolvability: no saveStep, no stepIdMap entry, added to skippedSteps.
      StepDataResolution stepDataResolution =
          resolveStepData(ctx, stepNode, resolvedContracts, baseIds, workflow);
      if (stepDataResolution.isFailed()) {
        SkippedWorkflowStep skipped = stepDataResolution.skipped();
        skippedSteps.add(skipped);
        log.warn(
            "Skipping chaining step whose payload creation failed (title='{}', reason={}, resource='{}')",
            skipped.injectTitle(),
            skipped.type(),
            skipped.resourceName());
        continue;
      }
      String stepData = stepDataResolution.stepData();

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

    // Second pass: create conditions for each step.
    // Conditions can be SHARED across steps: an exported root event may be referenced by the
    // step_conditions of several steps (same condition_id). A single Condition entity must then be
    // reused with an additional conditions_steps link, NOT recreated once per step. This map (keyed
    // by the exported condition_id) is shared across every step of this import so an already
    // imported condition is detected on subsequent steps — the import-side equivalent of
    // copiedConditionsByOriginalId in StepService.copyStepsTemplate() (PR #7119).
    Map<String, Condition> importedConditionsByOriginalId = new LinkedHashMap<>();
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

      importConditionNodes(
          conditionsNode, workflow, step, stepIdMap, importedConditionsByOriginalId);
    }
    return skippedSteps;
  }

  /**
   * Evaluates whether a chaining workflow step can be materialised on the target instance. A step
   * is considered <b>unresolvable</b> (and must be skipped, never recreated) when:
   *
   * <ul>
   *   <li>(b) its injector type ({@code injector_contract_injector_type}) is present but no
   *       injector of that type exists on the target ({@link
   *       InjectorService#injectorTypeExists(String)}); or
   *   <li>(a) its injector contract is neither already present on the target for the current tenant
   *       ({@code existsByContractIdAndTenant} / previously resolved) nor resolvable read-only from
   *       the payload external id ({@link #resolveInjectorContractReadOnly}).
   * </ul>
   *
   * Steps carrying no {@code inject_injector_contract} (or unparsable step_data) are always kept
   * and left to {@link #resolveStepData}.
   *
   * @return the skip descriptor when the step is unresolvable, empty when it can be imported
   */
  private Optional<SkippedWorkflowStep> evaluateChainingStepResolvability(
      JsonNode stepNode, Map<String, String> resolvedContracts) {
    JsonNode stepDataNode = stepNode.get("step_data");
    if (stepDataNode == null || stepDataNode.isNull()) {
      return Optional.empty();
    }
    JsonNode dataJson;
    try {
      dataJson = stepDataNode.isTextual() ? mapper.readTree(stepDataNode.asText()) : stepDataNode;
    } catch (Exception e) {
      // Malformed step_data: leave it to resolveStepData's own defensive handling.
      return Optional.empty();
    }
    JsonNode injectContractNode = dataJson.get("inject_injector_contract");
    if (injectContractNode == null || injectContractNode.isNull()) {
      return Optional.empty();
    }

    String injectTitle = getTextValue(dataJson, "inject_title");

    // (b) Injector of the referenced type must exist on the target instance.
    String injectorType = extractInjectorType(injectContractNode);
    if (hasText(injectorType) && !injectorService.injectorTypeExists(injectorType)) {
      return Optional.of(
          new SkippedWorkflowStep(SkippedWorkflowStepType.INJECTOR, injectTitle, injectorType));
    }

    // (a) Injector contract must already exist on the target or be resolvable read-only.
    String injectorContractId = extractInjectorContractId(injectContractNode);
    boolean alreadyResolvable =
        (hasText(injectorContractId)
                && (injectorContractRepository.existsByContractIdAndTenant(
                        injectorContractId, TenantContext.getCurrentTenant())
                    || resolvedContracts.containsKey(injectorContractId)))
            || resolveInjectorContractReadOnly(injectContractNode).isPresent();
    // A contract carrying embedded, self-sufficient payload data (typically a MANUAL payload with
    // no payload_external_id, e.g. a Command payload) can be recreated locally: do NOT skip it here
    // even when it is neither already present nor resolvable read-only — leave resolveStepData to
    // perform the full resolution/creation (and to surface a creation failure as a missing step).
    // A pure reference to an external-collector contract with no embedded payload (e.g.
    // NetExec/Nmap) keeps the original skip behaviour.
    if (!alreadyResolvable && !hasEmbeddedPayloadData(injectContractNode)) {
      String payloadName = extractPayloadName(injectContractNode);
      String resourceName = hasText(payloadName) ? payloadName : injectorType;
      return Optional.of(
          new SkippedWorkflowStep(
              SkippedWorkflowStepType.INJECTOR_CONTRACT, injectTitle, resourceName));
    }
    return Optional.empty();
  }

  /**
   * Whether the injector contract node embeds self-sufficient payload data that can be recreated
   * locally (typically a MANUAL payload without {@code payload_external_id}, e.g. a Command
   * payload). Such steps must not be skipped by {@link #evaluateChainingStepResolvability}: their
   * creation is delegated to {@link #resolveStepData}.
   *
   * <p>"Self-sufficient" requires the fields {@code PayloadUtils.buildPayload()} dereferences
   * unconditionally ({@code payload_type}, {@code payload_name}, {@code payload_source}, {@code
   * payload_status}): a partial shape (e.g. only a stale {@code payload_id}) must take the skip
   * path and be reported as a missing action instead of crashing the recreation mid-import.
   *
   * @return true when {@code injectContractNode} is an object whose {@code
   *     injector_contract_payload} field carries recreatable payload data, false otherwise
   */
  private static boolean hasEmbeddedPayloadData(JsonNode injectContractNode) {
    if (injectContractNode == null || !injectContractNode.isObject()) {
      return false;
    }
    JsonNode payloadNode = injectContractNode.get("injector_contract_payload");
    if (payloadNode == null || !payloadNode.isObject() || payloadNode.isEmpty()) {
      return false;
    }
    return hasNonBlankTextField(payloadNode, "payload_type")
        && hasNonBlankTextField(payloadNode, "payload_name")
        && hasNonBlankTextField(payloadNode, "payload_source")
        && hasNonBlankTextField(payloadNode, "payload_status");
  }

  private static boolean hasNonBlankTextField(JsonNode node, String field) {
    JsonNode fieldNode = node.get(field);
    return fieldNode != null && fieldNode.isTextual() && hasText(fieldNode.asText());
  }

  private static String extractInjectorType(JsonNode injectContractNode) {
    if (injectContractNode == null || !injectContractNode.isObject()) {
      return null;
    }
    JsonNode typeNode = injectContractNode.get("injector_contract_injector_type");
    return typeNode != null && !typeNode.isNull() ? typeNode.asText() : null;
  }

  /**
   * Reads a boolean field treating "absent" and "explicit JSON null" identically: both fall back to
   * {@code defaultValue}. Needed because {@code asBoolean()} on a {@code NullNode} returns {@code
   * false}, which would silently turn a partial export's {@code null} into "explicitly disabled".
   */
  private static boolean booleanOrDefault(JsonNode parent, String field, boolean defaultValue) {
    JsonNode node = parent.get(field);
    return node == null || node.isNull() ? defaultValue : node.asBoolean(defaultValue);
  }

  private static String extractPayloadName(JsonNode injectContractNode) {
    if (injectContractNode == null || !injectContractNode.isObject()) {
      return null;
    }
    JsonNode payloadNode = injectContractNode.get("injector_contract_payload");
    if (payloadNode == null || payloadNode.isNull()) {
      return null;
    }
    JsonNode nameNode = payloadNode.get("payload_name");
    return nameNode != null && !nameNode.isNull() ? nameNode.asText() : null;
  }

  /**
   * Imports a list of condition nodes, optionally linking them to a step.
   *
   * <p>Pass {@code step = null} for standalone conditions (not linked to any step).
   *
   * <p>{@code conditionIdMap} is keyed by the exported {@code condition_id}. It doubles as (1) the
   * map of conditions already resolved in THIS call — used to resolve parent conditions — and (2)
   * the map of ALL conditions already imported across every step of the current import (when the
   * caller passes a shared map). A condition_id already present is reused instead of being
   * recreated, mirroring {@code copiedConditionsByOriginalId} in {@code
   * StepService.copyStepsTemplate()} (PR #7119).
   */
  private void importConditionNodes(
      JsonNode conditionsNode,
      Workflow workflow,
      @Nullable Step step,
      Map<String, Step> stepIdMap,
      Map<String, Condition> conditionIdMap) {

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

        // Shared/duplicate condition reuse: this condition_id was already imported — either for a
        // previous step, earlier in this same array, or duplicated within one step's
        // step_conditions. Reuse the existing Condition entity instead of creating a second one.
        if (originalCondId != null && conditionIdMap.containsKey(originalCondId)) {
          Condition existing = conditionIdMap.get(originalCondId);
          // A root event shared across steps: add the additional conditions_steps link to THIS
          // step (no new entity). Non-root duplicates are already correctly wired through their
          // parent on first creation, so nothing more is required.
          if (step != null && isRoot) {
            chainingConditionService.linkToStep(existing, step, isRoot);
            chainingConditionService.saveCondition(existing);
          }
          iterator.remove();
          progressed = true;
          continue;
        }

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

        MappingType conditionMappingType =
            condNode.has("condition_mapping_type")
                    && !condNode.get("condition_mapping_type").isNull()
                ? MappingType.valueOf(condNode.get("condition_mapping_type").asText())
                : null;
        Condition condition =
            Condition.builder()
                .workflowId(workflow.getId())
                .key(
                    condNode.has("condition_key") && !condNode.get("condition_key").isNull()
                        ? condNode.get("condition_key").asText()
                        : null)
                .keyTypes(
                    ConditionKeyTypesUtils.normalizeForConditionType(
                        condNode.has("condition_key_types")
                                && condNode.get("condition_key_types").isArray()
                            ? StreamSupport.stream(
                                    condNode.get("condition_key_types").spliterator(), false)
                                .filter(Objects::nonNull)
                                .filter(node -> !node.isNull())
                                .map(
                                    node -> mapper.convertValue(node.asText(), PrimitiveType.class))
                                .toList()
                            : condNode.has("condition_key_type")
                                    && !condNode.get("condition_key_type").isNull()
                                ? List.of(
                                    mapper.convertValue(
                                        condNode.get("condition_key_type").asText(),
                                        PrimitiveType.class))
                                : List.of(),
                        conditionType,
                        conditionMappingType))
                .type(conditionType)
                .mappingType(conditionMappingType)
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
  private StepDataResolution resolveStepData(
      TxCtx ctx,
      JsonNode stepNode,
      Map<String, String> resolvedContracts,
      Map<String, Base> baseIds,
      Workflow workflow) {
    if (!stepNode.has("step_data") || stepNode.get("step_data").isNull()) {
      return StepDataResolution.resolved(null);
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
      return StepDataResolution.resolved(
          stepDataNode.isTextual() ? stepDataNode.asText() : stepDataNode.toString());
    }

    // Chaining-only: pre-resolve the contract domains from the inject-level rich format
    // (inject_contract_domains carries domain_name) so importDomains can resolve the bare source
    // ids of injector_contract_domains cross-instance via its baseIds cache — both when recreating
    // the contract (resolveInjectorContract) and when rewriting step_data
    // (rewriteInjectorContractDomains
    // inside sanitizateStepData). No-op when inject_contract_domains is absent.
    resolveInjectContractDomainsFromInjectFormat(dataJson, baseIds);

    // Extract injector contract info from step_data
    JsonNode injectContractNode = dataJson.get("inject_injector_contract");
    if (injectContractNode == null || injectContractNode.isNull()) {
      return StepDataResolution.resolved(
          sanitizateStepData(ctx, dataJson, stepDataRaw, workflow, baseIds));
    }

    String injectorContractId = extractInjectorContractId(injectContractNode);
    if (!hasText(injectorContractId)) {
      return StepDataResolution.resolved(
          sanitizateStepData(ctx, dataJson, stepDataRaw, workflow, baseIds));
    }

    // Contract already exists in DB (tenant-scoped: the composite PK is (tenant_id, id), so the
    // same id can exist in another tenant and must NOT count as present), no resolution needed
    if (injectorContractRepository.existsByContractIdAndTenant(
            injectorContractId, TenantContext.getCurrentTenant())
        && !shouldResolveContractFromStepData(injectContractNode, injectorContractId)) {
      return StepDataResolution.resolved(
          sanitizateStepData(ctx, dataJson, stepDataRaw, workflow, baseIds));
    }

    // Already resolved by a previous step or by importInjects — reuse
    String alreadyResolved = resolvedContracts.get(injectorContractId);
    if (alreadyResolved != null) {
      if (!updateContractIdInStepData(dataJson, alreadyResolved)) {
        return StepDataResolution.resolved(stepDataRaw);
      }
      return StepDataResolution.resolved(
          sanitizateStepData(ctx, dataJson, stepDataRaw, workflow, baseIds));
    }

    if (!(injectContractNode instanceof ObjectNode injectContractObject)) {
      log.warn(
          "Step data references missing injector contract {} in textual form with no payload to recreate",
          injectorContractId);
      return StepDataResolution.resolved(
          sanitizateStepData(ctx, dataJson, stepDataRaw, workflow, baseIds));
    }

    // Contract is missing then resolve using the same logic as importInjects
    JsonNode payloadNode = injectContractObject.get("injector_contract_payload");
    if (payloadNode == null || payloadNode.isNull() || payloadNode.isEmpty()) {
      log.warn(
          "Step data references missing injector contract {} with no payload to recreate",
          injectorContractId);
      return StepDataResolution.resolved(
          sanitizateStepData(ctx, dataJson, stepDataRaw, workflow, baseIds));
    }

    // A partial payload shape (e.g. only a stale payload_id, missing the fields buildPayload()
    // dereferences unconditionally) is NOT recreatable: surface a skipped step instead of letting
    // the recreation throw and roll back the whole import. evaluateChainingStepResolvability
    // normally skips these upfront; this guard protects direct/other entry orders.
    if (!hasEmbeddedPayloadData(injectContractObject)) {
      log.warn(
          "Step data references missing injector contract {} with partial payload data that"
              + " cannot be recreated",
          injectorContractId);
      return StepDataResolution.failed(
          missingContractSkip(dataJson, injectContractObject, injectorContractId));
    }

    InjectorContract resolvedStepContract;
    try {
      resolvedStepContract = resolveInjectorContract(ctx, injectContractObject, baseIds);
    } catch (Exception e) {
      // Recreation failed on unexpected embedded data BEFORE any transactional work (e.g. a
      // malformed field slipping past hasEmbeddedPayloadData in buildPayloadCreateInput, which
      // runs outside PayloadCreationService#createPayload): degrade to a skipped step (partial
      // import with a reported missing action). When the failure escaped createPayload itself,
      // its @Transactional interceptor has already marked the joined import transaction
      // rollback-only - a partial result is impossible (commit would raise
      // UnexpectedRollbackException), so rethrow and let the import fail loudly instead of
      // pretending a partial import succeeded.
      if (isCurrentTransactionMarkedRollbackOnly()) {
        throw e instanceof RuntimeException runtimeException
            ? runtimeException
            : new IllegalStateException(e);
      }
      log.warn(
          "Failed to recreate the payload/contract for missing injector contract {}",
          injectorContractId,
          e);
      return StepDataResolution.failed(
          missingContractSkip(dataJson, injectContractObject, injectorContractId));
    }
    String newContractId = resolvedStepContract != null ? resolvedStepContract.getId() : null;

    // Update step_data and cache the mapping
    if (newContractId != null) {
      resolvedContracts.put(injectorContractId, newContractId);
      if (!updateContractIdInStepData(dataJson, newContractId)) {
        return StepDataResolution.resolved(stepDataRaw);
      }
      return StepDataResolution.resolved(
          sanitizateStepData(ctx, dataJson, stepDataRaw, workflow, baseIds));
    }

    // Creation was attempted (embedded payload present) but failed silently: importPayload returned
    // a transient, never-persisted InjectorContract whose getId() == null. This is distinct from
    // "no contract to resolve": the step references a payload that could not be recreated on the
    // target, so surface it as a missing step (same type/name convention as
    // evaluateChainingStepResolvability) instead of persisting a step pointing to a broken id.
    if (resolvedStepContract != null) {
      log.warn(
          "Payload recreation for missing injector contract {} returned a transient contract"
              + " (payload='{}'): the step cannot reference it and is skipped",
          injectorContractId,
          resolvedStepContract.getPayload() != null
              ? resolvedStepContract.getPayload().getName()
              : null);
      return StepDataResolution.failed(
          missingContractSkip(dataJson, injectContractNode, injectorContractId));
    }

    return StepDataResolution.resolved(
        sanitizateStepData(ctx, dataJson, stepDataRaw, workflow, baseIds));
  }

  /**
   * Whether the surrounding Spring transaction (if any) has been marked rollback-only, e.g. by the
   * {@code @Transactional} interceptor of a nested service call whose exception was caught. When
   * true, converting the failure into a partial result is impossible: the commit would raise {@code
   * UnexpectedRollbackException} anyway.
   */
  private static boolean isCurrentTransactionMarkedRollbackOnly() {
    try {
      return TransactionAspectSupport.currentTransactionStatus().isRollbackOnly();
    } catch (NoTransactionException e) {
      return false;
    }
  }

  /**
   * Builds the skip descriptor for a step whose injector contract / payload could not be resolved
   * or recreated, using the same type/name convention as {@link
   * #evaluateChainingStepResolvability}.
   */
  private SkippedWorkflowStep missingContractSkip(
      JsonNode dataJson, JsonNode injectContractNode, String injectorContractId) {
    String injectTitle = getTextValue(dataJson, "inject_title");
    String payloadName = extractPayloadName(injectContractNode);
    String injectorType = extractInjectorType(injectContractNode);
    String resourceName =
        hasText(payloadName)
            ? payloadName
            : hasText(injectorType) ? injectorType : injectorContractId;
    return new SkippedWorkflowStep(
        SkippedWorkflowStepType.INJECTOR_CONTRACT, injectTitle, resourceName);
  }

  /**
   * Outcome of {@link #resolveStepData}: either the resolved {@code step_data} JSON to persist
   * ({@link #resolved}), or a skip descriptor when a payload creation was attempted but failed
   * silently ({@link #failed}). In the failure case the caller must treat the step exactly like one
   * skipped by {@link #evaluateChainingStepResolvability}: no {@code saveStep}, no {@code
   * stepIdMap} entry, and add the descriptor to the collected skipped steps.
   */
  record StepDataResolution(String stepData, SkippedWorkflowStep skipped) {
    static StepDataResolution resolved(String stepData) {
      return new StepDataResolution(stepData, null);
    }

    static StepDataResolution failed(SkippedWorkflowStep skipped) {
      return new StepDataResolution(null, skipped);
    }

    boolean isFailed() {
      return skipped != null;
    }
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

    // Tenant-scoped on purpose: findById matches only compositeId.id, so with the same id in two
    // tenants it can resolve a foreign contract (or throw NonUniqueResultException when the
    // Hibernate tenant filter is not enabled on the session).
    Optional<InjectorContract> existingContractOpt =
        injectorContractRepository.findByContractIdAndTenant(
            injectorContractId, TenantContext.getCurrentTenant());
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

  private String sanitizateStepData(
      TxCtx ctx, JsonNode dataJson, String fallback, Workflow workflow, Map<String, Base> baseIds) {
    if (!(dataJson instanceof ObjectNode dataObject) || workflow == null) {
      return fallback;
    }
    rewriteInjectInjector(dataObject);
    dataObject.remove("inject_assets");
    dataObject.remove("inject_asset_groups");
    dataObject.remove("inject_teams");
    dataObject.remove("inject_exercise");
    dataObject.remove("inject_scenario");
    // A simulation can be imported on a different instance: the source creator user UUID is
    // meaningless (and may not exist) on the target instance. Always rewrite inject_user to the
    // user executing the import, mirroring InjectExecutionStep.stepData() which sets
    // inject.setUser(...) for a normal inject creation. currentUserOrNull() is used (not
    // currentUser()) because this code path can legitimately run without an authenticated request
    // (see UserService#currentUserOrNull javadoc: "scenario imports"); the null guard below keeps
    // the sanitization non-blocking in that case.
    dataObject.remove("inject_user");
    User currentUser = this.userService.currentUserOrNull();
    if (currentUser != null) {
      dataObject.put("inject_user", currentUser.getId());
    }
    // Rewrite nested tag id references (raw SOURCE-instance UUIDs) to the resolved target tag ids.
    // At run time InjectExecutionStep.getInjectFromDataStep() deserializes step_data into an Inject
    // via MonoIdDeserializerHelper (em.getReference() -> Hibernate proxy, no existence check). A
    // source tag id absent from the target instance triggers EntityNotFoundException as soon as the
    // proxy is initialized (e.g. hashCode() on the Set<Tag>), which bubbles up as a fatal
    // ChainingException. injector_contract_tags in particular is functionally useless at run time:
    // the deserialized InjectorContract is fully replaced a few lines later by a fresh
    // injectorContractRepository.findById(...) read. baseIds is populated up-front by importTags()
    // (exercise_tags / scenario_tags / payload_tags), so every exported tag is already resolved
    // here; any unresolved id is dropped (degraded but non-blocking) rather than left dangling.
    rewriteImportedTagIds(dataObject, "inject_tags", baseIds);
    // Rewrite the inject_documents attachment references: documents are recreated with a NEW UUID
    // on the target instance, so the source ids serialized in step_data must be mapped to the
    // resolved target documents or the imported step silently loses valid attachments at run time.
    rewriteImportedInjectDocuments(dataObject, baseIds);
    JsonNode injectContractNode = dataObject.get("inject_injector_contract");
    if (injectContractNode instanceof ObjectNode injectContractObject) {
      rewriteImportedTagIds(injectContractObject, "injector_contract_tags", baseIds);
      rewriteInjectorContractDomains(injectContractObject, baseIds);
      rewriteInjectorContractAttackPatterns(ctx, injectContractObject, baseIds);
    }
    buildStepTtpFromInjectorContract(dataObject);
    if (workflow.getSimulation() != null) {
      dataObject.put("inject_exercise", workflow.getSimulation().getId());
      dataObject.putNull("inject_scenario");
    } else if (workflow.getScenario() != null) {
      dataObject.put("inject_scenario", workflow.getScenario().getId());
      dataObject.putNull("inject_exercise");
    }
    return serializeStepData(dataObject, fallback);
  }

  private void rewriteInjectInjector(ObjectNode dataObject) {
    JsonNode injectContractNode = dataObject.get("inject_injector_contract");
    String injectorContractId = extractInjectorContractId(injectContractNode);
    if (!hasText(injectorContractId)) {
      return;
    }
    // Tenant-scoped on purpose: the contract id comes from the import file (or was just resolved),
    // and a bare findById matches only compositeId.id — with the same id in two tenants it can
    // resolve a FOREIGN tenant's contract (writing that tenant's injector id into inject_injector)
    // or throw NonUniqueResultException. Same rationale as shouldResolveContractFromStepData().
    Injector resolvedInjector =
        injectorContractRepository
            .findByContractIdAndTenant(injectorContractId, TenantContext.getCurrentTenant())
            .map(InjectorContract::getFirstInjector)
            .orElse(null);
    if (resolvedInjector != null) {
      dataObject.put("inject_injector", resolvedInjector.getId());
    } else {
      // Avoid persisting a dangling SOURCE instance id when the target contract has no linked
      // injector yet (e.g. collector not registered yet).
      dataObject.remove("inject_injector");
    }
  }

  /**
   * Rewrites an array of raw tag UUIDs (as serialized by {@code MultiIdSetSerializer}) in place,
   * mapping each SOURCE-instance tag id to the resolved TARGET tag id via {@code baseIds}
   * (populated up-front by {@link #importTags}). When an id is not in {@code baseIds} but already
   * exists on the target tenant (e.g. re-import on the same instance, where the export carries no
   * root tag object to seed {@code baseIds}), it is kept as-is rather than dropped. Only ids that
   * resolve to nothing on the target are dropped: keeping a dangling id would crash {@code
   * InjectExecutionStep.getInjectFromDataStep()} at run time when the Hibernate proxy is
   * initialized, and this tag field is never used after that deserialization anyway.
   */
  private void rewriteImportedTagIds(ObjectNode parent, String field, Map<String, Base> baseIds) {
    JsonNode tagsNode = parent.get(field);
    if (tagsNode == null || !tagsNode.isArray()) {
      return;
    }
    String tenantId = TenantContext.getCurrentTenant();
    ArrayNode rewritten = mapper.createArrayNode();
    for (JsonNode tagIdNode : tagsNode) {
      if (tagIdNode == null || tagIdNode.isNull() || !tagIdNode.isTextual()) {
        continue;
      }
      String rawId = tagIdNode.asText();
      // baseIds caches every imported entity type under its SOURCE id: only accept a cached TAG,
      // otherwise a source id shared with e.g. a domain would inject that entity's target id into
      // the tag array and recreate the dangling-reference runtime failure this rewrite prevents.
      if (baseIds.get(rawId) instanceof Tag resolvedTag && resolvedTag.getId() != null) {
        rewritten.add(resolvedTag.getId());
      } else if (tagRepository.findByIdAndTenantId(rawId, tenantId).isPresent()) {
        // Not seeded in baseIds but already present on the target tenant: keep it (safe fallback).
        rewritten.add(rawId);
      }
    }
    parent.set(field, rewritten);
  }

  /**
   * Rewrites the {@code inject_documents} array of a serialized step_data inject in place, mapping
   * each SOURCE-instance document id to the resolved TARGET document id via {@code baseIds}
   * (populated by {@link #importDocuments} before the workflow import). Documents are recreated
   * with a NEW UUID on the target instance, so without this rewrite an imported chaining step
   * queries the stale source UUID at run time and silently drops a valid attachment.
   *
   * <p>Elements keep their serialized shape: link objects ({@code MultiModelSerializer} output,
   * matched on {@code document_id}) are rewritten in place, scalar id entries (the defensive shape
   * also accepted by {@code InjectDocumentDeserializer}) are replaced by the resolved id. An id not
   * seeded in {@code baseIds} but already present on the target tenant (re-import on the same
   * instance without a bundled file) is kept as-is. An id that resolves to nothing is dropped: the
   * run-time lookup in {@code InjectExecutionStep#getInjectFromDataStep} is tenant-filtered and
   * would drop the attachment anyway, so dropping here keeps the persisted step data free of dead
   * references.
   */
  private void rewriteImportedInjectDocuments(ObjectNode dataObject, Map<String, Base> baseIds) {
    JsonNode documentsNode = dataObject.get("inject_documents");
    if (documentsNode == null || !documentsNode.isArray()) {
      return;
    }
    ArrayNode rewritten = mapper.createArrayNode();
    for (JsonNode linkNode : documentsNode) {
      String rawId = null;
      if (linkNode instanceof ObjectNode linkObject) {
        JsonNode idNode = linkObject.get("document_id");
        rawId = idNode != null && idNode.isTextual() ? idNode.asText() : null;
      } else if (linkNode != null && linkNode.isTextual()) {
        rawId = linkNode.asText();
      }
      if (!hasText(rawId)) {
        continue;
      }
      String resolvedId = resolveImportedDocumentId(rawId, baseIds);
      if (resolvedId == null) {
        continue;
      }
      if (linkNode instanceof ObjectNode linkObject) {
        linkObject.put("document_id", resolvedId);
        rewritten.add(linkObject);
      } else {
        rewritten.add(resolvedId);
      }
    }
    dataObject.set("inject_documents", rewritten);
  }

  /**
   * Resolves a step_data document reference to a TARGET-instance document id: the {@code baseIds}
   * mapping seeded by the document import first, then a tenant-scoped lookup (re-import on the same
   * instance where the export did not bundle the file), {@code null} when the id resolves to
   * nothing. The fallback is tenant-scoped on purpose: the raw id comes from the import file and a
   * bare {@code findById} could match another tenant's document. A successful fallback is cached
   * back into {@code baseIds}, so an id referenced by several links or steps costs at most one
   * query per import instead of one per occurrence.
   */
  private String resolveImportedDocumentId(String rawId, Map<String, Base> baseIds) {
    if (baseIds.get(rawId) instanceof Document resolvedDocument
        && resolvedDocument.getId() != null) {
      return resolvedDocument.getId();
    }
    return documentRepository
        .findByIdAndTenantId(rawId, TenantContext.getCurrentTenant())
        .map(
            document -> {
              baseIds.put(rawId, document);
              return document.getId();
            })
        .orElse(null);
  }

  /**
   * Rewrites {@code injector_contract_domains} in a serialized step_data contract node so every
   * entry points to a domain that exists on the TARGET instance.
   *
   * <p>At run time {@code InjectExecutionStep.getInjectFromDataStep()} deserializes step_data into
   * an Inject; {@code InjectorContract.domains} is a {@code Set<Domain>} deserialized via {@code
   * MonoIdDeserializerHelper} (em.getReference() -> Hibernate proxy, no existence check, no
   * {@code @NotFound(IGNORE)}). A source-instance domain id absent from the target triggers {@code
   * EntityNotFoundException} as soon as the proxy is initialized (hashCode() on the Set), which
   * bubbles up as a fatal ChainingException — even though that deserialized InjectorContract is
   * discarded and re-read fresh from the DB a few lines later.
   *
   * <p>Resolution reuses {@link #importDomains} (no duplicated logic): baseIds cache first, then
   * {@code domainService.findOptionalByIdAndTenantId} (same id present on target), then {@code
   * domainService.upsert} (find-by-name or create) for object-shaped entries. Bare source ids that
   * resolve to nothing are dropped rather than kept dangling (degraded but non-blocking; the field
   * is never used at run time after deserialization). The array is then replaced with the resolved
   * TARGET domain ids.
   */
  private void rewriteInjectorContractDomains(ObjectNode contractNode, Map<String, Base> baseIds) {
    JsonNode domainsNode = contractNode.get("injector_contract_domains");
    if (domainsNode == null || !domainsNode.isArray()) {
      return;
    }
    List<Domain> resolvedDomains = importDomains(contractNode, "injector_contract_", baseIds);
    ArrayNode rewritten = mapper.createArrayNode();
    for (Domain domain : resolvedDomains) {
      if (domain != null && domain.getId() != null) {
        rewritten.add(domain.getId());
      }
    }
    contractNode.set("injector_contract_domains", rewritten);
  }

  /**
   * Rewrites {@code injector_contract_attack_patterns} — and the nested {@code
   * injector_contract_payload.payload_attack_patterns} — in a serialized step_data contract node so
   * every entry is a plain scalar id pointing to an attack pattern that exists on the TARGET
   * instance.
   *
   * <p>Unlike tags/domains this is NOT a missing-FK problem and would crash even when re-importing
   * on the SAME instance: {@code InjectorContract.attackPatterns} (and {@code
   * Payload.attackPatterns}) is annotated {@code @JsonDeserialize(contentUsing =
   * MonoIdDeserializerHelper.class)} (expects scalar UUIDs), but {@code Mixins.InjectorContract} /
   * {@code Mixins.Payload} disable that serializer ({@code @JsonSerialize(using =
   * JsonSerializer.None.class)} on {@code getAttackPatterns()}), so the export writes FULL OBJECTS.
   * At run time {@code MonoIdDeserializerHelper} calls {@code p.getValueAsString()} on each
   * element: on a JSON object it returns null, so the list element becomes null, and {@code
   * InjectorContract.setAttackPatterns} -> {@code Base.collectIds} then NPEs on {@code
   * element.getId()} (see the reported ChainingException).
   *
   * <p>Resolution reuses {@link #importAttackPattern} (no duplicated logic) for the OBJECT entries
   * the export writes: baseIds cache first, then resolution by {@code attack_pattern_external_id}
   * (MITRE ATT&amp;CK id), then creation. SCALAR entries (bare UUIDs, e.g. a re-import on the same
   * instance where the array was already normalized) are kept when the id still exists on the
   * target tenant, so a valid existing reference is not silently wiped. Each array is replaced with
   * the resolved TARGET scalar ids; entries that resolve to nothing are dropped (degraded but
   * non-blocking; the field is never used at run time — the InjectorContract is re-read fresh from
   * the DB right after in getInjectFromDataStep()).
   */
  private void rewriteInjectorContractAttackPatterns(
      TxCtx ctx, ObjectNode contractNode, Map<String, Base> baseIds) {
    rewriteAttackPatternArray(ctx, contractNode, "injector_contract_", baseIds);
    JsonNode payloadNode = contractNode.get("injector_contract_payload");
    if (payloadNode instanceof ObjectNode payloadObject) {
      rewriteAttackPatternArray(ctx, payloadObject, "payload_", baseIds);
    }
  }

  /**
   * Rebuilds {@code inject_attack_patterns} and {@code inject_kill_chain_phases} of a step_data
   * from the target injector contract, which owns these fields at run time (#7577).
   */
  private void buildStepTtpFromInjectorContract(ObjectNode dataObject) {
    String injectorContractId =
        extractInjectorContractId(dataObject.get("inject_injector_contract"));
    InjectorContract contract =
        hasText(injectorContractId)
            ? injectorContractRepository
                .findByContractIdAndTenant(injectorContractId, TenantContext.getCurrentTenant())
                .orElse(null)
            : null;
    if (contract == null) {
      return;
    }

    ArrayNode attackPatternsNode = mapper.createArrayNode();
    ArrayNode killChainPhasesNode = mapper.createArrayNode();
    Set<String> seenKillChainPhaseIds = new LinkedHashSet<>();
    for (AttackPattern attackPattern : contract.getAttackPatterns()) {
      if (attackPattern == null || attackPattern.getId() == null) {
        continue;
      }
      attackPatternsNode.add(mapper.valueToTree(attackPattern));
      for (KillChainPhase killChainPhase : attackPattern.getKillChainPhases()) {
        if (killChainPhase != null
            && killChainPhase.getId() != null
            && seenKillChainPhaseIds.add(killChainPhase.getId())) {
          killChainPhasesNode.add(mapper.valueToTree(killChainPhase));
        }
      }
    }
    dataObject.set("inject_attack_patterns", attackPatternsNode);
    dataObject.set("inject_kill_chain_phases", killChainPhasesNode);
  }

  private void rewriteAttackPatternArray(
      TxCtx ctx, ObjectNode node, String prefix, Map<String, Base> baseIds) {
    JsonNode attackPatternsNode = node.get(prefix + "attack_patterns");
    if (attackPatternsNode == null || !attackPatternsNode.isArray()) {
      return;
    }
    // importAttackPattern resolves both OBJECT entries (baseIds cache, external id, creation) and
    // SCALAR entries (baseIds cache, tenant-scoped existence check) — see its javadoc.
    List<AttackPattern> resolvedAttackPatterns = importAttackPattern(ctx, node, prefix, baseIds);
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    for (AttackPattern attackPattern : resolvedAttackPatterns) {
      if (attackPattern != null && attackPattern.getId() != null) {
        ids.add(attackPattern.getId());
      }
    }
    ArrayNode rewritten = mapper.createArrayNode();
    ids.forEach(rewritten::add);
    node.set(prefix + "attack_patterns", rewritten);
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

  /** Nature of the unresolvable dependency that caused a chaining step to be skipped. */
  public enum SkippedWorkflowStepType {
    /** No injector of the referenced type exists on the target instance. */
    INJECTOR,
    /** The injector contract / payload could not be resolved on the target instance. */
    INJECTOR_CONTRACT
  }

  /**
   * Descriptor of a chaining workflow step skipped during import because its injector, injector
   * contract or payload could not be resolved on the target instance.
   *
   * @param type the nature of the missing dependency
   * @param injectTitle the human-readable step title ({@code inject_title})
   * @param resourceName the injector type name (for {@link SkippedWorkflowStepType#INJECTOR}) or
   *     the payload name (for {@link SkippedWorkflowStepType#INJECTOR_CONTRACT})
   */
  public record SkippedWorkflowStep(
      SkippedWorkflowStepType type, String injectTitle, String resourceName) {}
}
