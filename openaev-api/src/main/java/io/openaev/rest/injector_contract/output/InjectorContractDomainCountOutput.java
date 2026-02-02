package io.openaev.rest.injector_contract.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InjectorContractDomainCountOutput {
  @NotBlank
  @JsonProperty("domain")
  @Schema(description = "The domain name extracted from OpenAEV", example = "Endpoints")
  private String domain;

  @NotNull
  @JsonProperty("count")
  @Schema(description = "Total number of observations linked to this domain", example = "42")
  private Long count;

  public InjectorContractDomainCountOutput(String domain, Long count) {
    this.domain = domain;
    this.count = count;
  }
}
