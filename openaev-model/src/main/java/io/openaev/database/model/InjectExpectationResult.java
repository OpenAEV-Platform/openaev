package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "inject_expectation_results")
@EntityListeners(ModelBaseListener.class)
public class InjectExpectationResult implements Base, Cloneable {

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
  @JsonProperty("sourceId")
  private String sourceId;

  @Setter
  @Column(name = "inject_expectation_result_source_type")
  @JsonProperty("sourceType")
  private String sourceType;

  @Setter
  @Column(name = "inject_expectation_result_source_name")
  @JsonProperty("sourceName")
  private String sourceName;

  @Setter
  @Column(name = "inject_expectation_result_source_asset_id")
  @JsonProperty("sourceAssetId")
  private String sourceAssetId;

  @Setter
  @Column(name = "inject_expectation_result_source_platform")
  @JsonProperty("sourcePlatform")
  private String sourcePlatform;

  @Setter
  @Column(name = "inject_expectation_result_date")
  @JsonProperty("date")
  private Instant date;

  @Column(name = "inject_expectation_result_score")
  @JsonProperty("score")
  private Double score;

  public void setScore(Double newScore) {
    if (this.injectExpectation != null) {
      this.injectExpectation.setUpdatedAt(Instant.now());
    }
    this.score = newScore;
  }

  @Column(name = "inject_expectation_result_result")
  @JsonProperty("result")
  private String result;

  public void setResult(String newResult) {
    if (this.injectExpectation != null) {
      this.injectExpectation.setUpdatedAt(Instant.now());
    }
    this.result = newResult;
  }

  @Type(JsonType.class)
  @Column(name = "inject_expectation_result_metadata")
  @JsonProperty("metadata")
  private Map<String, String> metadata;

  public void setMetadata(Map<String, String> newMetadata) {
    if (this.injectExpectation != null) {
      this.injectExpectation.setUpdatedAt(Instant.now());
    }
    this.metadata = newMetadata;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inject_expectation_result_inject_expectation_id")
  @Setter
  @JsonIgnore
  private BaseInjectExpectation injectExpectation;

  @Setter
  @Column(name = "inject_expectation_result_created_at")
  @JsonProperty("inject_expectation_result_created_at")
  @CreationTimestamp
  private Instant createdAt = now();

  @Setter
  @Column(name = "inject_expectation_result_updated_at")
  @JsonProperty("inject_expectation_result_updated_at")
  @UpdateTimestamp
  private Instant updatedAt = now();

  /**
   * Compares two results by their values rather than by hash or ID
   *
   * @param other
   * @return
   */
  public boolean valueEquals(InjectExpectationResult other) {
    return Optional.ofNullable(sourceId)
            .map(sourceId -> sourceId.equals(other.getSourceId()))
            .orElse(other.getSourceId() == null)
        && Optional.ofNullable(sourceType)
            .map(sourceType -> sourceType.equals(other.getSourceType()))
            .orElse(other.getSourceType() == null)
        && Optional.ofNullable(sourceName)
            .map(sourceName -> sourceName.equals(other.getSourceName()))
            .orElse(other.getSourceName() == null)
        && Optional.ofNullable(sourcePlatform)
            .map(sourcePlatform -> sourcePlatform.equals(other.getSourcePlatform()))
            .orElse(other.getSourcePlatform() == null)
        && Optional.ofNullable(sourceAssetId)
            .map(sourceAssetId -> sourceAssetId.equals(other.getSourceAssetId()))
            .orElse(other.getSourceAssetId() == null)
        && Optional.ofNullable(date)
            .map(date -> date.equals(other.getDate()))
            .orElse(other.getDate() == null)
        && Optional.ofNullable(score)
            .map(score -> score.equals(other.getScore()))
            .orElse(other.getScore() == null)
        && Optional.ofNullable(result)
            .map(result -> result.equals(other.getResult()))
            .orElse(other.getResult() == null)
        && Optional.ofNullable(metadata)
            .map(metadata -> metadata.equals(other.getMetadata()))
            .orElse(other.getMetadata() == null);
  }

  @Override
  public InjectExpectationResult clone() {
    try {
      InjectExpectationResult clone = (InjectExpectationResult) super.clone();
      clone.setId(
          null); // computed at persist time anyway, but let's avoid mistaking the clone for the
      // original
      clone.setInjectExpectation(null); // cloned results are orphan by default
      clone.setMetadata(this.metadata == null ? null : new HashMap<>(this.metadata));
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError(e);
    }
  }
}
