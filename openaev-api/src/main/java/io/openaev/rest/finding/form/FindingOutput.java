package io.openaev.rest.finding.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Single finding representation returned by the finding CRUD endpoints. It exists so the API never
 * serializes the {@link io.openaev.database.model.Finding} entity directly: the value carried here
 * is the masked one when the finding type holds secret material, while the database row keeps the
 * cleartext.
 */
@Data
@SuperBuilder
@JsonInclude(NON_NULL)
public class FindingOutput {

  @Schema(description = "Finding Id")
  @JsonProperty("finding_id")
  @NotBlank
  private String id;

  @Schema(
      description = "Represents the data type being extracted.",
      example = "text, number, port, portscan, ipv4, ipv6, credentials, cve")
  @JsonProperty("finding_type")
  @NotNull
  private ContractOutputType type;

  @Schema(
      description =
          "Finding value. Masked when the finding type holds secret material: the API never"
              + " discloses the cleartext value of a sensitive finding.")
  @JsonProperty("finding_value")
  @NotBlank
  private String value;

  @Schema(description = "Tag ids linked to the finding")
  @JsonProperty("finding_tags")
  private Set<String> tags;

  @Schema(description = "First time the finding was seen")
  @JsonProperty("finding_first_seen")
  private Instant firstSeen;

  @Schema(description = "Last time the finding was seen")
  @JsonProperty("finding_last_seen")
  private Instant lastSeen;

  @Schema(description = "Number of occurrences, one per inject that detected the finding")
  @JsonProperty("finding_occurrences_count")
  private long occurrencesCount;

  @Schema(description = "Asset or player the finding was detected on, absent when unknown")
  @JsonProperty("finding_location")
  private TargetSimple location;
}
