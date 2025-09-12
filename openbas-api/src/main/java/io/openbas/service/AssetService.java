package io.openbas.service;

import static io.openbas.helper.StreamHelper.fromIterable;

import io.openbas.database.model.Asset;
import io.openbas.database.model.SecurityPlatform;
import io.openbas.database.repository.AssetRepository;
import io.openbas.database.repository.SecurityPlatformRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AssetService {

  private final AssetRepository assetRepository;
  private final SecurityPlatformRepository securityPlatformRepository;

  public Asset asset(@NotBlank final String assetId) {
    return this.assetRepository.findById(assetId).orElseThrow();
  }

  public List<Asset> assets(@NotBlank final List<String> assetIds) {
    return fromIterable(this.assetRepository.findAllById(assetIds));
  }

  public List<Asset> assets() {
    return fromIterable(this.assetRepository.findAll());
  }

  public List<SecurityPlatform> securityPlatforms() {
    return fromIterable(securityPlatformRepository.findAll());
  }

  public Iterable<Asset> assetFromIds(@NotNull final List<String> assetIds) {
    return this.assetRepository.findAllById(assetIds);
  }

  public Iterable<Asset> saveAllAssets(List<Asset> assets) {
    return this.assetRepository.saveAll(assets);
  }
}
