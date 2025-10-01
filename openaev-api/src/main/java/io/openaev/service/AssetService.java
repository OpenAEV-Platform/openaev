package io.openaev.service;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.Asset;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
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
