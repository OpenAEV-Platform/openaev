package io.openaev.database.repository;

import io.openaev.database.model.StableFinding;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

public interface StableFindingRepositoryCustom {

  record SourceCount(String id, String name, String type, long count) {}

  record AssetCount(String id, String name, long count) {}

  Map<String, Long> countByType(Specification<StableFinding> specification);

  Map<String, Long> countBySeverity(Specification<StableFinding> specification);

  Map<String, Long> countByCloudProvider(Specification<StableFinding> specification);

  Map<String, Long> countByTriageStatus(Specification<StableFinding> specification);

  Map<String, SourceCount> countBySource(Specification<StableFinding> specification);

  /**
   * Findings per location asset, for the Asset quick filter; findings without asset are left out.
   */
  Map<String, AssetCount> countByAsset(Specification<StableFinding> specification);
}
