package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAiStatus extends OcsfObject {
  /**
   * The outcome of the agent runtime's permission check for this operation, as reported by the
   * runtime. Normalized to the caption of the <code>permission_result_id</code> value; when the
   * value is 'Other', this carries the runtime's native label.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "permission_result")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT permissionResultField;

  /**
   * The normalized outcome of the agent runtime's permission check for this operation, as reported
   * by the runtime. Producers map their runtime's native value into this set; when the value is
   * 'Other', set <code>permission_result</code> to the runtime's native label.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "permission_result_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT permissionResultIdField;

  /**
   * The reason the model completion ended, as reported by the event source. Normalized to the
   * caption of the <code>stop_reason_id</code> value (e.g. <code>Token Limit</code>); when the
   * value is 'Other', this carries the event source's native label (e.g. <code>recitation</code>,
   * <code>malformed_function_call</code>).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "stop_reason")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT stopReasonField;

  /**
   * The normalized reason the model completion ended. Producers map their runtime's native value
   * into this set. Populate on completion events; when the value is 'Other', set <code>stop_reason
   * </code> to the event source's native label. Because completion behavior is bound to the
   * configuration that produced it, a shift in this attribute's distribution is more meaningful
   * when weighed against that configuration: <code>ai_model.version</code> for direct model
   * invocations, and <code>ai_agent.version</code>, <code>ai_agent.charter</code>, and <code>
   * ai_agent.instance_uid</code> for agent-mediated operations.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "stop_reason_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT stopReasonIdField;
}
