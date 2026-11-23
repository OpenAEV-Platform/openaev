package io.openaev.database.model;

/** The role of a person or a team toward an asset they own (#9725). */
public enum AssetOwnerRole {
  /** Business owner: accountable for the asset. */
  OWNED_BY,
  /** Technical owner or operator: runs the asset. */
  MANAGED_BY
}
