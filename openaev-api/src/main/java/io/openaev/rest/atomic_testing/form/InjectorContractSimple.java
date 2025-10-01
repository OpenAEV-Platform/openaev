package io.openaev.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.converter.ContentConverter;
import io.openaev.database.model.Endpoint;
import io.openaev.rest.payload.output.PayloadSimple;
import jakarta.persistence.Convert;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class InjectorContractSimple {

  @JsonProperty("injector_contract_id")
  @NotBlank
  private String id;

  @JsonProperty("injector_contract_content")
  @NotBlank
  private String content;

  @JsonProperty("convertedContent")
  @Convert(converter = ContentConverter.class)
  private ObjectNode convertedContent;

  @JsonProperty("injector_contract_platforms")
  private Endpoint.PLATFORM_TYPE[] platforms;

  @JsonProperty("injector_contract_payload")
  private PayloadSimple payload;

  @Builder.Default
  @JsonProperty("injector_contract_labels")
  @NotBlank
  private Map<String, String> labels = new HashMap<>();
}
