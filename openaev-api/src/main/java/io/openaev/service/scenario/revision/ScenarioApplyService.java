package io.openaev.service.scenario.revision;

import static io.openaev.service.scenario.revision.ScenarioContentReader.CONTRACT_PREFIX;
import static io.openaev.service.scenario.revision.ScenarioContentReader.INJECT_IDS;
import static io.openaev.service.scenario.revision.ScenarioContentReader.PAYLOAD_PREFIX;
import static io.openaev.service.scenario.revision.ScenarioContentReader.STEP_IDS;
import static io.openaev.service.scenario.revision.ScenarioMerge.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.catalog.CatalogManifest.PayloadRef;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectDependenciesRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.service.VariableService;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.catalog.CatalogPayloadService;
import io.openaev.service.catalog.CatalogRequirementsService;
import io.openaev.service.catalog.CatalogService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.content.ContentHistoryService;
import io.openaev.service.payload.PayloadVersionService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Applies content to an installed catalogue scenario (ADR-018, section 6): a release for an update,
 * a revision for a rollback, both through the same three-way merge. The configuration is never
 * written; a revision of the current state is taken first.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ScenarioApplyService {

  /** A release ({@code version}) or a revision ({@code revisionId}) to apply. */
  public record Target(String version, String revisionId) {
    boolean rollback() {
      return revisionId != null;
    }
  }

  public record Preview(
      Scenario scenario,
      Target target,
      String targetVersion,
      ScenarioMerge.Result merge,
      int lastRevision,
      String localHash,
      List<String> payloadsToInstall,
      List<String> revokedPayloads) {}

  private record Applied(
      ObjectNode content, ObjectNode base, String version, Set<String> referencing) {}

  private final ScenarioRepository scenarioRepository;
  private final InjectRepository injectRepository;
  private final InjectDependenciesRepository injectDependenciesRepository;
  private final InjectService injectService;
  private final InjectorContractService injectorContractService;
  private final VariableService variableService;
  private final PayloadVersionService payloadVersionService;
  private final CatalogService catalogService;
  private final CatalogPayloadService catalogPayloadService;
  private final AutonomousRunService autonomousRunService;
  private final ContentHistoryService contentHistoryService;
  private final ScenarioRevisionService scenarioRevisionService;
  private final ScenarioContentReader reader;
  private final WorkflowService workflowService;
  private final ObjectMapper mapper;

  // -- PREVIEW --

  @Transactional(readOnly = true)
  public Preview preview(
      String scenarioId, Target target, Map<String, Choice> choices, Map<String, JsonNode> edits) {
    Scenario scenario = installed(scenarioRepository.findById(scenarioId));
    return previewOf(scenario, target, choices, edits);
  }

  private Preview previewOf(
      Scenario scenario, Target target, Map<String, Choice> choices, Map<String, JsonNode> edits) {
    refuseChained(scenario);
    Collection<Inject> injects = injectService.findByScenarioId(scenario.getId());
    ObjectNode local = scenarioRevisionService.local(scenario, injects);
    ObjectNode base = scenarioRevisionService.base(scenario, injects);
    Applied applied = applied(scenario, target);
    ScenarioMerge.Result merge =
        ScenarioMerge.merge(base, applied.content(), local, choices, edits);
    refuseUnwritable(merge, local, applied);
    List<String> toInstall = new ArrayList<>();
    List<String> revoked = new ArrayList<>();
    payloadContracts(merge.merged())
        .forEach(
            contract -> {
              String[] parts = contract.substring(PAYLOAD_PREFIX.length()).split("\\|", 3);
              Optional<Payload> version = installedVersion(parts[0], parts[1], parts[2]);
              if (version.isEmpty()) {
                toInstall.add(parts[1] + " " + parts[2]);
              } else if (!payloadVersionService.revokedIds(List.of(version.get())).isEmpty()) {
                revoked.add(parts[1] + " " + parts[2]);
              }
            });
    return new Preview(
        scenario,
        target,
        applied.version(),
        merge,
        scenarioRevisionService.lastNumber(scenario.getId()),
        hash(local),
        toInstall,
        revoked);
  }

  // -- APPLY --

  /**
   * Applies the target with the client's choices. Refused while a conflict has no choice, while an
   * autonomous run is active, or when the scenario changed since the preview ({@code localHash}).
   */
  public Preview apply(
      TxCtx ctx,
      String scenarioId,
      Target target,
      Map<String, Choice> choices,
      Map<String, JsonNode> edits,
      String localHash)
      throws Exception {
    Scenario scenario = installed(scenarioRepository.findForUpdate(scenarioId));
    if (scenario.isUninstalled()) {
      throw new BadRequestException("An uninstalled scenario is read-only");
    }
    if (scenario.isBasePending()) {
      throw new BadRequestException("The base of this scenario is not computed yet");
    }
    if (autonomousRunService.hasActiveRun(scenarioId)) {
      throw new BadRequestException("Stop the autonomous run first");
    }
    Preview preview = previewOf(scenario, target, choices, edits);
    if (localHash != null && !localHash.equals(preview.localHash())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The scenario changed: run the preview again");
    }
    if (!preview.merge().resolved()) {
      throw new BadRequestException(
          "Choose a side, or type a valid value, for each conflict: "
              + String.join(", ", preview.merge().unresolved()));
    }
    if (!target.rollback() && !preview.revokedPayloads().isEmpty()) {
      throw new BadRequestException(
          "This version runs a revoked payload: " + String.join(", ", preview.revokedPayloads()));
    }
    String fromVersion = scenario.getVersion();
    scenarioRevisionService.take(
        scenario,
        target.rollback() ? ScenarioRevision.Reason.ROLLBACK : ScenarioRevision.Reason.UPDATE);

    Applied applied = applied(scenario, target);
    Map<String, InjectorContract> contracts = resolveContracts(ctx, scenario, preview);
    writeScenario(scenario, preview.merge().merged());
    writeVariables(scenario, preview.merge().merged());
    writeInjects(scenario, preview.merge().merged(), applied.base(), contracts);
    writeBase(scenario, applied.base());
    scenario.setVersion(applied.version());
    scenarioRepository.save(scenario);
    if (target.rollback()) {
      contentHistoryService.scenarioRolledBack(scenario, fromVersion);
    } else {
      contentHistoryService.scenarioUpdated(scenario, fromVersion);
    }
    return preview;
  }

  /** After a first install: the base is the package as the catalogue wrote it. */
  public void recordInstall(Scenario scenario, CatalogItem item) {
    ObjectNode base = reader.fromPackage(packageRoot(item), payloadVersions(item));
    Map<String, Inject> byCatalogId = new HashMap<>();
    injectService
        .findByScenarioId(scenario.getId())
        .forEach(i -> Optional.ofNullable(i.getCatalogId()).ifPresent(c -> byCatalogId.put(c, i)));
    base.path(INJECTS)
        .properties()
        .forEach(
            e ->
                Optional.ofNullable(byCatalogId.get(e.getKey()))
                    .ifPresent(
                        inject -> {
                          inject.setBaseContent(e.getValue());
                          injectRepository.save(inject);
                        }));
    writeBase(scenario, base);
    scenario.setCatalogRepository(item.getRepository());
    scenarioRepository.save(scenario);
    scenarioRevisionService.take(scenario, ScenarioRevision.Reason.INSTALL);
  }

  // -- TARGET --

  private Applied applied(Scenario scenario, Target target) {
    if (target.rollback()) {
      ScenarioRevision revision =
          scenarioRevisionService.find(scenario.getId(), target.revisionId());
      JsonNode snapshot = revision.getSnapshot();
      return new Applied(
          (ObjectNode) snapshot.get(ScenarioRevisionService.CONTENT),
          (ObjectNode) snapshot.get(ScenarioRevisionService.BASE),
          revision.getCatalogVersion(),
          Set.of());
    }
    CatalogItem item =
        catalogService
            .find(
                ContentType.SCENARIO,
                scenario.getCatalogRepository(),
                scenario.getCatalogId(),
                target.version())
            .orElseThrow(() -> new ElementNotFoundException("Catalogue scenario not found"));
    JsonNode root = packageRoot(item);
    if (CatalogRequirementsService.isChained(item) || hasWorkflow(root)) {
      throw new BadRequestException(CatalogRequirementsService.CHAINED_NOT_SUPPORTED);
    }
    ObjectNode content = reader.fromPackage(root, payloadVersions(item));
    return new Applied(content, content, item.getVersion(), reader.keysWithReferences(root));
  }

  /** Refused while the steps of a chained scenario are not part of its content. */
  public boolean isChained(CatalogItem item) {
    return CatalogRequirementsService.isChained(item) || hasWorkflow(packageRoot(item));
  }

  private void refuseChained(Scenario scenario) {
    try {
      if (workflowService.findWorkflowTemplateByScenarioId(scenario.getId()).isPresent()) {
        throw new BadRequestException(CatalogRequirementsService.CHAINED_NOT_SUPPORTED);
      }
    } catch (ChainingException e) {
      throw new BadRequestException(CatalogRequirementsService.CHAINED_NOT_SUPPORTED);
    }
  }

  // Not written by an update yet: refused rather than reported as done.
  private static void refuseUnwritable(
      ScenarioMerge.Result merge, ObjectNode local, Applied applied) {
    JsonNode materials = merge.merged().get(MATERIALS);
    if (materials != null && !materials.equals(local.path(MATERIALS))) {
      throw new BadRequestException(
          "This version changes articles, objectives or lessons: not supported yet");
    }
    merge
        .merged()
        .path(INJECTS)
        .properties()
        .forEach(
            e -> {
              if (applied.referencing().contains(e.getKey())
                  && !e.getValue().equals(local.path(INJECTS).get(e.getKey()))) {
                throw new BadRequestException(
                    "This version changes an inject that uses a challenge, an article or a"
                        + " document: not supported yet");
              }
            });
  }

  private static boolean hasWorkflow(JsonNode root) {
    JsonNode workflow = root.get("scenario_workflow");
    return workflow != null && !workflow.isNull() && !workflow.isEmpty();
  }

  /** The normalized contract of each payload version a release names, by external ID. */
  public Map<String, String> payloadVersions(CatalogItem item) {
    Map<String, String> versions = new HashMap<>();
    for (PayloadRef ref : catalogService.payloadRefs(item)) {
      versions.put(
          ref.externalId(),
          ScenarioContentReader.payloadContract(
              payloadRepository(ref, item), ref.externalId(), ref.version()));
    }
    return versions;
  }

  // A reference names no repository: the one releasing it, else the one installed, else the
  // scenario's own.
  public String payloadRepository(PayloadRef ref, CatalogItem scenarioItem) {
    return catalogService
        .find(ContentType.PAYLOAD, null, ref.externalId(), ref.version())
        .map(CatalogItem::getRepository)
        .or(
            () -> {
              List<PayloadIdentity> installed =
                  payloadVersionService.findManagedIdentities(ref.externalId());
              return installed.size() == 1
                  ? Optional.of(installed.getFirst().getRepository())
                  : Optional.empty();
            })
        .orElse(scenarioItem.getRepository());
  }

  private JsonNode packageRoot(CatalogItem item) {
    byte[] archive = catalogService.readVerifiedPackage(item);
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory() || !entry.getName().endsWith(".json")) {
          continue;
        }
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        zip.transferTo(json);
        JsonNode root = mapper.readTree(json.toByteArray());
        if (root.has("scenario_information")) {
          return root;
        }
      }
    } catch (Exception e) {
      throw new BadRequestException("Unreadable catalogue package");
    }
    throw new BadRequestException("The package holds no scenario");
  }

  // -- PAYLOADS --

  private static Set<String> payloadContracts(ObjectNode merged) {
    Set<String> contracts = new TreeSet<>();
    merged
        .path(INJECTS)
        .forEach(
            element -> {
              String contract = element.path(CONTRACT).asText(null);
              if (contract != null && contract.startsWith(PAYLOAD_PREFIX)) {
                contracts.add(contract);
              }
            });
    return contracts;
  }

  private Optional<Payload> installedVersion(String repository, String externalId, String version) {
    return payloadVersionService
        .findManagedIdentity(repository, externalId)
        .filter(identity -> !identity.isUninstalled())
        .flatMap(
            identity ->
                payloadVersionService.findVersions(identity).stream()
                    .filter(p -> version.equals(p.getVersion()))
                    .findFirst());
  }

  /** Installs the payload versions the content runs, then each contract it names. */
  private Map<String, InjectorContract> resolveContracts(
      TxCtx ctx, Scenario scenario, Preview preview) throws Exception {
    Map<String, InjectorContract> contracts = new HashMap<>();
    String tenantId = scenario.getTenant().getId();
    for (JsonNode element : preview.merge().merged().path(INJECTS)) {
      String contract = element.path(CONTRACT).asText(null);
      if (contract == null || contracts.containsKey(contract)) {
        continue;
      }
      if (contract.startsWith(PAYLOAD_PREFIX)) {
        String[] parts = contract.substring(PAYLOAD_PREFIX.length()).split("\\|", 3);
        Payload payload =
            installedVersion(parts[0], parts[1], parts[2]).isPresent()
                ? installedVersion(parts[0], parts[1], parts[2]).get()
                : catalogPayloadService.install(ctx, tenantId, parts[0], parts[1], parts[2]);
        contracts.put(contract, injectorContractService.findByPayload(payload));
      } else if (contract.startsWith(CONTRACT_PREFIX)) {
        try {
          contracts.put(
              contract,
              injectorContractService.injectorContract(
                  contract.substring(CONTRACT_PREFIX.length())));
        } catch (ElementNotFoundException e) {
          throw new BadRequestException(
              "Payload not found for " + element.path(TEXT).path("title").asText());
        }
      } else {
        throw new BadRequestException(
            "The release does not name the payload of "
                + element.path(TEXT).path("title").asText());
      }
    }
    return contracts;
  }

  // -- WRITE --

  private void writeScenario(Scenario scenario, ObjectNode merged) {
    JsonNode block = merged.path(SCENARIO);
    scenario.setName(block.path("name").asText(scenario.getName()));
    scenario.setDescription(block.path("description").asText(null));
    scenario.setCategory(block.path("category").asText(null));
    String severity = block.path("severity").asText(null);
    if (severity != null) {
      Arrays.stream(Scenario.SEVERITY.values())
          .filter(s -> s.name().equalsIgnoreCase(severity))
          .findFirst()
          .ifPresent(scenario::setSeverity);
    }
  }

  private void writeVariables(Scenario scenario, ObjectNode merged) {
    JsonNode wanted = merged.path(VARIABLES);
    Map<String, Variable> current = new HashMap<>();
    variableService
        .variablesFromScenario(scenario.getId())
        .forEach(v -> current.put(v.getKey(), v));
    current.forEach(
        (key, variable) -> {
          if (!wanted.has(key)) {
            variableService.deleteVariable(variable.getId());
          }
        });
    wanted
        .properties()
        .forEach(
            e -> {
              Variable variable = current.get(e.getKey());
              String value = e.getValue().isNull() ? null : e.getValue().asText();
              if (variable == null) {
                variable = new Variable();
                variable.setKey(e.getKey());
                variable.setScenario(scenario);
                variable.setValue(value);
                variableService.createVariable(variable);
              } else if (!Objects.equals(variable.getValue(), value)) {
                variable.setValue(value);
                variableService.updateVariable(variable);
              }
            });
  }

  private void writeInjects(
      Scenario scenario,
      ObjectNode merged,
      ObjectNode newBase,
      Map<String, InjectorContract> contracts) {
    Map<String, Inject> byKey = new HashMap<>();
    injectService
        .findByScenarioId(scenario.getId())
        .forEach(i -> byKey.put(ScenarioContentReader.key(i), i));
    JsonNode wanted = merged.path(INJECTS);
    JsonNode baseInjects = newBase.path(INJECTS);
    Map<String, Inject> written = new HashMap<>();
    Set<String> kept = new HashSet<>();
    wanted
        .properties()
        .forEach(
            e -> {
              String key = e.getKey();
              Inject inject = byKey.get(key);
              String catalogId = key.startsWith(LOCAL_PREFIX) ? null : key;
              if (inject == null && key.startsWith(LOCAL_PREFIX)) {
                // A removed catalogue inject the client kept: it becomes his own.
                inject = byKey.get(key.substring(LOCAL_PREFIX.length()));
              }
              if (inject == null) {
                inject = new Inject();
                inject.setScenario(scenario);
                inject.setTenant(scenario.getTenant());
                inject.setEnabled(true);
              } else {
                kept.add(ScenarioContentReader.key(inject));
              }
              inject.setCatalogId(catalogId);
              JsonNode element = e.getValue();
              inject.setInjectorContract(contracts.get(element.path(CONTRACT).asText()));
              inject.setTitle(element.path(TEXT).path("title").asText(null));
              inject.setDescription(element.path(TEXT).path("description").asText(null));
              inject.setContent(content(inject.getContent(), element));
              inject.setDependsDuration(element.path(TIMING).path("depends_duration").asLong(0));
              JsonNode base = catalogId == null ? null : baseInjects.get(catalogId);
              inject.setBaseContent(base != null && base.size() > 0 ? base : null);
              written.put(key, injectRepository.save(inject));
            });
    byKey.forEach(
        (key, inject) -> {
          if (!kept.contains(key)) {
            injectRepository.delete(inject);
          }
        });
    writeDependencies(wanted, written);
  }

  // Content and arguments from the merge, configuration (targets) from the inject as it is.
  private ObjectNode content(ObjectNode current, JsonNode element) {
    ObjectNode content = mapper.createObjectNode();
    element.path(CONTENT).properties().forEach(e -> content.set(e.getKey(), e.getValue()));
    element.path(ARGUMENTS).properties().forEach(e -> content.set(e.getKey(), e.getValue()));
    if (current != null) {
      ScenarioConfigFields.INJECT_CONTENT_KEYS.forEach(
          key -> {
            if (current.has(key)) {
              content.set(key, current.get(key));
            }
          });
    }
    return content;
  }

  private void writeDependencies(JsonNode wanted, Map<String, Inject> written) {
    written.forEach(
        (key, inject) -> {
          List<InjectDependency> dependencies = new ArrayList<>();
          for (JsonNode dependency : wanted.path(key).path(TIMING).path(DEPENDS_ON)) {
            String parentKey = dependency.path(PARENT).asText();
            // A parent the client kept after the catalogue removed it is written as a local one.
            Inject parent = written.getOrDefault(parentKey, written.get(LOCAL_PREFIX + parentKey));
            if (parent == null) {
              continue;
            }
            InjectDependency link = new InjectDependency();
            link.getCompositeId().setInjectParent(parent);
            link.getCompositeId().setInjectChildren(inject);
            JsonNode condition = dependency.get("condition");
            if (condition != null && !condition.isNull()) {
              link.setInjectDependencyCondition(
                  mapper.convertValue(
                      condition, InjectDependencyConditions.InjectDependencyCondition.class));
            }
            dependencies.add(link);
          }
          if (!inject.getDependsOn().isEmpty()) {
            injectDependenciesRepository.deleteAll(inject.getDependsOn());
            inject.getDependsOn().clear();
          }
          inject.getDependsOn().addAll(dependencies);
          injectRepository.save(inject);
        });
  }

  /** Scenario blocks and the catalogue IDs of the base; each inject keeps its own. */
  private void writeBase(Scenario scenario, ObjectNode base) {
    ObjectNode stored = mapper.createObjectNode();
    stored.set(SCENARIO, base.path(SCENARIO));
    stored.set(MATERIALS, base.path(MATERIALS));
    stored.set(VARIABLES, base.path(VARIABLES));
    ArrayNode injectIds = stored.putArray(INJECT_IDS);
    base.path(INJECTS).fieldNames().forEachRemaining(injectIds::add);
    stored.putArray(STEP_IDS);
    scenario.setBaseContent(stored);
  }

  // -- HELPERS --

  private Scenario installed(Optional<Scenario> scenario) {
    Scenario found = scenario.orElseThrow(() -> new ElementNotFoundException("Scenario not found"));
    if (found.getCatalogId() == null) {
      throw new BadRequestException("This scenario was not installed from the catalogue");
    }
    return found;
  }

  private static String hash(JsonNode content) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(content.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
