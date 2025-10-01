package io.openaev.database.model;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.Data;

@Data
@Entity
@IdClass(AssetGroupAsset.AssetGroupAssetId.class)
@Table(name = "asset_groups_assets")
public class AssetGroupAsset {

  @Id
  @Column(name = "asset_group_id")
  private String assetGroupId;

  @Id
  @Column(name = "asset_id")
  private String assetId;

  @Data
  public static class AssetGroupAssetId implements Serializable {

    private String assetGroupId;
    private String assetId;
  }
}
