package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class DefaultGrant {

  @JsonProperty("grant_type")
  @Column(name = "grant_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private Grant.GRANT_TYPE grantType;

  @JsonProperty("grant_resource_type")
  @Column(name = "grant_resource_type", nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  private Grant.GRANT_RESOURCE_TYPE grantResourceType;
}
