package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.helper.MultiIdSetDeserializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Table(name = "contract_output_elements")
public class ContractOutputElement implements Base {

  @Id
  @Column(name = "contract_output_element_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("contract_output_element_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contract_output_element_output_parser_id", nullable = false)
  @JsonIgnore
  private OutputParser outputParser;

  @Column(name = "contract_output_element_is_finding")
  @JsonProperty("contract_output_element_is_finding")
  @NotNull
  private boolean isFinding;

  @Column(name = "contract_output_element_rule")
  @JsonProperty("contract_output_element_rule")
  @NotBlank
  private String rule;

  @Column(name = "contract_output_element_name")
  @JsonProperty("contract_output_element_name")
  @NotBlank
  private String name;

  @OneToMany(
      mappedBy = "contractOutputElement",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("contract_output_element_regex_groups")
  @NotNull
  private Set<RegexGroup> regexGroups = new HashSet<>();

  @Column(name = "contract_output_element_key")
  @JsonProperty("contract_output_element_key")
  @NotBlank
  private String key;

  @Enumerated(EnumType.STRING)
  @Column(name = "contract_output_element_type")
  @JsonProperty("contract_output_element_type")
  @NotNull
  private ContractOutputType type;

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "contract_output_elements_tags",
      joinColumns = @JoinColumn(name = "contract_output_element_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetDeserializer.class)
  @JsonProperty("contract_output_element_tags")
  private Set<Tag> tags = new HashSet<>();

  // -- AUDIT --

  @CreationTimestamp
  @Column(name = "contract_output_element_created_at")
  @JsonProperty("contract_output_element_created_at")
  @NotNull
  private Instant createdAt = now();

  @UpdateTimestamp
  @Column(name = "contract_output_element_updated_at")
  @JsonProperty("contract_output_element_updated_at")
  @NotNull
  private Instant updatedAt = now();

  public void setRegexGroups(final Set<RegexGroup> regexGroups) {
    this.regexGroups.clear();
    regexGroups.forEach(this::addRegexGroup);
  }

  public void addRegexGroup(final RegexGroup regexGroup) {
    if (regexGroup != null) {
      regexGroup.setContractOutputElement(this);
      this.regexGroups.add(regexGroup);
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
