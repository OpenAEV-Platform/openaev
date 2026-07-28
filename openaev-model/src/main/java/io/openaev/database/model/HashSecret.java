package io.openaev.database.model;

import static io.openaev.database.model.Secret.SECRET_TYPE.HASH_VALUE;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@DiscriminatorValue(HASH_VALUE)
@EntityListeners(ModelBaseListener.class)
public class HashSecret extends Secret {

  @Column(name = "secret_hash_algorithm")
  @JsonProperty("secret_hash_algorithm")
  @NotBlank
  private String hashAlgorithm;

  @Column(name = "secret_hash")
  @JsonIgnore
  @NotBlank
  private String hash;
}
