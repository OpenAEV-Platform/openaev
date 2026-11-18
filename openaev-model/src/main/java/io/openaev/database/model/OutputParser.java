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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "output_parsers")
@InnerRelationship
public class OutputParser implements Base {

  @NotHashed
  @Id
  @Column(name = "output_parser_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("output_parser_id")
  @NotBlank
  private String id;

  @NotHashed
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "output_parser_payload_id", nullable = false)
  @JsonIgnore
  private Payload payload;

  @Hashed
  @Enumerated(EnumType.STRING)
  @Column(name = "output_parser_mode")
  @JsonProperty("output_parser_mode")
  @NotNull
  private ParserMode mode;

  @Hashed
  @Enumerated(EnumType.STRING)
  @Column(name = "output_parser_type")
  @JsonProperty("output_parser_type")
  @NotNull
  private ParserType type;

  @Hashed(order = Hashed.Order.OBJECTS)
  @OneToMany(
      mappedBy = "outputParser",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("output_parser_contract_output_elements")
  @NotNull
  private Set<ContractOutputElement> contractOutputElements = new HashSet<>();

  // -- AUDIT --

  @NotHashed
  @CreationTimestamp
  @Column(name = "output_parser_created_at")
  @JsonProperty("output_parser_created_at")
  @NotNull
  private Instant createdAt = now();

  @NotHashed
  @UpdateTimestamp
  @Column(name = "output_parser_updated_at")
  @JsonProperty("output_parser_updated_at")
  @NotNull
  private Instant updatedAt = now();

  public void setContractOutputElements(final Set<ContractOutputElement> contractOutputElements) {
    this.contractOutputElements.clear();
    contractOutputElements.forEach(this::addContractOutputElement);
  }

  public void addContractOutputElement(final ContractOutputElement contractOutputElement) {
    if (contractOutputElement != null) {
      contractOutputElement.setOutputParser(this);
      this.contractOutputElements.add(contractOutputElement);
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "OutputParser{" + "id='" + id + '\'' + ", type=" + type + ", mode=" + mode + '}';
  }
}
