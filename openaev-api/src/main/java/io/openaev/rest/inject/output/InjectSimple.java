package io.openaev.rest.inject.output;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(NON_NULL)
public class InjectSimple {

  @Schema(description = "Inject Id")
  @JsonProperty("inject_id")
  @NotBlank
  private String id;

  @Schema(description = "Inject Title")
  @JsonProperty("inject_title")
  @NotBlank
  private String title;
}
