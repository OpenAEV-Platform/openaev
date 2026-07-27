package io.openaev.database.model;

import static io.openaev.database.model.Secret.SECRET_TYPE.USERNAME_PASSWORD_VALUE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Entity
@DiscriminatorValue(USERNAME_PASSWORD_VALUE)
@EntityListeners(ModelBaseListener.class)
public class UsernamePasswordSecret extends Secret {

  @Column(name = "secret_username")
  @JsonProperty("secret_username")
  @NotBlank
  private String username;

  @Column(name = "secret_password")
  @JsonProperty("secret_password")
  @NotBlank
  private String password;
}
