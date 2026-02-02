package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "injects_expectations_traces")
@EntityListeners(ModelBaseListener.class)
public class InjectExpectationTrace implements Base {

  @Id
  @NotBlank
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "inject_expectation_trace_id")
  @JsonProperty("inject_expectation_trace_id")
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inject_expectation_trace_expectation")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_trace_expectation")
  @Schema(type = "string")
  private InjectExpectation injectExpectation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inject_expectation_trace_source_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_trace_source_id")
  @Schema(type = "string")
  private SecurityPlatform securityPlatform;

  @Column(name = "inject_expectation_trace_alert_name")
  @JsonProperty("inject_expectation_trace_alert_name")
  private String alertName;

  @Column(name = "inject_expectation_trace_alert_link")
  @JsonProperty("inject_expectation_trace_alert_link")
  private String alertLink;

  @JsonProperty("inject_expectation_trace_date")
  @Column(name = "inject_expectation_trace_date")
  private Instant alertDate;

  @Column(name = "inject_expectation_trace_created_at")
  @JsonProperty("inject_expectation_trace_created_at")
  @NotNull
  private Instant createdAt = now();

  @Column(name = "inject_expectation_trace_updated_at")
  @JsonProperty("inject_expectation_trace_updated_at")
  @NotNull
  private Instant updatedAt = now();
}
