package io.openaev.service.attackpath;

/**
 * Deterministic, collision-safe IDs for the attack-path graph (issue 6647). Every node and edge
 * carries a stable string ID built from its columns, so the same inputs always produce the same ID
 * and the render layer can upsert in O(1). The IDs mirror the render contract's composition rules
 * (see the design's deterministic ID table).
 *
 * <p>Encoding: a kind prefix followed by the components, joined by {@code |}. Each component
 * escapes {@code \} and {@code |} so the encoding is injective: two different component splits can
 * never produce the same string (e.g. {@code finding("a", "b|c")} and {@code finding("a|b", "c")}
 * differ). A {@code null} component encodes as an explicit marker distinct from any real value.
 */
public final class AttackPathIds {

  private static final char DELIMITER = '|';

  /**
   * Prefix of a synthetic seed simulation id ({@code AttackPathSeedService} builds ids as {@code
   * <prefix><seed>-sim-<n>}). Seed simulations are not real {@code exercises}, so resource-level
   * RBAC cannot resolve them; callers use {@link #isSeedId} to keep them reachable (see {@code
   * AttackPathAccessControl}).
   */
  public static final String SEED_ID_PREFIX = "ap-seed-";

  /** Whether {@code id} is a synthetic seed simulation id (not a real exercise). */
  public static boolean isSeedId(String id) {
    return id != null && id.startsWith(SEED_ID_PREFIX);
  }

  /**
   * Marker for a {@code null} component. The two chars {@code \0} can never occur in an escaped
   * non-null component (there, a {@code \} is always followed by {@code \} or {@code |}), so a null
   * component never collides with a real value.
   */
  private static final String NULL_MARKER = "\\0";

  private AttackPathIds() {}

  /** {@code NODE_INJECTOR}: the injector name (source_kind = INJECTOR). */
  public static String injectorNode(String injector) {
    return encode("NODE_INJECTOR", injector);
  }

  /** {@code NODE_ENDPOINT}: the unified endpoint key (asset id or raw value). DTO type = ASSET. */
  public static String endpointNode(String endpointKey) {
    return encode("NODE_ENDPOINT", endpointKey);
  }

  /** {@code NODE_EXECUTION}: the execution row plus its target and agent. */
  public static String executionNode(String executionId, String targetKey, String agentId) {
    return encode("NODE_EXECUTION", executionId, targetKey, agentId);
  }

  /** {@code EDGE_EXECUTIONS}: source node to target node; grouped rows share this edge. */
  public static String executionsEdge(String sourceNodeId, String targetNodeId) {
    return encode("EDGE_EXECUTIONS", sourceNodeId, targetNodeId);
  }

  /** {@code NODE_FINDINGS_TYPE}: a finding type on a specific endpoint. */
  public static String findingTypeNode(String type, String endpointKey) {
    return encode("NODE_FINDINGS_TYPE", type, endpointKey);
  }

  /** {@code NODE_FINDING}: a single finding, deduped by (type, value) across endpoints. */
  public static String findingNode(String type, String value) {
    return encode("NODE_FINDING", type, value);
  }

  /** {@code EDGE_ENDPOINT_FINDINGS_TYPE}: an endpoint to one of its finding-type nodes. */
  public static String endpointFindingTypeEdge(String type, String endpointKey) {
    return encode("EDGE_ENDPOINT_FINDINGS_TYPE", type, endpointKey);
  }

  /** {@code EDGE_FINDINGS_TYPE_FINDING}: a finding-type node to a specific finding. */
  public static String findingTypeFindingEdge(String type, String endpointKey, String value) {
    return encode("EDGE_FINDINGS_TYPE_FINDING", type, endpointKey, value);
  }

  private static String encode(String kind, String... parts) {
    StringBuilder builder = new StringBuilder(kind);
    for (String part : parts) {
      builder.append(DELIMITER).append(escape(part));
    }
    return builder.toString();
  }

  private static String escape(String part) {
    if (part == null) {
      return NULL_MARKER;
    }
    // Fast path: the common case has no special char, so no allocation and no copy.
    if (part.indexOf('\\') < 0 && part.indexOf(DELIMITER) < 0) {
      return part;
    }
    StringBuilder builder = new StringBuilder(part.length() + 4);
    for (int i = 0; i < part.length(); i++) {
      char c = part.charAt(i);
      if (c == '\\' || c == DELIMITER) {
        builder.append('\\');
      }
      builder.append(c);
    }
    return builder.toString();
  }
}
