package io.openbas.runner;

import static io.openbas.utils.StringUtils.generateRandomColor;

import io.openbas.database.model.*;
import io.openbas.database.repository.SettingRepository;
import io.openbas.rest.asset.endpoint.form.EndpointInput;
import io.openbas.rest.tag.TagService;
import io.openbas.rest.tag.form.TagCreateInput;
import io.openbas.service.AssetGroupService;
import io.openbas.service.EndpointService;
import io.openbas.service.ImportService;
import io.openbas.service.ZipJsonService;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Command line runner that initializes the starter pack on first application start. */
@Slf4j
@Component
@Transactional()
public class InitStarterPackCommandLineRunner implements CommandLineRunner {

  private static final class Config {
    static final String STARTERPACK_KEY = "starterpack";
    static final String STARTER_PACK_SETTING_VALUE = "StarterPack creation process completed";
    static final String SCENARIOS_FOLDER_NAME = "scenarios";
    static final String DASHBOARDS_FOLDER_NAME = "dashboards";
  }

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
  }

  private static final class AllEndpointsAssetGroup {
    static final String NAME = "All endpoints";
    static final String KEY = "endpoint_platform";
    static final Filters.FilterOperator OPERATOR = Filters.FilterOperator.not_empty;
  }

  @Value("${openbas.starterpack.enabled:#{true}}")
  private boolean isStarterPackEnabled;

  private final SettingRepository settingRepository;
  private final TagService tagService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final ImportService importService;
  private final ZipJsonService<CustomDashboard> zipJsonService;

  public InitStarterPackCommandLineRunner(
      SettingRepository settingRepository,
      TagService tagService,
      EndpointService endpointService,
      AssetGroupService assetGroupService,
      ImportService importService,
      ZipJsonService<CustomDashboard> zipJsonService) {
    this.settingRepository = settingRepository;
    this.tagService = tagService;
    this.endpointService = endpointService;
    this.assetGroupService = assetGroupService;
    this.importService = importService;
    this.zipJsonService = zipJsonService;
  }

  @Override
  public void run(String... args) {
    if (!isStarterPackEnabled) {
      log.info("Starter pack is disabled by configuration");
      return;
    }

    if (this.settingRepository.findByKey(Config.STARTERPACK_KEY).isPresent()) {
      log.info("Starter pack already initialized");
      return;
    }

    Tag tagVulnerability = this.createTag(Tags.VULNERABILITY);
    Tag tagCisco = this.createTag(Tags.CISCO);
    Tag tagOpenCTI = this.createTag(Tags.OPENCTI);
    this.createHoneyScanMeAgentlessEndpoint(List.of(tagVulnerability.getId(), tagCisco.getId()));
    this.createAllEndpointsAssetGroup(Set.of(tagOpenCTI));
    this.importScenariosFromResources();
    this.importDashboardsFromResources();

    this.createSetting();
  }

  private void createHoneyScanMeAgentlessEndpoint(List<String> tags) {
    EndpointInput endpointInput = new EndpointInput();
    endpointInput.setName(HoneyScanMeEndpoint.HOSTNAME);
    endpointInput.setHostname(HoneyScanMeEndpoint.HOSTNAME);
    endpointInput.setIps(HoneyScanMeEndpoint.IPS);
    endpointInput.setArch(HoneyScanMeEndpoint.ARCH);
    endpointInput.setPlatform(HoneyScanMeEndpoint.PLATFORM);
    endpointInput.setEol(true);
    endpointInput.setTagIds(tags);
    this.endpointService.createEndpoint(endpointInput);
  }

  private void createAllEndpointsAssetGroup(Set<Tag> tags) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(AllEndpointsAssetGroup.KEY);
    filter.setOperator(AllEndpointsAssetGroup.OPERATOR);
    filter.setMode(Filters.FilterMode.or);

    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.or);
    filterGroup.setFilters(List.of(filter));

    AssetGroup allEndpointsAssetGroup = new AssetGroup();
    allEndpointsAssetGroup.setName(AllEndpointsAssetGroup.NAME);
    allEndpointsAssetGroup.setTags(tags);
    allEndpointsAssetGroup.setDynamicFilter(filterGroup);
    this.assetGroupService.createAssetGroup(allEndpointsAssetGroup);
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
                log.error(
                    "Failed to import StarterPack scenario file : {}; cause {}",
                    resourceToAdd.getFilename(),
                    e.getMessage());
              }
            });
  }

  private void importDashboardsFromResources() {
    listFilesInResourceFolder(Config.DASHBOARDS_FOLDER_NAME)
        .forEach(
            resourceToAdd -> {
              try {
                this.zipJsonService.handleImport(
                    resourceToAdd.getContentAsByteArray(), "custom_dashboard_name", null);
                log.info(
                    "Successfully imported StarterPack dashboard file : {}",
                    resourceToAdd.getFilename());
              } catch (Exception e) {
                log.error(
                    "Failed to import StarterPack dashboard file : {}; cause {}",
                    resourceToAdd.getFilename(),
                    e.getMessage());
              }
            });
  }

  private List<Resource> listFilesInResourceFolder(String folderName) {
    try {
      ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
      return Arrays.stream(
              resolver.getResources(
                  "classpath:" + Config.STARTERPACK_KEY + "/" + folderName + "/*"))
          .toList();
    } catch (Exception e) {
      log.error(
          "Failed to import StarterPack files from resource folder {}; cause {}",
          Config.STARTERPACK_KEY + "/" + folderName,
          e.getMessage());
      return Collections.emptyList();
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
    setting.setKey(Config.STARTERPACK_KEY);
    setting.setValue(Config.STARTER_PACK_SETTING_VALUE);

    this.settingRepository.save(setting);
  }
}
