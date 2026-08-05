package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * A capability question asked by the orchestrator (the {@code openaev_capability_gaps} tool) or the
 * run-creation UI: "for these techniques / desired outputs, what can this platform actually do, and
 * what is missing?". The resolver answers per token against the installed injector contracts and,
 * for anything unsatisfied, suggests marketplace connectors to install.
 *
 * <p>All lists are optional; an empty query yields an empty report. Techniques are MITRE ATT&amp;CK
 * external ids ({@code T1110}), output types are {@link
 * io.openaev.database.model.ContractOutputType} labels ({@code credentials}, {@code cve}, {@code
 * port}...), matching the typed primitive/complex model the chaining engine now speaks.
 */
@Getter
@Setter
@Schema(description = "Capability resolution query (techniques / desired outputs / platforms)")
public class CapabilityQueryInput {

  @JsonProperty("techniques")
  @Schema(description = "MITRE ATT&CK technique external ids to resolve (e.g. T1110, T1566)")
  private List<String> techniques;

  @JsonProperty("output_types")
  @Schema(
      description =
          "Desired output/primitive types the AI needs produced (ContractOutputType labels: "
              + "credentials, cve, port, share, kerberoastable_account...)")
  private List<String> outputTypes;

  @JsonProperty("platforms")
  @Schema(description = "Optional platform filter (Windows, Linux, MacOS) applied to matches")
  private List<String> platforms;

  @JsonProperty("objective_template_key")
  @Schema(
      description =
          "Optional objective template key; its kill-chain focus seeds the resolution when no "
              + "explicit techniques/output types are given")
  private String objectiveTemplateKey;
}
