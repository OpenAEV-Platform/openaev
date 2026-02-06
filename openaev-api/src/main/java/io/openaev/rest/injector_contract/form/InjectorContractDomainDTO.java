package io.openaev.rest.injector_contract.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Domain;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InjectorContractDomainDTO {
  @JsonProperty("domain_id")
  @NotBlank
  private String id;

  @JsonProperty("domain_name")
  @NotBlank
  private String name;

  @JsonProperty("domain_color")
  @NotBlank
  private String color;

  public static InjectorContractDomainDTO fromDomain(Domain domain) {
    InjectorContractDomainDTO dto = new InjectorContractDomainDTO();
    dto.setId(domain.getId());
    dto.setName(domain.getName());
    dto.setColor(domain.getColor());
    return dto;
  }
}
