package io.openaev.database.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Data;

/**
 * A person or a team owning an asset (#9725), as business owner ({@link AssetOwnerRole#OWNED_BY})
 * or technical owner ({@link AssetOwnerRole#MANAGED_BY}). Exactly one of {@code userId} and {@code
 * teamId} is set.
 *
 * <p>A join row like {@link AssetLink}: no tenant of its own. The asset carries it, the marking
 * registry derives the row from its asset, and {@code AssetOwnerService} refuses an owner outside
 * the asset's tenant. Deleting the asset, the person or the team deletes the row (foreign keys
 * cascade).
 */
@Data
@Entity
@Table(name = "assets_owners")
public class AssetOwner {

  @Id
  @Column(name = "asset_owner_id")
  private String id;

  @Column(name = "asset_id", nullable = false)
  private String assetId;

  @Column(name = "asset_owner_user_id")
  private String userId;

  @Column(name = "asset_owner_team_id")
  private String teamId;

  @Column(name = "asset_owner_role", nullable = false)
  @Enumerated(EnumType.STRING)
  private AssetOwnerRole role;

  @Column(name = "asset_owner_source", nullable = false)
  @Enumerated(EnumType.STRING)
  private AssetRelationSource source = AssetRelationSource.MANUAL;

  @Column(name = "asset_owner_created_at", updatable = false)
  private Instant createdAt = Instant.now();
}
