package io.openaev.utils.mapper;

import io.openaev.database.model.AssetGroup;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssetGroupMapper {

  public AssetGroupSimple toAssetGroupSimple(AssetGroup assetGroup) {
    return AssetGroupSimple.builder().id(assetGroup.getId()).name(assetGroup.getName()).build();
  }
}
