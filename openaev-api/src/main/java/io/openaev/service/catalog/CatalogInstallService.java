package io.openaev.service.catalog;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSETS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TEAMS;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.CatalogInstallInput;
import io.openaev.api.catalog.CatalogInstallOutput;
import io.openaev.api.catalog.CatalogInstallPreviewOutput;
import io.openaev.api.catalog.CatalogInstallPreviewOutput.InjectPreview;
import io.openaev.api.catalog.CatalogManifest.PayloadRef;
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
import io.openaev.utils.SemanticVersion;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Installs a catalogue scenario version next to the ones already installed (F11), with its targets
 * (F12). Every payload it runs must be a version its release names, never revoked.
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

  private final CatalogService catalogService;
  private final ImportService importService;
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
      String catalogId = text(inject, "inject_catalog_id");
      JsonNode contract = inject.get("inject_injector_contract");
      List<String> fields =
          contract == null ? List.of() : targetFields(text(contract, "injector_contract_content"));
      Inject before = catalogId == null ? null : previousInjects.get(catalogId);
      injects.add(
          new InjectPreview(
              catalogId,
              text(inject, "inject_title"),
              fields,
              before == null,
              fields.isEmpty() || (before != null && isTargeted(before, fields))));
    }
    return new CatalogInstallPreviewOutput(
        previous.map(Scenario::getVersion).orElse(null),
        previous.map(Scenario::getId).orElse(null),
        injects);
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
    Optional<Scenario> previous = previousInstall(externalId, version);
    byte[] archive = readPackage(item);

    Instant start = Instant.now();
    ImportResult result =
        importService.handleInputStreamFileImport(
            ctx, new ByteArrayInputStream(archive), null, null, null, null, "");
    if (result.scenarioId() == null) {
      throw new BadRequestException("The package holds no scenario");
    }
    Scenario scenario = scenarioService.scenario(result.scenarioId());
    Set<Inject> injects = injectService.findByScenarioId(scenario.getId());

    bindPayloads(injects, payloadRefs(item), start);

    scenario.setCatalogId(externalId);
    scenario.setVersion(version);
    if (item.getAuthor() != null && !item.getAuthor().isBlank()) {
      scenario.setAuthorOrganization(
          organizationService.findOrCreateByName(item.getAuthor(), tenantId));
    }
    setTargets(
        injects, previousInjects(previous), input == null ? CatalogInstallInput.empty() : input);
    contentHistoryService.scenarioInstalled(
        scenario, previous.map(Scenario::getVersion).orElse(null));
    return new CatalogInstallOutput(scenario.getId(), version);
  }

  // -- DELETE --

  /** Removes an installed version; the payload versions it used stay installed (F11). */
  public void uninstall(String scenarioId) {
    Scenario scenario = scenarioService.scenario(scenarioId);
    if (scenario.getCatalogId() == null) {
      throw new BadRequestException("This scenario was not installed from the catalogue");
    }
    contentHistoryService.scenarioUninstalled(scenario);
    autonomousRunService.deleteForScenario(scenarioId);
    scenarioService.deleteScenario(scenarioId);
  }

  // -- PAYLOADS --

  private void bindPayloads(Set<Inject> injects, List<PayloadRef> refs, Instant start) {
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
        boolean versionExists =
            payloadVersionService.findAllByExternalId(ref.externalId()).stream()
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
        payloadVersionService.promoteToVersion(payload, ref.externalId(), ref.version());
      }
      managed.put(payload.getId(), payload);
    }
    if (!payloadVersionService.revokedIds(managed.values()).isEmpty()) {
      throw new BadRequestException("This scenario runs a revoked payload");
    }
  }

  private List<PayloadRef> payloadRefs(CatalogItem item) {
    if (item.getPayloadRefs() == null) {
      return List.of();
    }
    try {
      return mapper.readValue(item.getPayloadRefs(), new TypeReference<List<PayloadRef>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Unreadable payload references", e);
    }
  }

  // -- TARGETS --

  private void setTargets(
      Set<Inject> injects, Map<String, Inject> previousInjects, CatalogInstallInput input) {
    List<Asset> assets =
        isEmpty(input.assetIds()) ? List.of() : assetService.assets(input.assetIds());
    List<AssetGroup> assetGroups =
        isEmpty(input.assetGroupIds())
            ? List.of()
            : assetGroupService.assetGroups(input.assetGroupIds());
    List<Team> teams =
        isEmpty(input.teamIds()) ? List.of() : teamService.getTeamsByIds(input.teamIds());
    List<String> missing = new ArrayList<>();
    for (Inject inject : injects) {
      List<String> fields = targetFields(inject);
      if (fields.isEmpty()) {
        continue;
      }
      Inject before =
          inject.getCatalogId() == null ? null : previousInjects.get(inject.getCatalogId());
      if (before != null && isTargeted(before, fields)) {
        inject.setAssets(new ArrayList<>(before.getAssets()));
        inject.setAssetGroups(new ArrayList<>(before.getAssetGroups()));
        inject.setTeams(new ArrayList<>(before.getTeams()));
        inject.setAllTeams(before.isAllTeams());
        continue;
      }
      if (fields.contains(CONTRACT_ELEMENT_CONTENT_KEY_ASSETS)) {
        inject.setAssets(new ArrayList<>(assets));
      }
      if (fields.contains(CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS)) {
        inject.setAssetGroups(new ArrayList<>(assetGroups));
      }
      if (fields.contains(CONTRACT_ELEMENT_CONTENT_KEY_TEAMS)) {
        inject.setTeams(new ArrayList<>(teams));
      }
      if (!isTargeted(inject, fields)) {
        missing.add(inject.getTitle());
      }
    }
    if (!missing.isEmpty()) {
      throw new BadRequestException("Missing target on: " + String.join(", ", missing));
    }
  }

  private List<String> targetFields(Inject inject) {
    InjectorContract contract = inject.getInjectorContract().orElse(null);
    return TARGET_FIELDS.stream()
        .filter(field -> injectorContractContentUtils.hasField(contract, field))
        .toList();
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
      return TARGET_FIELDS.stream().filter(keys::contains).toList();
    } catch (Exception e) {
      return List.of();
    }
  }

  private static boolean isTargeted(Inject inject, List<String> fields) {
    return (fields.contains(CONTRACT_ELEMENT_CONTENT_KEY_ASSETS) && !inject.getAssets().isEmpty())
        || (fields.contains(CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS)
            && !inject.getAssetGroups().isEmpty())
        || (fields.contains(CONTRACT_ELEMENT_CONTENT_KEY_TEAMS)
            && (inject.isAllTeams() || !inject.getTeams().isEmpty()));
  }

  // -- PACKAGE --

  private CatalogItem findInstallable(String externalId, String version) {
    CatalogItem item =
        catalogService
            .find(ContentType.SCENARIO, externalId, version)
            .orElseThrow(() -> new ElementNotFoundException("Catalogue scenario not found"));
    if ("REVOKED".equals(item.getStatus())) {
      throw new BadRequestException("This version is revoked");
    }
    if (scenarioService.findAllByCatalogId(externalId).stream()
        .anyMatch(s -> version.equals(s.getVersion()))) {
      throw new BadRequestException("This version is already installed");
    }
    return item;
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
    byte[] archive =
        catalogService
            .readPackage(item)
            .orElseThrow(() -> new ElementNotFoundException("Catalogue package not found"));
    if (!CatalogArchiveChecks.sha256(archive).equalsIgnoreCase(item.getArchiveHash())) {
      throw new BadRequestException("The stored package does not match its release");
    }
    return archive;
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
