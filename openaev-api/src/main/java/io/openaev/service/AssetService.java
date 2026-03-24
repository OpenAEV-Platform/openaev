package io.openaev.service;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.Asset;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AssetService {

  @PersistenceContext private EntityManager entityManager;

  private final AssetRepository assetRepository;
  private final SecurityPlatformRepository securityPlatformRepository;

  public Asset asset(@NotBlank final String assetId) {
    return this.assetRepository
        .findById(assetId)
        .orElseThrow(() -> new ElementNotFoundException("Asset not found with id: " + assetId));
  }

  public List<Asset> assets(@NotNull final List<String> assetIds) {
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

  @Transactional
  public void saveAllAssets(List<Asset> assets) {
    // Improve perfs for save all
    for (int i = 0; i < assets.size(); i++) {
      assetRepository.save(assets.get(i));
      // Flush and clear the session every 50 (batch_size property) inserts
      if (i % 50 == 0) {
        entityManager.flush();
        entityManager.clear();
      }
    }
  }
}
