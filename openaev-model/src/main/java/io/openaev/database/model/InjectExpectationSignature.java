package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

import static java.time.Instant.now;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"type", "value"})
@Entity
@IdClass(InjectExpectationSignature.InjectExpectationSignatureId.class)
@Table(name = "injects_expectations_signatures")
public class InjectExpectationSignature {

  @Id
  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inject_expectation_signature_inject_expectation_id")
  private InjectExpectation injectExpectation;

  @Id
  @NotBlank
  @Column(name = "inject_expectation_signature_type")
  @JsonProperty("type")
  private String type;

  @Id
  @NotBlank
  @Column(name = "inject_expectation_signature_value")
  @JsonProperty("value")
  private String value;

  @NotNull
  @JsonIgnore
  @Column(name = "inject_expectation_signature_created_at", updatable = false)
  @JsonProperty("inject_expectation_signature_created_at")
  private Instant createdAt = now();

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InjectExpectationSignatureId implements Serializable {
    private String injectExpectation;
    private String type;
    private String value;
  }
}
