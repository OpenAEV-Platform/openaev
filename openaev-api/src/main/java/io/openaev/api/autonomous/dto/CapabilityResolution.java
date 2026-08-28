package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The resolution of a single capability token (one technique or one output type): whether the
 * installed threat arsenal can satisfy it, the concrete contracts that do, and - when it cannot -
 * the marketplace connectors an operator could install to close the gap.
 *
 * <p>This is what powers both the UI capability-gap strip and the orchestrator's decision to either
 * execute a technique now or narrate a capability gap and (if authorized) craft a custom arsenal
 * item on the fly.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resolution of one capability token against installed contracts")
public class CapabilityResolution {

  /** What flavour of token was resolved, so the UI can group technique gaps vs output gaps. */
  public enum Kind {
    TECHNIQUE,
    OUTPUT_TYPE,
    KILL_CHAIN_PHASE
  }

  @JsonProperty("kind")
  @Schema(description = "Whether this token is a technique, an output/primitive type, or a phase")
  private Kind kind;

  @JsonProperty("token")
  @Schema(description = "The resolved token (technique external id, output type label, or phase)")
  private String token;

  @JsonProperty("label")
  @Schema(description = "Human-readable label for the token")
  private String label;

  @JsonProperty("satisfied")
  @Schema(description = "True when at least one installed contract satisfies the token")
  private boolean satisfied;

  @JsonProperty("contracts")
  @Schema(description = "Installed contracts that satisfy the token (empty when unsatisfied)")
  private List<ResolvedContract> contracts;

  @JsonProperty("suggested_connectors")
  @Schema(description = "Marketplace connectors to install to close the gap (empty when satisfied)")
  private List<SuggestedConnector> suggestedConnectors;

  /** A concrete installed contract that satisfies the queried capability. */
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "An installed injector contract that satisfies the capability")
  public static class ResolvedContract {
    @JsonProperty("injector_contract_id")
    private String injectorContractId;

    @JsonProperty("label")
    private String label;

    @JsonProperty("injector_type")
    private String injectorType;

    @JsonProperty("platforms")
    private List<String> platforms;
  }

  /** A marketplace connector suggestion, with the links an operator needs to install it. */
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "A marketplace connector suggested to close a capability gap")
  public static class SuggestedConnector {
    @JsonProperty("connector_id")
    private String connectorId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("slug")
    private String slug;

    @JsonProperty("short_description")
    private String shortDescription;

    @JsonProperty("logo_url")
    private String logoUrl;

    @JsonProperty("subscription_link")
    private String subscriptionLink;

    @JsonProperty("source_code")
    private String sourceCode;
  }
}
