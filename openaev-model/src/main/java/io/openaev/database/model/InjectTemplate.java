package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.converter.ContentConverter;
import io.openaev.helper.*;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static io.openaev.database.model.CollectExecutionStatus.COLLECTING;
import static io.openaev.database.specification.InjectSpecification.VALID_TESTABLE_TYPES;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;

@Setter
@Entity
@Table(name = "injects_templates")
@EntityListeners(ModelBaseListener.class)
@Slf4j
@Grantable(Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING)
public class InjectTemplate implements GrantableBase, Injection {

  public static final String ID_COLUMN_NAME = "inject_template_id";

  @Getter
  @Id
  @Column(name = ID_COLUMN_NAME)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("inject_template_id")
  @NotBlank
  private String id;

  @Getter
  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "inject_template_title")
  @JsonProperty("inject_template_title")
  @NotBlank
  private String title;

  @Getter
  @Column(name = "inject_template_description")
  @JsonProperty("inject_template_description")
  private String description;

  @Getter
  @Column(name = "inject_template_content")
  @Convert(converter = ContentConverter.class)
  @JsonProperty("inject_template_content")
  private ObjectNode content;

  @Getter
  @Column(name = "inject_template_created_at")
  @JsonProperty("inject_template_created_at")
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Getter
  @Column(name = "inject_template_updated_at")
  @Queryable(filterable = true, sortable = true)
  @JsonProperty("inject_template_updated_at")
  @NotNull
  @UpdateTimestamp
  private Instant updatedAt = now();

  @Getter
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "inject_template_exercise")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("inject_template_exercise")
  @Schema(type = "string")
  private Exercise simulation;

  @Getter
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "inject_template_scenario")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("inject_template_scenario")
  @Schema(type = "string")
  private Scenario scenario;

  @Getter
  @OneToMany(
      mappedBy = "compositeId.injectChildren",
      fetch = FetchType.EAGER,
      orphanRemoval = true,
      cascade = CascadeType.ALL)
  @JsonProperty("inject_template_depends_on")
  private List<Edge> dependsOn = new ArrayList<>();

  // CascadeType.ALL is required here because inject status are embedded
  @JsonProperty("inject_template_status")
  @Queryable(filterable = true, sortable = true)
  private String status;

  @Getter @Setter @Transient private boolean isListened = true;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.INJECT;

  @Getter
  @OneToMany
  @JoinColumn(
      name = "grant_resource",
      referencedColumnName = "inject_template_id",
      insertable = false,
      updatable = false)
  @SQLRestriction(
      "grant_resource_type = 'ATOMIC_TESTING'") // Must be present in Grant.GRANT_RESOURCE_TYPE
  @JsonIgnore
  private List<Grant> grants = new ArrayList<>();

  @JsonIgnore
  @Override
  public boolean isUserHasAccess(User user) {
    return this.getExercise().isUserHasAccess(user);
  }

  @JsonIgnore
  public void clean() {
    this.status = null;
  }
}
