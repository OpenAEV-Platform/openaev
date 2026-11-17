package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.hash.Hashed;
import io.openaev.database.model.hash.NotHashed;
import io.openaev.jsonapi.InnerRelationship;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "regex_groups")
@InnerRelationship
public class RegexGroup implements Base {

  @NotHashed
  @Id
  @Column(name = "regex_group_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("regex_group_id")
  @NotBlank
  private String id;

  @NotHashed
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "regex_group_contract_output_element_id", nullable = false)
  @JsonIgnore
  private ContractOutputElement contractOutputElement;

  @Hashed
  @Column(name = "regex_group_field")
  @JsonProperty("regex_group_field")
  @NotBlank
  private String field;

  @Hashed
  @Column(name = "regex_group_index_values")
  @JsonProperty("regex_group_index_values")
  @NotBlank
  private String indexValues;

  // -- AUDIT --

  @NotHashed
  @CreationTimestamp
  @Column(name = "regex_group_created_at")
  @JsonProperty("regex_group_created_at")
  @NotNull
  private Instant createdAt = now();

  @NotHashed
  @UpdateTimestamp
  @Column(name = "regex_group_updated_at")
  @JsonProperty("regex_group_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
