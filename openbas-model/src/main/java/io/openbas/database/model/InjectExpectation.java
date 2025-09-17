package io.openbas.database.model;

import static io.openbas.helper.InjectExpectationHelper.computeStatus;
import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openbas.annotation.Queryable;
import io.openbas.database.audit.ModelBaseListener;
import io.openbas.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(name = "injects_expectations")
@EntityListeners(ModelBaseListener.class)
public class InjectExpectation implements Base, Cloneable {

  @Override
  public InjectExpectation clone() {
    try {
      InjectExpectation clone = (InjectExpectation) super.clone();
      // TODO: copy mutable state here, so the clone can't change the internals of the original
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }

  public enum EXPECTATION_TYPE {
    TEXT,
    DOCUMENT,
    ARTICLE,
    CHALLENGE,
    MANUAL,
    PREVENTION,
    DETECTION,
    VULNERABILITY
  }

  public enum EXPECTATION_STATUS {
    FAILED,
    PENDING,
    PARTIAL,
    UNKNOWN,
    SUCCESS
  }

  @Queryable(filterable = true, label = "inject expectation type")
  @Setter
  @Column(name = "inject_expectation_type")
  @JsonProperty("inject_expectation_type")
  @Enumerated(EnumType.STRING)
  @NotNull
  private EXPECTATION_TYPE type;

  // region basic
  @Id
  @NotBlank
  @Setter
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "inject_expectation_id")
  @JsonProperty("inject_expectation_id")
  private String id;

  @Setter
  @Column(name = "inject_expectation_name")
  @JsonProperty("inject_expectation_name")
  private String name;

  @Setter
  @Column(name = "inject_expectation_description")
  @JsonProperty("inject_expectation_description")
  private String description;

  @Setter
  @Type(JsonType.class)
  @Column(name = "inject_expectation_signatures")
  @JsonProperty("inject_expectation_signatures")
  private List<InjectExpectationSignature> signatures = new ArrayList<>();

  @Setter
  @Type(JsonType.class)
  @Column(name = "inject_expectation_results")
  @JsonProperty("inject_expectation_results")
  private List<InjectExpectationResult> results = new ArrayList<>();

  @Setter
  @Column(name = "inject_expectation_score")
  @JsonProperty("inject_expectation_score")
  private Double score;

  @JsonProperty("inject_expectation_status")
  public EXPECTATION_STATUS getResponse() {
    return computeStatus(this.getScore(), this.getExpectedScore());
  }

  @Setter
  @Column(name = "inject_expectation_expected_score")
  @JsonProperty("inject_expectation_expected_score")
  @NotNull
  private Double expectedScore;

  /** Expiration time in seconds */
  @Setter
  @Column(name = "inject_expiration_time")
  @JsonProperty("inject_expiration_time")
  @NotNull
  private Long expirationTime;

  @Queryable(filterable = true, label = "created at")
  @Setter
  @Column(name = "inject_expectation_created_at")
  @JsonProperty("inject_expectation_created_at")
  @CreationTimestamp
  private Instant createdAt = now();

  @Queryable(filterable = true, label = "updated at")
  @Setter
  @Column(name = "inject_expectation_updated_at")
  @JsonProperty("inject_expectation_updated_at")
  @UpdateTimestamp
  private Instant updatedAt = now();

  @Setter
  @Column(name = "inject_expectation_group")
  @JsonProperty("inject_expectation_group")
  private boolean expectationGroup;

  // endregion

  // region contextual relations
  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "exercise_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("inject_expectation_exercise")
  @Schema(type = "string")
  private Exercise exercise;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inject_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("inject_expectation_inject")
  @Schema(type = "string")
  private Inject inject;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("inject_expectation_user")
  @Schema(type = "string")
  private User user;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "team_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("inject_expectation_team")
  @Schema(type = "string")
  private Team team;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "agent_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("inject_expectation_agent")
  @Schema(type = "string")
  private Agent agent;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "asset_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("inject_expectation_asset")
  @Schema(type = "string")
  private Asset asset;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "asset_group_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("inject_expectation_asset_group")
  @Schema(type = "string")
  private AssetGroup assetGroup;

  // endregion

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "article_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("inject_expectation_article")
  @Schema(type = "string")
  private Article article;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "challenge_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("inject_expectation_challenge")
  @Schema(type = "string")
  private Challenge challenge;

  @OneToMany(
      mappedBy = "injectExpectation",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @JsonProperty("inject_expectation_traces")
  private List<InjectExpectationTrace> traces = new ArrayList<>();

  public void setArticle(Article article) {
    this.type = EXPECTATION_TYPE.ARTICLE;
    this.article = article;
  }

  public void setChallenge(Challenge challenge) {
    this.type = EXPECTATION_TYPE.CHALLENGE;
    this.challenge = challenge;
  }

  public void setManual(
      @NotNull final Agent agent,
      @NotNull final Asset asset,
      @NotNull final AssetGroup assetGroup) {
    this.type = EXPECTATION_TYPE.MANUAL;
    this.agent = agent;
    this.asset = asset;
    this.assetGroup = assetGroup;
  }

  public void setPrevention(
      @NotNull final Agent agent,
      @NotNull final Asset asset,
      @NotNull final AssetGroup assetGroup) {
    this.type = EXPECTATION_TYPE.PREVENTION;
    this.agent = agent;
    this.asset = asset;
    this.assetGroup = assetGroup;
  }

  public void setDetection(
      @NotNull final Agent agent,
      @NotNull final Asset asset,
      @NotNull final AssetGroup assetGroup) {
    this.type = EXPECTATION_TYPE.DETECTION;
    this.agent = agent;
    this.asset = asset;
    this.assetGroup = assetGroup;
  }

  public void setVulnerability(
      @NotNull final Agent agent,
      @NotNull final Asset asset,
      @NotNull final AssetGroup assetGroup) {
    this.type = EXPECTATION_TYPE.VULNERABILITY;
    this.agent = agent;
    this.asset = asset;
    this.assetGroup = assetGroup;
  }

  public boolean isUserHasAccess(User user) {
    return getExercise().isUserHasAccess(user);
  }

  @JsonProperty("target_id")
  public String getTargetId() {
    if (user != null) {
      return user.getId();
    } else if (team != null) {
      return team.getId();
    } else if (agent != null) {
      return agent.getId();
    } else if (asset != null) {
      return asset.getId();
    } else if (assetGroup != null) {
      return assetGroup.getId();
    } else {
      throw new RuntimeException();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
