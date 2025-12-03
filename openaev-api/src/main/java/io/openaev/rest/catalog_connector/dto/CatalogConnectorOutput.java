package io.openaev.rest.catalog_connector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.CatalogConnector;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Builder
@Data
public class CatalogConnectorOutput {

  @JsonProperty("catalog_connector_id")
  @NotBlank
  private String id;

  @JsonProperty("catalog_connector_title")
  @NotBlank
  private String title;

  @JsonProperty("catalog_connector_description")
  private String description;

  @JsonProperty("catalog_connector_short_description")
  private String shortDescription;

  @JsonProperty("catalog_connector_logo_url")
  private String logoUrl;

  @JsonProperty("catalog_connector_verified")
  private boolean isVerified;

  @JsonProperty("catalog_connector_subscription_link")
  private String subscriptionLink;

  @JsonProperty("catalog_connector_source_code")
  private String sourceCode;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @JsonProperty("catalog_connector_type")
  @NotNull
  private CatalogConnector.CONNECTOR_TYPE containerType;

  @JsonProperty("catalog_connector_last_verified_date")
  private Instant lastVerifiedDate;

  @JsonProperty("catalog_connector_use_cases")
  private Set<String> useCases;

  @JsonProperty("catalog_connector_manager_supported")
  private boolean isManagerSupported;
}
