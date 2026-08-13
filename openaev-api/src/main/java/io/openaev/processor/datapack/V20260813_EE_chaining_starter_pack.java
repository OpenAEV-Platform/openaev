package io.openaev.processor.datapack;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.DataPackService;
import io.openaev.service.ImportService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class V20260813_EE_chaining_starter_pack extends DataPack {

  public V20260813_EE_chaining_starter_pack(
      DataPackService dataPackService,
      EnterpriseEditionService enterpriseEditionService,
      LicenseCacheManager licenseCacheManager,
      EndpointRepository endpointRepository,
      AssetGroupService assetGroupService,
      ImportService importService,
      ResourcePatternResolver resolver) {
    super(dataPackService);
    this.enterpriseEditionService = enterpriseEditionService;
    this.licenseCacheManager = licenseCacheManager;
    this.endpointRepository = endpointRepository;
    this.assetGroupService = assetGroupService;
    this.importService = importService;
    this.resolver = resolver;
  }

  private static final class Config {
    static final String SCENARIOS_FOLDER = "scenario-chaining";
    static final String STARTER_PACK_KEY = "starterpack";
    static final String HONEY_SCANME_HOSTNAME = "honey.scanme.sh";
    static final String[] HONEY_SCANME_IPS = new String[] {"67.205.158.113"};
    static final String ALL_ENDPOINTS_ASSET_GROUP = "All endpoints";
  }

  @Value("${openbas.starterpack.enabled:${openaev.starterpack.enabled:#{true}}}")
  private boolean isStarterPackEnabled;

  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final EndpointRepository endpointRepository;
  private final AssetGroupService assetGroupService;
  private final ImportService importService;
  private final ResourcePatternResolver resolver;

  @Override
  protected boolean doProcess() {
    if (!isStarterPackEnabled) {
      log.info("Starter pack is disabled by configuration");
      return false;
    }

    if (!enterpriseEditionService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo())) {
      log.info("Skipping EE starter pack chaining scenario import: EE license is inactive");
      return false;
    }

    String tenantId = TenantContext.getCurrentTenant();
    Asset defaultAsset =
        endpointRepository
            .findByHostnameAndAtleastOneIp(
                Config.HONEY_SCANME_HOSTNAME, Config.HONEY_SCANME_IPS, tenantId)
            .stream()
            .findFirst()
            .orElse(null);
    if (defaultAsset == null) {
      log.info(
          "Skipping EE starter pack chaining scenario import: base starter pack endpoint not found");
      return false;
    }

    AssetGroup defaultAssetGroup =
        assetGroupService.assetGroups().stream()
            .filter(assetGroup -> Config.ALL_ENDPOINTS_ASSET_GROUP.equals(assetGroup.getName()))
            .findFirst()
            .orElse(null);
    if (defaultAssetGroup == null) {
      log.info(
          "Skipping EE starter pack chaining scenario import: base starter pack asset group not found");
      return false;
    }

    List<Resource> scenarioResources = listFilesInResourceFolder(Config.SCENARIOS_FOLDER);
    if (scenarioResources.isEmpty()) {
      log.warn("No EE starter pack scenario found under '{}'", Config.SCENARIOS_FOLDER);
      return false;
    }

    for (Resource scenarioResource : scenarioResources) {
      try {
        importService.handleInputStreamFileImport(
            scenarioResource.getInputStream(), null, null, defaultAsset, defaultAssetGroup, "");
        log.info(
            "Successfully imported EE StarterPack scenario file : {}",
            scenarioResource.getFilename());
      } catch (Exception e) {
        log.error(
            "Failed to import EE StarterPack scenario file : " + scenarioResource.getFilename(), e);
        return false;
      }
    }

    return true;
  }

  private List<Resource> listFilesInResourceFolder(String folderName) {
    try {
      return Arrays.stream(
              resolver.getResources(
                  "classpath:" + Config.STARTER_PACK_KEY + "/" + folderName + "/*.zip"))
          .toList();
    } catch (Exception e) {
      log.error(
          "Failed to import EE StarterPack files from resource folder "
              + Config.STARTER_PACK_KEY
              + "/"
              + folderName,
          e);
      return Collections.emptyList();
    }
  }
}
