package io.openaev.service.catalog;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSETS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TEAMS;
import static io.openaev.service.ImportService.EXPORT_ENTRY_SCENARIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.catalog.CatalogInstallInput;
import io.openaev.api.catalog.CatalogInstallOutput;
import io.openaev.api.catalog.CatalogInstallPreviewOutput;
import io.openaev.api.catalog.CatalogInstallPreviewOutput.InjectPreview;
import io.openaev.api.catalog.CatalogInstallPreviewOutput.TargetLabel;
import io.openaev.api.catalog.CatalogManifest.PayloadRef;
import io.openaev.api.catalog.CatalogMissingRequirement;
import io.openaev.api.catalog.CatalogScenarioVersionsOutput;
import io.openaev.api.catalog.ScenarioTargetsOutput;
import io.openaev.api.threat_arsenal.ContentHistoryOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.importer.ImportResult;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import io.openaev.service.ImportService;
import io.openaev.service.TeamService;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.content.ContentHistoryService;
import io.openaev.service.organization.OrganizationService;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.scenario.revision.ScenarioApplyService;
import io.openaev.utils.SemanticVersion;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Installs a catalogue scenario, one instance per scenario (ADR-018), with its targets (F12). Every
 * payload it runs must be a version its release names, never revoked. Updates and rollbacks go
 * through {@link ScenarioApplyService}.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CatalogInstallService {

  private static final List<String> TARGET_FIELDS =
      List.of(
          CONTRACT_ELEMENT_CONTENT_KEY_ASSETS,
          CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS,
          CONTRACT_ELEMENT_CONTENT_KEY_TEAMS);

  // Free-text hosts of an inject with a target selector (nmap, nuclei...).
  static final String MANUAL = "manual";
  private static final String SELECTOR = "target_selector";
  private static final String MANUAL_TARGETS = "targets";
  private static final Map<String, String> SELECTOR_VALUES =
      Map.of(
          CONTRACT_ELEMENT_CONTENT_KEY_ASSETS, "assets",
          CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS, "asset-groups",
          MANUAL, "manual");

  private final CatalogService catalogService;
  private final CatalogRequirementsService catalogRequirementsService;
  private final ImportService importService;
  private final ScenarioApplyService scenarioApplyService;
  private final ScenarioService scenarioService;
  private final InjectService injectService;
  private final PayloadVersionService payloadVersionService;
  private final ContentHistoryService contentHistoryService;
  private final OrganizationService organizationService;
  private final AutonomousRunService autonomousRunService;
  private final AssetService assetService;
  private final AssetGroupService assetGroupService;
  private final TeamService teamService;
  private final InjectorContractContentUtils injectorContractContentUtils;
  private final ObjectMapper mapper;

  // -- LIST --

  /**
   * The drawer content: each packaged inject, its target kinds, and what the last install gives.
   */
  @Transactional(readOnly = true)
  public CatalogInstallPreviewOutput preview(String externalId, String version) {
    CatalogItem item = findInstallable(externalId, version);
    Optional<Scenario> previous = previousInstall(externalId, version);
    Map<String, Inject> previousInjects = previousInjects(previous);
    List<InjectPreview> injects = new ArrayList<>();
    for (JsonNode inject : packagedInjects(readPackage(item))) {
      String catalogId = catalogId(inject);
      JsonNode contract = inject.get("inject_injector_contract");
      List<String> fields =
          contract == null ? List.of() : targetFields(text(contract, "injector_contract_content"));
      Inject before = catalogId == null ? null : previousInjects.get(catalogId);
      boolean carried = before != null && isTargeted(before, fields);
      JsonNode content = inject.get("inject_content");
      List<TargetLabel> labels =
          carried ? currentTargets(before, fields) : packagedTargets(content, fields);
      injects.add(
          new InjectPreview(
              catalogId,
              text(inject, "inject_title"),
              fields,
              before == null,
              fields.isEmpty() || !labels.isEmpty(),
              fields.contains(MANUAL)
                  ? (carried ? selectorKind(before.getContent()) : selectorKind(content))
                  : null,
              labels));
    }
    List<CatalogMissingRequirement> missing =
        catalogRequirementsService.checker().missingPrerequisites(item);
    return new CatalogInstallPreviewOutput(
        previous.map(Scenario::getVersion).orElse(null),
        previous.map(Scenario::getId).orElse(null),
        injects,
        missing.stream().map(CatalogMissingRequirement::label).toList(),
        missing);
  }

  /** Every release of an installed scenario, which ones are installed, and its history (F7). */
  @Transactional(readOnly = true)
  public CatalogScenarioVersionsOutput versions(String scenarioId) {
    Scenario scenario = installedScenario(scenarioId);
    String catalogId = scenario.getCatalogId();
    Map<String, String> installed =
        scenarioService.findInstalledCatalogVersions().getOrDefault(catalogId, Map.of());
    List<CatalogItem> items =
        catalogService.findVersions(ContentType.SCENARIO, catalogId).stream()
            .sorted(Comparator.comparing(CatalogItem::getVersion, SemanticVersion.ORDER).reversed())
            .toList();
    String latest = items.isEmpty() ? null : items.getFirst().getVersion();
    List<CatalogScenarioVersionsOutput.Version> versions =
        items.stream()
            .map(
                i ->
                    new CatalogScenarioVersionsOutput.Version(
                        i.getVersion(),
                        i.getStatus(),
                        i.getChangelog(),
                        i.getPublishedAt(),
                        installed.get(i.getVersion()),
                        i.getVersion().equals(scenario.getVersion()),
                        i.getVersion().equals(latest),
                        installed.containsKey(i.getVersion())
                            ? scenarioService
                                .scenario(installed.get(i.getVersion()))
                                .getExercises()
                                .size()
                            : 0))
            .toList();
    List<ContentHistoryOutput> history =
        contentHistoryService
            .findHistory(ContentType.SCENARIO, catalogId, PageRequest.of(0, 100))
            .map(ContentHistoryOutput::of)
            .getContent();
    return new CatalogScenarioVersionsOutput(catalogId, scenario.getVersion(), versions, history);
  }

  /** Target state of each inject, for the drawer a launch opens when one is missing (F12). */
  @Transactional(readOnly = true)
  public ScenarioTargetsOutput targets(String scenarioId) {
    return targetsOf(scenarioId);
  }

  private ScenarioTargetsOutput targetsOf(String scenarioId) {
    List<InjectPreview> injects =
        injectService.findByScenarioId(scenarioService.scenario(scenarioId).getId()).stream()
            .sorted(Comparator.comparing(Inject::getTitle, Comparator.nullsLast(String::compareTo)))
            .map(
                inject -> {
                  List<String> fields = targetFields(inject);
                  return new InjectPreview(
                      inject.getCatalogId(),
                      inject.getTitle(),
                      fields,
                      false,
                      fields.isEmpty() || isTargeted(inject, fields),
                      fields.contains(MANUAL) ? selectorKind(inject.getContent()) : null,
                      currentTargets(inject, fields));
                })
            .toList();
    return new ScenarioTargetsOutput(injects);
  }

  // -- CREATE --

  /**
   * Imports the package, binds its payloads to the versions the release names, then sets the
   * targets: those of the last installed version for the same injects, else the drawer's choice.
   */
  public CatalogInstallOutput install(
      TxCtx ctx, String tenantId, String externalId, String version, CatalogInstallInput input)
      throws Exception {
    CatalogItem item = findInstallable(externalId, version);
    List<String> missing = catalogRequirementsService.checker().missing(item);
    if (!missing.isEmpty()) {
      throw new BadRequestException("Missing to run this scenario: " + String.join(", ", missing));
    }
    if (scenarioApplyService.isChained(item)) {
      throw new BadRequestException(CatalogRequirementsService.CHAINED_NOT_SUPPORTED);
    }
    Optional<Scenario> instance = instance(item);
    if (instance.isPresent()) {
      return reinstall(ctx, instance.get(), version);
    }
    Optional<Scenario> previous = Optional.empty();
    byte[] archive = readPackage(item);

    Instant start = Instant.now();
    ImportResult result =
        importService.handleInputStreamFileImport(
            ctx, new ByteArrayInputStream(withCatalogIds(archive)), null, null, null, null, "");
    if (result.scenarioId() == null) {
      throw new BadRequestException("The package holds no scenario");
    }
    Scenario scenario = scenarioService.scenario(result.scenarioId());
    Set<Inject> injects = injectService.findByScenarioId(scenario.getId());

    bindPayloads(injects, item, tenantId, start);

    scenario.setCatalogId(externalId);
    scenario.setCatalogRepository(item.getRepository());
    scenario.setVersion(version);
    copyMetadata(item, scenario);
    if (item.getAuthor() != null && !item.getAuthor().isBlank()) {
      scenario.setAuthorOrganization(
          organizationService.findOrCreateByName(item.getAuthor(), tenantId));
    }
    setTargets(
        injects, previousInjects(previous), input == null ? CatalogInstallInput.empty() : input);
    scenarioApplyService.recordInstall(scenario, item);
    contentHistoryService.scenarioInstalled(
        scenario, previous.map(Scenario::getVersion).orElse(null));
    return new CatalogInstallOutput(scenario.getId(), version);
  }

  /**
   * Reinstall finds the same instance, then updates it to the chosen version when that merges
   * without a choice; otherwise the scenario offers the update.
   */
  private CatalogInstallOutput reinstall(TxCtx ctx, Scenario scenario, String version)
      throws Exception {
    if (!scenario.isUninstalled()) {
      throw new BadRequestException("This scenario is already installed: update it instead");
    }
    scenario.setUninstalled(false);
    scenarioService.updateScenario(scenario);
    contentHistoryService.scenarioInstalled(scenario, scenario.getVersion());
    if (!version.equals(scenario.getVersion())) {
      ScenarioApplyService.Target target = new ScenarioApplyService.Target(version, null);
      if (scenarioApplyService
          .preview(scenario.getId(), target, Map.of(), Map.of())
          .merge()
          .resolved()) {
        scenarioApplyService.apply(ctx, scenario.getId(), target, Map.of(), Map.of(), null);
      }
    }
    return new CatalogInstallOutput(scenario.getId(), scenario.getVersion());
  }

  private Optional<Scenario> instance(CatalogItem item) {
    return scenarioService.findCatalogInstance(item.getRepository(), item.getExternalId());
  }

  // -- UPDATE --

  /** Adds the chosen targets to every inject that takes their kind; none may stay without. */
  public ScenarioTargetsOutput completeTargets(String scenarioId, CatalogInstallInput input) {
    Scenario scenario = scenarioService.scenario(scenarioId);
    setTargets(
        injectService.findByScenarioId(scenario.getId()),
        Map.of(),
        input == null ? CatalogInstallInput.empty() : input);
    return targetsOf(scenarioId);
  }

  // -- DELETE --

  /**
   * Soft uninstall: the instance, its revisions and simulations stay; its schedule and active
   * autonomous run stop; it becomes read-only and hidden from the Active screen.
   */
  public void uninstall(String scenarioId) {
    Scenario scenario = installedScenario(scenarioId);
    autonomousRunService.activeRunId(scenarioId).ifPresent(autonomousRunService::cancel);
    scenario.setUninstalled(true);
    scenario.setRecurrence(null);
    scenario.setRecurrenceStart(null);
    scenario.setRecurrenceEnd(null);
    scenarioService.updateScenario(scenario);
    contentHistoryService.scenarioUninstalled(scenario);
  }

  private Scenario installedScenario(String scenarioId) {
    Scenario scenario = scenarioService.scenario(scenarioId);
    if (scenario.getCatalogId() == null) {
      throw new BadRequestException("This scenario was not installed from the catalogue");
    }
    return scenario;
  }

  private static void copyMetadata(CatalogItem item, Scenario scenario) {
    if (item.getSeverity() != null) {
      Arrays.stream(Scenario.SEVERITY.values())
          .filter(s -> s.name().equalsIgnoreCase(item.getSeverity()))
          .findFirst()
          .ifPresent(scenario::setSeverity);
    }
    if (item.getCategory() != null) {
      scenario.setCategory(item.getCategory());
    }
    if (item.getMainFocus() != null) {
      scenario.setMainFocus(item.getMainFocus());
    }
  }

  // -- PAYLOADS --

  private void bindPayloads(Set<Inject> injects, CatalogItem item, String tenantId, Instant start) {
    List<PayloadRef> refs = payloadRefs(item);
    Map<String, PayloadRef> byVersion =
        refs.stream()
            .collect(
                Collectors.toMap(r -> r.externalId() + "@" + r.version(), r -> r, (a, b) -> a));
    Map<String, PayloadRef> byHash =
        refs.stream()
            .filter(r -> r.sha256() != null)
            .collect(
                Collectors.toMap(
                    r -> r.sha256().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
    Map<String, Payload> managed = new HashMap<>();
    for (Inject inject : injects) {
      Payload payload = inject.getPayload().orElse(null);
      if (payload == null || managed.containsKey(payload.getId())) {
        continue;
      }
      if (PayloadVersionService.isManaged(payload)) {
        if (!byVersion.containsKey(payload.getExternalId() + "@" + payload.getVersion())) {
          throw new BadRequestException(
              "Payload " + payload.getName() + " is not a version this release names");
        }
      } else {
        PayloadRef ref =
            payload.getContentHash() == null
                ? null
                : byHash.get(payload.getContentHash().toLowerCase(Locale.ROOT));
        if (ref == null) {
          throw new BadRequestException(
              "Payload " + payload.getName() + " does not match the release");
        }
        PayloadIdentity identity =
            payloadVersionService.findOrCreateManagedIdentity(
                tenantId, payloadRepository(ref, item), ref.externalId());
        boolean versionExists =
            payloadVersionService.findVersions(identity).stream()
                .anyMatch(p -> ref.version().equals(p.getVersion()));
        // Only a copy this install made may become the version: never an existing local payload.
        if (versionExists || payload.getCreatedAt().isBefore(start)) {
          throw new BadRequestException(
              "Payload "
                  + ref.externalId()
                  + " "
                  + ref.version()
                  + " conflicts with a payload already installed");
        }
        payloadVersionService.moveToIdentity(payload, identity, ref.version());
      }
      managed.put(payload.getId(), payload);
    }
    if (!payloadVersionService.revokedIds(managed.values()).isEmpty()) {
      throw new BadRequestException("This scenario runs a revoked payload");
    }
  }

  private List<PayloadRef> payloadRefs(CatalogItem item) {
    return catalogService.payloadRefs(item);
  }

  private String payloadRepository(PayloadRef ref, CatalogItem scenarioItem) {
    return scenarioApplyService.payloadRepository(ref, scenarioItem);
  }

  // -- TARGETS --

  private record Chosen(
      List<Asset> assets, List<AssetGroup> assetGroups, List<Team> teams, List<String> manual) {

    boolean has(String kind) {
      return switch (kind) {
        case CONTRACT_ELEMENT_CONTENT_KEY_ASSETS -> !assets.isEmpty();
        case CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS -> !assetGroups.isEmpty();
        case CONTRACT_ELEMENT_CONTENT_KEY_TEAMS -> !teams.isEmpty();
        case MANUAL -> !manual.isEmpty();
        default -> false;
      };
    }
  }

  /**
   * Injects keep the targets of the installed version they replace; then the chosen targets are
   * added to every inject that takes their kind (section 6.3). None may stay without a target.
   */
  private void setTargets(
      Set<Inject> injects, Map<String, Inject> previousInjects, CatalogInstallInput input) {
    Chosen chosen =
        new Chosen(
            isEmpty(input.assetIds()) ? List.of() : assetService.assets(input.assetIds()),
            isEmpty(input.assetGroupIds())
                ? List.of()
                : assetGroupService.assetGroups(input.assetGroupIds()),
            isEmpty(input.teamIds()) ? List.of() : teamService.getTeamsByIds(input.teamIds()),
            input.manualTargets() == null
                ? List.of()
                : input.manualTargets().stream()
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .toList());
    List<String> missing = new ArrayList<>();
    for (Inject inject : injects) {
      List<String> fields = targetFields(inject);
      if (fields.isEmpty()) {
        continue;
      }
      Inject before =
          inject.getCatalogId() == null ? null : previousInjects.get(inject.getCatalogId());
      if (before != null && isTargeted(before, fields)) {
        carryOver(before, inject, fields);
      }
      addTargets(inject, fields, chosen);
      if (!isTargeted(inject, fields)) {
        missing.add(inject.getTitle());
      }
    }
    if (!missing.isEmpty()) {
      throw new BadRequestException("Missing target on: " + String.join(", ", missing));
    }
  }

  private void carryOver(Inject before, Inject inject, List<String> fields) {
    inject.setAssets(new ArrayList<>(before.getAssets()));
    inject.setAssetGroups(new ArrayList<>(before.getAssetGroups()));
    inject.setTeams(new ArrayList<>(before.getTeams()));
    inject.setAllTeams(before.isAllTeams());
    if (fields.contains(MANUAL) && before.getContent() != null) {
      ObjectNode content = content(inject);
      content.set(SELECTOR, before.getContent().get(SELECTOR));
      content.set(MANUAL_TARGETS, before.getContent().get(MANUAL_TARGETS));
      inject.setContent(content);
    }
  }

  // An inject with a target selector takes one kind at a time: the chosen one, if it has none.
  private void addTargets(Inject inject, List<String> fields, Chosen chosen) {
    if (fields.contains(MANUAL)) {
      String kind = selectorKind(inject.getContent());
      if (!chosen.has(kind) && !isTargeted(inject, fields)) {
        kind =
            Stream.of(
                    CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS,
                    CONTRACT_ELEMENT_CONTENT_KEY_ASSETS,
                    MANUAL)
                .filter(chosen::has)
                .findFirst()
                .orElse(kind);
        ObjectNode content = content(inject);
        content.put(SELECTOR, SELECTOR_VALUES.get(kind));
        inject.setContent(content);
      }
      add(inject, kind, chosen);
      return;
    }
    fields.forEach(kind -> add(inject, kind, chosen));
  }

  private void add(Inject inject, String kind, Chosen chosen) {
    switch (kind) {
      case CONTRACT_ELEMENT_CONTENT_KEY_ASSETS ->
          inject.setAssets(union(inject.getAssets(), chosen.assets(), Asset::getId));
      case CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS ->
          inject.setAssetGroups(
              union(inject.getAssetGroups(), chosen.assetGroups(), AssetGroup::getId));
      case CONTRACT_ELEMENT_CONTENT_KEY_TEAMS ->
          inject.setTeams(union(inject.getTeams(), chosen.teams(), Team::getId));
      case MANUAL -> {
        if (!chosen.manual().isEmpty()) {
          ObjectNode content = content(inject);
          content.put(
              MANUAL_TARGETS,
              String.join(",", union(manualTargets(inject.getContent()), chosen.manual(), t -> t)));
          inject.setContent(content);
        }
      }
      default -> {}
    }
  }

  private static <T> List<T> union(List<T> current, List<T> added, Function<T, String> id) {
    List<T> merged = new ArrayList<>(current);
    Set<String> ids = current.stream().map(id).collect(Collectors.toSet());
    added.stream().filter(item -> ids.add(id.apply(item))).forEach(merged::add);
    return merged;
  }

  private ObjectNode content(Inject inject) {
    return inject.getContent() == null ? mapper.createObjectNode() : inject.getContent().deepCopy();
  }

  private List<String> targetFields(Inject inject) {
    InjectorContract contract = inject.getInjectorContract().orElse(null);
    List<String> fields =
        new ArrayList<>(
            TARGET_FIELDS.stream()
                .filter(field -> injectorContractContentUtils.hasField(contract, field))
                .toList());
    if (injectorContractContentUtils.hasField(contract, SELECTOR)
        && injectorContractContentUtils.hasField(contract, MANUAL_TARGETS)) {
      fields.add(MANUAL);
    }
    return fields;
  }

  private List<String> targetFields(String contractContent) {
    if (contractContent == null) {
      return List.of();
    }
    try {
      JsonNode fields = mapper.readTree(contractContent).get("fields");
      Set<String> keys = new HashSet<>();
      if (fields != null && fields.isArray()) {
        fields.forEach(field -> keys.add(text(field, "key")));
      }
      List<String> kinds = new ArrayList<>(TARGET_FIELDS.stream().filter(keys::contains).toList());
      if (keys.contains(SELECTOR) && keys.contains(MANUAL_TARGETS)) {
        kinds.add(MANUAL);
      }
      return kinds;
    } catch (Exception e) {
      return List.of();
    }
  }

  private static boolean isTargeted(Inject inject, List<String> fields) {
    if (fields.contains(MANUAL)) {
      return switch (selectorKind(inject.getContent())) {
        case MANUAL -> !manualTargets(inject.getContent()).isEmpty();
        case CONTRACT_ELEMENT_CONTENT_KEY_ASSETS -> !inject.getAssets().isEmpty();
        default -> !inject.getAssetGroups().isEmpty();
      };
    }
    return (fields.contains(CONTRACT_ELEMENT_CONTENT_KEY_ASSETS) && !inject.getAssets().isEmpty())
        || (fields.contains(CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS)
            && !inject.getAssetGroups().isEmpty())
        || (fields.contains(CONTRACT_ELEMENT_CONTENT_KEY_TEAMS)
            && (inject.isAllTeams() || !inject.getTeams().isEmpty()));
  }

  private static List<TargetLabel> currentTargets(Inject inject, List<String> fields) {
    List<TargetLabel> labels = new ArrayList<>();
    String selected = fields.contains(MANUAL) ? selectorKind(inject.getContent()) : null;
    if (fields.contains(CONTRACT_ELEMENT_CONTENT_KEY_ASSETS)
        && (selected == null || CONTRACT_ELEMENT_CONTENT_KEY_ASSETS.equals(selected))) {
      inject
          .getAssets()
          .forEach(
              a -> labels.add(new TargetLabel(CONTRACT_ELEMENT_CONTENT_KEY_ASSETS, a.getName())));
    }
    if (fields.contains(CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS)
        && (selected == null || CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS.equals(selected))) {
      inject
          .getAssetGroups()
          .forEach(
              g ->
                  labels.add(
                      new TargetLabel(CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS, g.getName())));
    }
    if (MANUAL.equals(selected)) {
      manualTargets(inject.getContent()).forEach(t -> labels.add(new TargetLabel(MANUAL, t)));
    }
    if (fields.contains(CONTRACT_ELEMENT_CONTENT_KEY_TEAMS)) {
      if (inject.isAllTeams()) {
        labels.add(new TargetLabel(CONTRACT_ELEMENT_CONTENT_KEY_TEAMS, "All teams"));
      }
      inject
          .getTeams()
          .forEach(
              t -> labels.add(new TargetLabel(CONTRACT_ELEMENT_CONTENT_KEY_TEAMS, t.getName())));
    }
    return labels;
  }

  // A package only carries manual hosts: its assets, groups and teams belong to another platform.
  private static List<TargetLabel> packagedTargets(JsonNode content, List<String> fields) {
    if (!fields.contains(MANUAL) || !MANUAL.equals(selectorKind(content))) {
      return List.of();
    }
    return manualTargets(content).stream().map(t -> new TargetLabel(MANUAL, t)).toList();
  }

  private static String selectorKind(JsonNode content) {
    String value = content == null ? null : text(content, SELECTOR);
    return SELECTOR_VALUES.entrySet().stream()
        .filter(e -> e.getValue().equals(value))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS);
  }

  private static List<String> manualTargets(JsonNode content) {
    String value = content == null ? null : text(content, MANUAL_TARGETS);
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
  }

  // -- PACKAGE --

  private CatalogItem findInstallable(String externalId, String version) {
    return catalogService
        .find(ContentType.SCENARIO, externalId, version)
        .orElseThrow(() -> new ElementNotFoundException("Catalogue scenario not found"));
  }

  private Optional<Scenario> previousInstall(String externalId, String version) {
    return scenarioService.findAllByCatalogId(externalId).stream()
        .filter(s -> s.getVersion() != null && !version.equals(s.getVersion()))
        .max(Comparator.comparing(Scenario::getVersion, SemanticVersion.ORDER));
  }

  private Map<String, Inject> previousInjects(Optional<Scenario> previous) {
    if (previous.isEmpty()) {
      return Map.of();
    }
    return injectService.findByScenarioId(previous.get().getId()).stream()
        .filter(i -> i.getCatalogId() != null)
        .collect(Collectors.toMap(Inject::getCatalogId, i -> i, (a, b) -> a));
  }

  private byte[] readPackage(CatalogItem item) {
    return catalogService.readVerifiedPackage(item);
  }

  // A repository keeps each inject's ID across versions; the import gives injects new IDs.
  private static String catalogId(JsonNode inject) {
    String catalogId = text(inject, "inject_catalog_id");
    return catalogId != null ? catalogId : text(inject, "inject_id");
  }

  /** The package with each inject's catalogue ID written, entry comments kept for the import. */
  private byte[] withCatalogIds(byte[] archive) throws IOException {
    Path file = Files.createTempFile("openaev-catalog-", ".zip");
    try {
      Files.write(file, archive);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (ZipFile zip = new ZipFile(file.toFile());
          ZipOutputStream rewritten = new ZipOutputStream(out)) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          byte[] content;
          try (InputStream in = zip.getInputStream(entry)) {
            content = in.readAllBytes();
          }
          if (EXPORT_ENTRY_SCENARIO.equals(entry.getComment())) {
            JsonNode root = mapper.readTree(content);
            JsonNode injects = root.get("scenario_injects");
            if (injects != null && injects.isArray()) {
              injects.forEach(
                  inject -> {
                    if (inject.isObject() && catalogId(inject) != null) {
                      ((ObjectNode) inject).put("inject_catalog_id", catalogId(inject));
                    }
                    // A payload contract ID comes from another platform: link the payload by
                    // content.
                    JsonNode contract = inject.get("inject_injector_contract");
                    if (contract != null
                        && contract.isObject()
                        && contract.hasNonNull("injector_contract_payload")) {
                      ((ObjectNode) contract)
                          .put("injector_contract_id", UUID.randomUUID().toString());
                    }
                  });
            }
            content = mapper.writeValueAsBytes(root);
          }
          ZipEntry copy = new ZipEntry(entry.getName());
          copy.setComment(entry.getComment());
          rewritten.putNextEntry(copy);
          rewritten.write(content);
          rewritten.closeEntry();
        }
      }
      return out.toByteArray();
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private List<JsonNode> packagedInjects(byte[] archive) {
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory() || !entry.getName().endsWith(".json")) {
          continue;
        }
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        zip.transferTo(json);
        JsonNode root = mapper.readTree(json.toByteArray());
        JsonNode injects = root.get("scenario_injects");
        if (root.has("scenario_information") && injects != null && injects.isArray()) {
          List<JsonNode> nodes = new ArrayList<>();
          injects.forEach(nodes::add);
          return nodes;
        }
      }
    } catch (Exception e) {
      throw new BadRequestException("Unreadable catalogue package");
    }
    throw new BadRequestException("The package holds no scenario");
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static boolean isEmpty(List<String> ids) {
    return ids == null || ids.isEmpty();
  }
}
