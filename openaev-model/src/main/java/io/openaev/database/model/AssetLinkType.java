package io.openaev.database.model;

/**
 * What a link between two assets means, read from the source asset to the linked asset.
 *
 * <p>The set is deliberately small: it covers what an estate and attack-surface graph needs to
 * pivot on (an account that logs on to a host, a service that runs on a host, a host that reaches
 * another), not a full CMDB relationship vocabulary.
 */
public enum AssetLinkType {
  /** The source asset reaches the linked asset over the network. */
  CONNECTS_TO,
  /** The source asset needs the linked asset to work. */
  DEPENDS_ON,
  /**
   * The source asset hosts the linked asset (a hypervisor and its guests, a cluster and a node).
   */
  HOSTS,
  /** The source identity asset has an account or a session on the linked endpoint. */
  LOGGED_ON,
  /** The source asset (a service, an AI target) runs on the linked asset. */
  RUNS_ON,
  /** The source asset uses the linked asset (a security platform, a shared service). */
  USES
}
