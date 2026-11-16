package io.openaev.database.model;

import static io.openaev.database.model.Secret.SECRET_TYPE.API_TOKEN_VALUE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Entity
@DiscriminatorValue(API_TOKEN_VALUE)
@EntityListeners(ModelBaseListener.class)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class ApiTokenSecret extends Secret {

  @Column(name = "secret_api_token")
  @JsonIgnore
  @NotBlank
  private String apiToken;
}
