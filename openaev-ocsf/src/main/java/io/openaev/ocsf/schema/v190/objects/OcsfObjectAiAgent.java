package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAiAgent extends OcsfObject {
  /**
   * The AI model backing this agent at the time of the recorded event. An agent's model may change
   * across instances or versions; this captures the model in use for the specific logged activity
   * or delegation.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_model")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAiModel aiModelField;

  /**
   * A document that defines an AI agent's durable role, responsibilities, constraints, and
   * operating boundaries. When available, populate <code>hashes</code> on the file for content
   * integrity and <code>signatures</code> for provenance. Integrity of the event that reports this
   * agent is provided separately by the <code>record_integrity</code> profile.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "charter")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile charterField;

  /**
   * Identifier for a specific running instance or session of the agent, distinct from the stable
   * logical <code>uid</code>. An instance is a single materialization of the agent: a conversation,
   * session, or run. It may persist across restarts (a session that is suspended and later resumed
   * keeps the same <code>instance_uid</code>) and may span multiple cooperating runtime components,
   * so several events can share one <code>instance_uid</code>. Enables attribution of actions to a
   * particular instance of the agent rather than to the agent generally.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "instance_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT instanceUidField;

  /**
   * Human-readable name for the agent. For example: <code>Q4 Analysis Agent</code> or <code>
   * Model Tester Agent</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The agent framework, normalized to the caption of the <code>type_id</code> value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The normalized identifier for the agent framework. Different agent frameworks have different
   * identity, tool-call, and delegation semantics, so recording the framework enables
   * cross-framework normalization. Communication protocols (e.g., MCP, A2A) are a property of
   * individual operations rather than the agent itself, and are surfaced on the relevant operation
   * rather than here.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * The stable logical identifier for the agent, assigned by the agent's authoritative source
   * (e.g., its control plane, registry, or issuing identity provider). Persists across restarts and
   * instances. Producers populate this from whatever identity they observe: for a runtime that owns
   * the agent, this is its issued ID; for a gateway or proxy, it is typically derived from the
   * agent's credential. Multiple producers logging the same agent should converge on the same
   * <code>uid</code> when they share an authoritative source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  /**
   * The version of the agent: the agent's own code or configuration revision (e.g., <code>1.4.2
   * </code>), distinct from the version of the model backing it (carried on <code>ai_model.version
   * </code>). Enables correlation of behavioral changes with charter or configuration revisions
   * across agent versions.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
