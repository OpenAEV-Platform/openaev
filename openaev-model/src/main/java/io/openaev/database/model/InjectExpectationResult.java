package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "inject_expectation_results")
@EntityListeners(ModelBaseListener.class)
public class InjectExpectationResult implements Base {

  @Id
  @NotBlank
  @Setter
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "inject_expectation_result_id")
  @JsonProperty("inject_expectation_id")
  private String id;

  @Setter
  @Column(name = "inject_expectation_result_source_id")
  @JsonProperty("inject_expectation_result_source_id")
  private String sourceId;

  @Setter
  @Column(name = "inject_expectation_result_source_type")
  @JsonProperty("inject_expectation_result_source_type")
  private String sourceType;

  @Setter
  @Column(name = "inject_expectation_result_source_name")
  @JsonProperty("inject_expectation_result_source_name")
  private String sourceName;

  @Setter
  @Column(name = "inject_expectation_result_source_asset_id")
  @JsonProperty("inject_expectation_result_source_asset_id")
  private String sourceAssetId;

  @Setter
  @Column(name = "inject_expectation_result_source_platform")
  @JsonProperty("inject_expectation_result_source_platform")
  private String sourcePlatform;

  @Setter
  @Column(name = "inject_expectation_result_date")
  @JsonProperty("inject_expectation_result_date")
  private Instant date;

  @Setter
  @Column(name = "inject_expectation_result_score")
  @JsonProperty("inject_expectation_result_score")
  private Double score;

  @Setter
  @Column(name = "inject_expectation_result_result")
  @JsonProperty("inject_expectation_result_result")
  private String result;

  @Setter
  @Type(JsonType.class)
  @Column(name = "inject_expectation_result_metadata")
  @JsonProperty("inject_expectation_result_metadata")
  private Map<String, String> metadata;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inject_expectation_result_inject_expectation_id")
  @Setter
  @JsonIgnore
  private BaseInjectExpectation injectExpectation;
}
