package io.openaev.api.custom_domain.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomDomainInput {

  @NotBlank
  @JsonProperty("custom_domain_hostname")
  @Schema(
      description = "Fully-qualified hostname to serve landing pages on, e.g. security.acme.com")
  private String hostname;
}
