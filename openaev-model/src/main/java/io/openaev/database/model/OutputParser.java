package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class OutputParser implements Base {

  @Id
  @Column(name = "output_parser_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("output_parser_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "output_parser_payload_id", nullable = false)
  @JsonIgnore
  private Payload payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "output_parser_mode")
  @JsonProperty("output_parser_mode")
  @NotNull
  private ParserMode mode;

  @Enumerated(EnumType.STRING)
  @Column(name = "output_parser_type")
  @JsonProperty("output_parser_type")
  @NotNull
  private ParserType type;

  @OneToMany(
      mappedBy = "outputParser",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("output_parser_contract_output_elements")
  @NotNull
  private Set<ContractOutputElement> contractOutputElements = new HashSet<>();

  // -- AUDIT --

  @CreationTimestamp
  @Column(name = "output_parser_created_at")
  @JsonProperty("output_parser_created_at")
  @NotNull
  private Instant createdAt = now();

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
}
