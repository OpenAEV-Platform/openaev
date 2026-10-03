package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One (indicator, security platform) pair of an IOC validation request, stored in the {@code
 * ioc_validation_pairs} JSON column. The STIX references are kept verbatim: the result bundle
 * pushed back to OpenCTI reuses them so the deployed-on relationship is upserted, not duplicated.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IocValidationPair {

  @JsonProperty("pair_indicator_ref")
  private String indicatorRef;

  @JsonProperty("pair_platform_ref")
  private String platformRef;

  @JsonProperty("pair_deployed_on_ref")
  private String deployedOnRef;

  @JsonProperty("pair_platform_name")
  private String platformName;

  /** OpenAEV security platform asset matched by name; {@code null} when none matches. */
  @JsonProperty("pair_security_platform_id")
  private String securityPlatformId;

  /** {@code null} while the pair is pending. */
  @JsonProperty("pair_outcome")
  private IocValidationOutcome outcome;

  @JsonProperty("pair_outcome_reason")
  private String outcomeReason;

  @JsonProperty("pair_evaluated_at")
  private Instant evaluatedAt;
}
