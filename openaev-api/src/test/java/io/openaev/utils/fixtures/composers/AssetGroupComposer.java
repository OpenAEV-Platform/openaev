package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.repository.AssetGroupRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AssetGroupComposer extends ComposerBase<AssetGroup> {
  @Autowired private AssetGroupRepository assetGroupRepository;

  public class Composer extends InnerComposerBase<AssetGroup> {
    private final AssetGroup assetGroup;
    private final List<EndpointComposer.Composer> endpointComposers = new ArrayList<>();

    public Composer(AssetGroup assetGroup) {
      this.assetGroup = assetGroup;
    }

    public Composer withAsset(EndpointComposer.Composer endpointComposer) {
      endpointComposers.add(endpointComposer);
      List<Asset> assets = assetGroup.getAssets();
      assets.add(endpointComposer.get());
      this.assetGroup.setAssets(assets);
      return this;
    }

    @Override
    public Composer persist() {
      endpointComposers.forEach(EndpointComposer.Composer::persist);
      assetGroupRepository.save(assetGroup);
      return this;
    }

    @Override
    public Composer delete() {
      endpointComposers.forEach(EndpointComposer.Composer::delete);
      assetGroupRepository.delete(assetGroup);
      return this;
    }

    @Override
    public AssetGroup get() {
      return this.assetGroup;
    }
  }

  public Composer forAssetGroup(AssetGroup assetGroup) {
    generatedItems.add(assetGroup);
    return new Composer(assetGroup);
  }
}
