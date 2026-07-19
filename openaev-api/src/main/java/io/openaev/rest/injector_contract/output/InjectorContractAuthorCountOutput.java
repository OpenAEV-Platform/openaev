package io.openaev.rest.injector_contract.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Distinct-author facet row for the Threat Arsenal sidebar: the resolved author (contract-level
 * first, then payload-level) plus how many contracts match under the current filters. Lets the UI
 * keep every author visible and grey out the zero-count ones, like the domain facet.
 */
@Getter
@Setter
@NoArgsConstructor
public class InjectorContractAuthorCountOutput {
  @JsonProperty("author")
  @Schema(description = "Author id (user, team or organization)")
  private String author;

  @JsonProperty("author_name")
  @Schema(description = "Author display name")
  private String authorName;

  @JsonProperty("author_type")
  @Schema(description = "Author type: user, team or organization")
  private String authorType;

  @JsonProperty("count")
  @Schema(description = "Number of contracts authored by this author under the current filters")
  private Long count;

  public InjectorContractAuthorCountOutput(
      String author, String authorName, String authorType, Long count) {
    this.author = author;
    this.authorName = authorName;
    this.authorType = authorType;
    this.count = count;
  }
}
