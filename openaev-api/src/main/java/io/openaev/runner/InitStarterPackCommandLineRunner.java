package io.openaev.runner;

import static io.openaev.utils.StringUtils.generateRandomColor;

import io.openaev.database.model.*;
import io.openaev.database.repository.SettingRepository;
import io.openaev.jsonapi.JsonApiDocument;
import io.openaev.jsonapi.ResourceObject;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.tag.TagService;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.service.*;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Command line runner that initializes the starter pack on first application start. */
@Slf4j
@Component
@Transactional()
@RequiredArgsConstructor
public class InitStarterPackCommandLineRunner implements CommandLineRunner {

  private static final class Config {
    static final String STARTER_PACK_KEY = "starterpack";
    static final String STARTER_PACK_SETTING_VALUE = "StarterPack creation process completed";
    static final String SCENARIOS_FOLDER_NAME = "scenarios";
    static final String DASHBOARDS_FOLDER_NAME = "dashboards";
    static final String DEFAULT_FILE_DASHBOARD_HOME = "default_home";
    static final String DEFAULT_FILE_DASHBOARD_SCENARIO = "default_scenario";
    static final String DEFAULT_FILE_DASHBOARD_SIMULATION = "default_simulation";
  }

  private static final Map<String, String> DASHBOARD_PREFIX_TO_SETTING_KEY =
      Map.of(
          Config.DEFAULT_FILE_DASHBOARD_HOME, SettingKeys.DEFAULT_HOME_DASHBOARD.key(),
          Config.DEFAULT_FILE_DASHBOARD_SCENARIO, SettingKeys.DEFAULT_SCENARIO_DASHBOARD.key(),
          Config.DEFAULT_FILE_DASHBOARD_SIMULATION, SettingKeys.DEFAULT_SIMULATION_DASHBOARD.key());

  private static final class Tags {
    static final String VULNERABILITY = "vulnerability";
    static final String CISCO = "cisco";
    static final String OPENCTI = "opencti";
  }

  private static final class HoneyScanMeEndpoint {
    static final String HOSTNAME = "honey.scanme.sh";
    static final String[] IPS = new String[] {"67.205.158.113"};
    static final Endpoint.PLATFORM_ARCH ARCH = Endpoint.PLATFORM_ARCH.x86_64;
    static final Endpoint.PLATFORM_TYPE PLATFORM = Endpoint.PLATFORM_TYPE.Generic;
    static final boolean END_OF_LIFE = true;
  }

  private static final class AllEndpointsAssetGroup {
    static final String NAME = "All endpoints";
    static final String KEY = "endpoint_platform";
    static final Filters.FilterOperator OPERATOR = Filters.FilterOperator.not_empty;
  }

  @Value("${openaev.starterpack.enabled:#{true}}")
  private boolean isStarterPackEnabled;

  private final SettingRepository settingRepository;
  private final TagService tagService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final TagRuleService tagRuleService;
  private final ImportService importService;
  private final ZipJsonService<CustomDashboard> zipJsonService;
  private final ResourcePatternResolver resolver;

  private boolean hasError = false;
  private String errorMessage = null;

  @Override
  public void run(String... args) {
    if (!isStarterPackEnabled) {
      log.info("Starter pack is disabled by configuration");
      return;
    }

    if (this.settingRepository.findByKey(Config.STARTER_PACK_KEY).isPresent()) {
      log.info("Starter pack already initialized");
      return;
    }

    try {
      Tag tagVulnerability = this.createTag(Tags.VULNERABILITY);
      Tag tagCisco = this.createTag(Tags.CISCO);
      this.createHoneyScanMeAgentlessEndpoint(List.of(tagVulnerability.getId(), tagCisco.getId()));
      this.createAllEndpointsAssetGroup();
      this.importScenariosFromResources();
      this.importDashboardsFromResources();
    } catch (Exception e) {
      recordError("Unexpected error during StarterPack initialization; cause " + e.getMessage());
    }

    this.createSetting();
  }

  private void createHoneyScanMeAgentlessEndpoint(List<String> tags) {
    EndpointInput endpointInput = new EndpointInput();
    endpointInput.setName(HoneyScanMeEndpoint.HOSTNAME);
    endpointInput.setHostname(HoneyScanMeEndpoint.HOSTNAME);
    endpointInput.setIps(HoneyScanMeEndpoint.IPS);
    endpointInput.setArch(HoneyScanMeEndpoint.ARCH);
    endpointInput.setPlatform(HoneyScanMeEndpoint.PLATFORM);
    endpointInput.setEol(HoneyScanMeEndpoint.END_OF_LIFE);
    endpointInput.setTagIds(tags);
    this.endpointService.createEndpoint(endpointInput);
  }

  private void createAllEndpointsAssetGroup() {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(AllEndpointsAssetGroup.KEY);
    filter.setOperator(AllEndpointsAssetGroup.OPERATOR);
    filter.setMode(Filters.FilterMode.or);

    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.or);
    filterGroup.setFilters(List.of(filter));

    AssetGroup allEndpointsAssetGroup = new AssetGroup();
    allEndpointsAssetGroup.setName(AllEndpointsAssetGroup.NAME);
    allEndpointsAssetGroup.setDynamicFilter(filterGroup);

    AssetGroup createdAllEndpointAssetGroup =
        this.assetGroupService.createAssetGroup(allEndpointsAssetGroup);

    Optional<TagRule> openCtiTagRule = this.tagRuleService.findByTagName(Tags.OPENCTI);
    openCtiTagRule.ifPresent(
        tagRule ->
            this.tagRuleService.updateTagRule(
                tagRule.getId(), Tags.OPENCTI, List.of(createdAllEndpointAssetGroup.getId())));
  }

  private void importScenariosFromResources() {
    listFilesInResourceFolder(Config.SCENARIOS_FOLDER_NAME)
        .forEach(
            resourceToAdd -> {
              try {
                this.importService.handleInputStreamFileImport(
                    resourceToAdd.getInputStream(), null, null);
                log.info(
                    "Successfully imported StarterPack scenario file : {}",
                    resourceToAdd.getFilename());
              } catch (Exception e) {
                recordError(
                    "Failed to import StarterPack scenario file : "
                        + resourceToAdd.getFilename()
                        + "; cause "
                        + e.getMessage());
              }
            });
  }

  private void importDashboardsFromResources() {
    listFilesInResourceFolder(Config.DASHBOARDS_FOLDER_NAME)
        .forEach(
            resourceToAdd -> {
              try {
                JsonApiDocument<ResourceObject> dashboard =
                    this.zipJsonService.handleImport(
                        resourceToAdd.getContentAsByteArray(),
                        "custom_dashboard_name",
                        null,
                        CustomDashboardService::sanityCheck);
                this.setDefaultDashboard(resourceToAdd.getFilename(), dashboard.data().id());
                log.info(
                    "Successfully imported StarterPack dashboard file : {}",
                    resourceToAdd.getFilename());
              } catch (Exception e) {
                recordError(
                    "Failed to import StarterPack dashboard file : "
                        + resourceToAdd.getFilename()
                        + "; cause "
                        + e.getMessage());
              }
            });
  }

  private List<Resource> listFilesInResourceFolder(String folderName) {
    try {
      return Arrays.stream(
              this.resolver.getResources(
                  "classpath:" + Config.STARTER_PACK_KEY + "/" + folderName + "/*.zip"))
          .toList();
    } catch (Exception e) {
      recordError(
          "Failed to import StarterPack files from resource folder "
              + Config.STARTER_PACK_KEY
              + "/"
              + folderName
              + "; cause "
              + e.getMessage());
      return Collections.emptyList();
    }
  }

  private void recordError(@NotBlank final String message) {
    this.hasError = true;
    this.errorMessage = message;
    log.error(message);
  }

  private void setDefaultDashboard(String filename, String dashboardId) {
    String settingKey =
        DASHBOARD_PREFIX_TO_SETTING_KEY.entrySet().stream()
            .filter(entry -> filename.startsWith(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);

    if (settingKey != null) {
      Setting defaultDashboardSetting =
          settingRepository.findByKey(settingKey).orElse(new Setting(settingKey, null));
      defaultDashboardSetting.setValue(dashboardId);
      settingRepository.save(defaultDashboardSetting);
    }
  }

  private Tag createTag(String name) {
    TagCreateInput tagCreateInput = new TagCreateInput();
    tagCreateInput.setName(name);
    tagCreateInput.setColor(generateRandomColor());
    return this.tagService.upsertTag(tagCreateInput);
  }

  private void createSetting() {
    Setting setting = new Setting();
    setting.setKey(Config.STARTER_PACK_KEY);
    if (hasError) {
      setting.setValue(this.errorMessage);
    } else {
      setting.setValue(Config.STARTER_PACK_SETTING_VALUE);
    }
    this.settingRepository.save(setting);
  }
}
