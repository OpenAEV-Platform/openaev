package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.PrimitiveType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * One predicate of a finding-driven trigger: "a finding emitted a {@code key_type} value that
 * satisfies {@code operator} {@code value}". Leaves are combined by the parent trigger's {@code
 * match} (AND / OR). Because the chaining engine matches readiness on the finding's primitive
 * {@code key_type} (not on a human name), this is the real, functional trigger - e.g. {@code
 * {key_type: "port", operator: "EQ", value: "445"}} fires a step once any upstream inject emits a
 * finding carrying port 445.
 */
@Getter
@Setter
@Schema(description = "A single predicate of a finding-driven trigger")
public class AutonomousTriggerFilter {

  @JsonProperty("key_type")
  @Schema(
      description =
          "The finding primitive to test, as its lowercase label (e.g. port, host, ipv4, cve,"
              + " username, password, hash, share_name, service, severity, kerberoastable_account)."
              + " This is the field an upstream inject's output parser emitted.")
  private PrimitiveType keyType;

  @JsonProperty("operator")
  @Schema(
      description =
          "Comparison: EQ, NEQ, IS_NULL, IS_NOT_NULL, GT, GTE, LT, LTE, IN, NIN. Defaults to"
              + " IS_NOT_NULL (fire as soon as the finding carries this key_type at all).")
  private ConditionType operator;

  @JsonProperty("value")
  @Schema(description = "The value to compare against (omit for IS_NULL / IS_NOT_NULL).")
  private String value;

  @JsonProperty("case_sensitive")
  @Schema(description = "Whether the comparison is case-sensitive (default true).")
  private Boolean caseSensitive;
}
