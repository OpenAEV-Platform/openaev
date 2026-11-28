package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAiCapability extends OcsfObject {
  /**
   * A fingerprint of the capability's declared contract as recorded at registration or approval
   * time, identifying the approved contract version without embedding the contract body in the
   * event. Compared against the contract fingerprints presented at invocation to detect a declared
   * contract that changed after approval, such as supply-chain "rug pull" changes. Carries the
   * approved value rather than a match verdict; consumers diff, the object does not assess.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "baseline_fingerprint")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectFingerprint baselineFingerprintField;

  /**
   * The declared cache scope, normalized to the caption of <code>cache_scope_id</code>. In the case
   * of <code>Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cache_scope")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT cacheScopeField;

  /**
   * The normalized identifier of the cache scope the serving system declared for this capability's
   * results, modeled on the MCP <code>cacheScope</code> field (itself modeled on HTTP
   * Cache-Control). Determines whether shared intermediaries may cache the result - a data-exposure
   * signal: a <code>Private</code> result cached in a shared scope is a cross-user exposure. Note
   * this is declared caching metadata observed on the serving response, not an invariant property
   * of the capability itself.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cache_scope_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT cacheScopeIdField;

  /**
   * The declared freshness window for this capability's results, in milliseconds, per the MCP
   * <code>ttlMs</code> field. Bounds how long a cached result was treated as valid by the consuming
   * system.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cache_ttl")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT cacheTtlField;

  /**
   * A fingerprint of the capability's declared natural-language description or instructions (e.g.,
   * the tool docstring or manifest description presented at discovery), identifying the description
   * version without embedding the description body in the event. The object carries no <code>desc
   * </code> attribute; this fingerprints the model-visible description by reference. Enables
   * detecting description poisoning, where the description is altered without any schema change.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc_fingerprint")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectFingerprint descFingerprintField;

  /**
   * A fingerprint of the capability's declared input contract (e.g., its JSON Schema), identifying
   * the contract version without embedding the schema body in the event. Enables grouping by
   * tool-contract version and detecting contract drift, including "rug pull" changes where a tool's
   * schema changes after initial approval. Use <code>serialization_id: JCS</code> when the
   * fingerprint is computed over canonicalized JSON.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "input_schema_fingerprint")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectFingerprint inputSchemaFingerprintField;

  /**
   * Indicates the capability declared that it may perform destructive updates, per the MCP <code>
   * destructiveHint</code> tool annotation. This is a self-declared, unverified hint from the
   * serving system: it supports triage and pivoting but must not be treated as an enforced
   * property.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_destructive")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeBooleanT isDestructiveField;

  /**
   * Indicates the capability declared that repeated calls with the same arguments have no
   * additional effect, per the MCP <code>idempotentHint</code> tool annotation. This is a
   * self-declared, unverified hint from the serving system.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_idempotent")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeBooleanT isIdempotentField;

  /**
   * Indicates the capability declared that it may interact with an open world of external entities
   * (e.g., the web), rather than a closed domain, per the MCP <code>openWorldHint</code> tool
   * annotation. This is a self-declared, unverified hint from the serving system.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_open_world")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeBooleanT isOpenWorldField;

  /**
   * Indicates the capability declared that it does not modify its environment, per the MCP <code>
   * readOnlyHint</code> tool annotation. This is a self-declared, unverified hint from the serving
   * system: it supports triage and pivoting (e.g., "every write-capable tool an agent invoked") but
   * must not be treated as an enforced property, and a mismatch between this hint and observed
   * behavior is itself a signal.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_readonly")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeBooleanT isReadonlyField;

  /**
   * The declared MIME type of the capability's content. Primarily for <code>Resource</code>
   * primitives, e.g. <code>text/markdown</code> or <code>application/json</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "mime_type")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT mimeTypeField;

  /**
   * The name of the capability as advertised by its provider: the MCP tool, resource, or prompt
   * name, the function name of a framework-native tool, or the identifier of a built-in tool. For
   * example: <code>get_weather</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The namespace or grouping the capability belongs to, when the provider organizes capabilities
   * into named collections. For example: <code>weather</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT namespaceField;

  /**
   * A fingerprint of the capability's declared structured-output contract, identifying the contract
   * version without embedding the schema body in the event. Use <code>serialization_id: JCS</code>
   * when the fingerprint is computed over canonicalized JSON.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "output_schema_fingerprint")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectFingerprint outputSchemaFingerprintField;

  /**
   * The logical serving system that advertised and served the capability - for MCP, the MCP server.
   * This is provenance, not a network endpoint: when a gateway or proxy sits in the path, <code>
   * dst_endpoint</code> identifies the gateway that was contacted, while this attribute identifies
   * the logical server that actually served the primitive behind it. For <code>stdio</code>
   * -transported servers there is no network endpoint at all, and this attribute is the only record
   * of the serving system.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectService serviceField;

  /**
   * The tool source, normalized to the caption of <code>source_id</code>. In the case of <code>
   * Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "source")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT sourceField;

  /**
   * The normalized identifier of the mechanism through which the capability was provided to the AI
   * system. Orthogonal to <code>type_id</code>, which is the kind of primitive: a <code>Tool</code>
   * may be sourced via MCP, via framework-native function calling, or as a model provider's
   * built-in. In the case of 'Other', it is defined by the event source and carried by the <code>
   * source</code> attribute.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "source_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT sourceIdField;

  /**
   * The task handle returned for a long-running invocation, per the MCP Tasks extension. Correlates
   * the originating call with subsequent task lifecycle events (get, update, cancel) so an
   * asynchronous tool invocation remains a single thread of activity across events.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "task_uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT taskUidField;

  /**
   * The capability-layer invocation identifier that correlates a request with its result across
   * events - for MCP, the JSON-RPC request id of the call. Distinct from <code>api.request.uid
   * </code>, which is transport-scoped, and from <code>uid</code>, which identifies the capability
   * itself rather than an invocation of it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "transaction_uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT transactionUidField;

  /**
   * The transport, normalized to the caption of <code>transport_id</code>. In the case of <code>
   * Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "transport")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT transportField;

  /**
   * The normalized identifier of the transport binding the client used to reach the serving system.
   * Distinguishes a local subprocess server from a remote networked server - a real trust boundary,
   * and the reason an event's network endpoint attributes may legitimately be absent (a <code>stdio
   * </code> server has no network endpoint). Not an IP-layer protocol: <code>protocol_name</code>
   * does not apply, and <code>stdio</code> is not a network protocol at all.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "transport_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT transportIdField;

  /**
   * The kind of capability primitive, normalized to the caption of the <code>type_id</code> value.
   * In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The normalized identifier of the kind of capability primitive, per the Model Context Protocol
   * primitive taxonomy. The three primitives carry different risk profiles; a first-class
   * discriminator makes questions like "resource reads vs. tool calls" queryable rather than buried
   * in free-text operation names.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * The stable identifier of the capability itself, when one exists: a registry or catalog
   * identifier, or a producer-derived stable key (e.g., serving server identity plus capability
   * name). Identifies the capability across invocations; per-invocation correlation is carried by
   * <code>transaction_uid</code> instead.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT uidNumericField;

  /**
   * The URI of the capability. For <code>Resource</code> primitives this is the resource URI that
   * was read, e.g. <code>file:///project/README.md</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uri")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeUrlT uriField;

  /** The version of the capability as advertised by its provider, when versioned. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT versionField;
}
