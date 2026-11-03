package io.openaev.utils.mapper;

import static java.util.Collections.emptySet;

import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Tag;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssetGroupMapper {

  public Set<AssetGroupSimple> toAssetGroupSimples(Set<AssetGroup> assetGroup) {
    return Optional.ofNullable(assetGroup).orElse(emptySet()).stream()
        .map(this::toAssetGroupSimple)
        .collect(Collectors.toSet());
  }

  public AssetGroupSimple toAssetGroupSimple(AssetGroup assetGroup) {
    return AssetGroupSimple.builder().id(assetGroup.getId()).name(assetGroup.getName()).build();
  }

  public AssetGroupOutput toAssetGroupOutput(AssetGroup assetGroup) {
    return AssetGroupOutput.builder()
        .id(assetGroup.getId())
        .name(assetGroup.getName())
        .description(assetGroup.getDescription())
        .dynamicFilter(assetGroup.getDynamicFilter())
        .assets(assetGroup.getAssets().stream().map(Asset::getId).collect(Collectors.toSet()))
        .tags(assetGroup.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .build();
  }
}
