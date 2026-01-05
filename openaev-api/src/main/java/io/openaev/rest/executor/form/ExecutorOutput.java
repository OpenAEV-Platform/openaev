package io.openaev.rest.executor.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorSimpleOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.Builder;
import org.hibernate.annotations.Type;

@Builder
@Schema(description = "Executor output")
public class ExecutorOutput {

  @Schema(description = "Executor id")
  @JsonProperty("executor_id")
  @NotBlank
  private String id;

  @JsonProperty("executor_name")
  @NotBlank
  private String name;

  @JsonProperty("executor_type")
  @NotBlank
  private String type;

  @JsonProperty("executor_updated_at")
  private Instant updatedAt;

  @JsonProperty("catalog")
  private CatalogConnectorSimpleOutput catalog;

  @JsonProperty("is_verified")
  private boolean verified = false;

  @Type(StringArrayType.class)
  @JsonProperty("executor_platforms")
  private String[] platforms;

  @JsonProperty("executor_doc")
  private String doc;

  @JsonProperty("executor_background_color")
  private String backgroundColor;
}
