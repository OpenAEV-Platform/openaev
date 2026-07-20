package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.SQLInsert;
import org.hibernate.jdbc.Expectation;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"injectExpectation", "type", "value"})
@Entity
@IdClass(InjectExpectationSignature.InjectExpectationSignatureId.class)
@Table(name = "injects_expectations_signatures")
@SQLInsert(
    sql =
        """
        INSERT INTO injects_expectations_signatures \
        (inject_expectation_signature_created_at, \
        inject_expectation_signature_inject_expectation_id, \
        inject_expectation_signature_type, \
        inject_expectation_signature_value) \
        VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING\
        """,
    verify = Expectation.None.class)
public class InjectExpectationSignature {

  @Id
  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inject_expectation_signature_inject_expectation_id")
  private BaseInjectExpectation injectExpectation;

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
  private Instant createdAt = now();

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class InjectExpectationSignatureId implements Serializable {
    private String injectExpectation;
    private String type;
    private String value;
  }
}
