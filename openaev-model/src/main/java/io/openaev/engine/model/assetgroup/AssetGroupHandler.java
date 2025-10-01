package io.openaev.engine.model.assetgroup;

import static io.openaev.engine.EsUtils.buildRestrictions;

import io.openaev.database.raw.RawAssetGroupIndexing;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.engine.Handler;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AssetGroupHandler implements Handler<EsAssetGroup> {

  private final AssetGroupRepository assetGroupRepository;

  @Override
  public List<EsAssetGroup> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawAssetGroupIndexing> forIndexing = assetGroupRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            assetGroup -> {
              EsAssetGroup esAssetGroup = new EsAssetGroup();
              // Base
              esAssetGroup.setBase_id(assetGroup.getAsset_group_id());
              esAssetGroup.setBase_created_at(assetGroup.getAsset_group_created_at());
              esAssetGroup.setBase_updated_at(assetGroup.getAsset_group_updated_at());
              esAssetGroup.setBase_representative(assetGroup.getAsset_group_name());
              esAssetGroup.setBase_restrictions(buildRestrictions(assetGroup.getAsset_group_id()));
              // Specific
              return esAssetGroup;
            })
        .toList();
  }
}
