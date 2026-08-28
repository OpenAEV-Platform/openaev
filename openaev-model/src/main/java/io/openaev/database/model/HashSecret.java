package io.openaev.database.model;

import static io.openaev.database.model.Secret.SECRET_TYPE.HASH_VALUE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Entity
@DiscriminatorValue(HASH_VALUE)
@EntityListeners(ModelBaseListener.class)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class HashSecret extends Secret {

  public enum HASH_ALGORITHM {
    SHA,
    NTLM
  }

  @Column(name = "secret_hash_algorithm")
  @JsonProperty("secret_hash_algorithm")
  @Enumerated(EnumType.STRING)
  @NotNull
  private HASH_ALGORITHM hashAlgorithm;

  @Column(name = "secret_hash")
  @JsonIgnore
  @NotBlank
  private String hash;
}
