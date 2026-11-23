package io.openaev.database.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import lombok.Data;

/**
 * A typed, directed link between two assets of the same tenant (an identity asset logged on to an
 * endpoint, a service running on a host...).
 *
 * <p>A join row like {@link AssetGroupAsset}: it carries no tenant of its own. Both ends are
 * assets, whose reads the tenant and marking inspectors already scope, and {@code AssetLinkService}
 * refuses a link between two tenants. Every read joins both ends to {@code assets}, so a link is
 * never seen without the two assets it joins. Deleting either asset deletes its links (foreign keys
 * cascade).
 */
@Data
@Entity
@IdClass(AssetLink.AssetLinkId.class)
@Table(name = "assets_links")
public class AssetLink {

  @Id
  @Column(name = "asset_id")
  private String assetId;

  @Id
  @Column(name = "linked_asset_id")
  private String linkedAssetId;

  @Id
  @Column(name = "asset_link_type")
  @Enumerated(EnumType.STRING)
  private AssetLinkType type;

  @Column(name = "asset_link_created_at", updatable = false)
  private Instant createdAt = Instant.now();

  @Data
  public static class AssetLinkId implements Serializable {

    private String assetId;
    private String linkedAssetId;
    private AssetLinkType type;
  }
}
