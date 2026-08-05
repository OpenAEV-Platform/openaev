package io.openaev.service.attackpath;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;

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
   * Capacity of the {@code attackpath_finding_id} column ({@code varchar(255)}): a raw {@code
   * FINDING_ROW} id at most this long keeps its legacy (un-hashed) form, so ids computed before the
   * value hashing was introduced stay stable across upgrades.
   */
  private static final int FINDING_ROW_ID_MAX_LENGTH = 255;

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

  /**
   * {@code NODE_INJECTOR} per contract: the injector name plus the frozen contract external id, so
   * an injector that ran several contracts renders one node per contract. A {@code null} contract
   * falls back to the per-injector id, byte-identical, so contractless sources (seed, legacy,
   * agents) keep the same id.
   */
  public static String injectorNode(String injector, String contractExternalId) {
    return contractExternalId == null
        ? injectorNode(injector)
        : encode("NODE_INJECTOR", injector, contractExternalId);
  }

  /** {@code NODE_ENDPOINT}: the unified endpoint key (asset id or raw value). DTO type = ASSET. */
  public static String endpointNode(String endpointKey) {
    return encode("NODE_ENDPOINT", endpointKey);
  }

  /** {@code NODE_EXECUTION}: the execution row plus its target and agent. */
  public static String executionNode(String executionId, String targetKey, String agentId) {
    return encode("NODE_EXECUTION", executionId, targetKey, agentId);
  }

  /** {@code EXECUTION_COLLECTOR_ROW}: one collector-result snapshot line of an execution. */
  public static String executionCollectorRow(String executionId, String bucket, String sourceKey) {
    return encode("EXECUTION_COLLECTOR_ROW", executionId, bucket, sourceKey);
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

  /**
   * {@code FINDING_ROW}: a copied finding's full identity within a simulation ({@code
   * simulationId}, {@code type}, {@code field}, {@code value}, {@code endpointKey}). Deterministic,
   * so re-copying the same finding lands on the same row; the simulation is part of the id, so the
   * same finding in two runs never collides.
   *
   * <p>ADR-004 lets arbitrarily long parsed outputs reach {@code attackpath_finding} (unlike short
   * {@link io.openaev.database.model.Finding} values), and a raw long value overflowed the {@code
   * varchar(255)} primary key. When the raw encoding fits the column, it is kept as-is - so every
   * row copied before the hashing was introduced still resolves to its legacy id, and a re-copy
   * upserts onto the existing row instead of duplicating it. Only when the raw encoding would
   * overflow does the id switch to a variant that hashes the whole {@code value} with SHA-256
   * (never truncated), under the distinct {@code FINDING_ROW_H} kind. If that variant still
   * overflows (the excess length comes from another component, e.g. a very long {@code
   * endpointKey}), the last resort hashes the whole raw id under the {@code FINDING_ROW_F} kind,
   * whose length is a constant 78 chars. The three kinds are distinct, so the namespaces can never
   * collide (and no pre-existing row can carry an overflowing raw id: those inserts failed). Every
   * variant is a deterministic, collision-free function of the same natural key; the real value is
   * kept untouched in {@code attackpath_finding_value} ({@code text}) for display.
   */
  public static String findingRow(
      String simulationId, String type, String field, String value, String endpointKey) {
    String rawId = encode("FINDING_ROW", simulationId, type, field, value, endpointKey);
    if (rawId.length() <= FINDING_ROW_ID_MAX_LENGTH) {
      return rawId;
    }
    String hashedValueId =
        encode(
            "FINDING_ROW_H",
            simulationId,
            type,
            field,
            value == null ? null : hashWithSHA256(value),
            endpointKey);
    if (hashedValueId.length() <= FINDING_ROW_ID_MAX_LENGTH) {
      return hashedValueId;
    }
    // The overflow comes from another component: hashing the full raw id (already an injective
    // encoding of the natural key) yields a fixed-size id that can never overflow.
    return encode("FINDING_ROW_F", hashWithSHA256(rawId));
  }

  /** {@code EDGE_ENDPOINT_FINDINGS_TYPE}: an endpoint to one of its finding-type nodes. */
  public static String endpointFindingTypeEdge(String type, String endpointKey) {
    return encode("EDGE_ENDPOINT_FINDINGS_TYPE", type, endpointKey);
  }

  /** {@code EDGE_FINDINGS_TYPE_FINDING}: a finding-type node to a specific finding. */
  public static String findingTypeFindingEdge(String type, String endpointKey, String value) {
    return encode("EDGE_FINDINGS_TYPE_FINDING", type, endpointKey, value);
  }

  /**
   * {@code EXECUTION_REMEDIATION_ROW}: one remediation snapshot attached to a step execution.
   *
   * <p>The collector type is normalized to {@code "0"} when null/blank to keep a deterministic id
   * even for remediation rows that no longer carry a collector key.
   */
  public static String executionRemediationRow(
      String stepExecutionId, String collectorType, String securityPlatformId) {
    String normalizedCollectorType =
        collectorType == null || collectorType.isBlank() ? "0" : collectorType;
    return encode(
        "EXECUTION_REMEDIATION_ROW", stepExecutionId, normalizedCollectorType, securityPlatformId);
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
