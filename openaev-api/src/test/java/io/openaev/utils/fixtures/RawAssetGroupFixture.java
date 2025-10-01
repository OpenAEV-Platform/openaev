package io.openaev.utils.fixtures;

import io.openaev.database.raw.RawAssetGroup;
import java.util.List;

public class RawAssetGroupFixture {

  public static RawAssetGroup createDefaultRawAssetGroup(
      String id, String name, List<String> assetIds) {
    return new RawAssetGroup() {
      @Override
      public String getAsset_group_id() {
        return id;
      }

      @Override
      public String getAsset_group_name() {
        return name;
      }

      @Override
      public List<String> getAsset_ids() {
        return assetIds;
      }

      @Override
      public String getAsset_group_dynamic_filter() {
        return "";
      }
    };
  }
}
