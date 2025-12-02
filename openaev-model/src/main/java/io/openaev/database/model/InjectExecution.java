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
@Table(name = "injects_executions")
@EntityListeners(ModelBaseListener.class)
@Slf4j
@Grantable(Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING)
public class InjectExecution implements GrantableBase, Injection {

  public static final String ID_COLUMN_NAME = "inject_id";

  @Getter
  @Id
  @Column(name = ID_COLUMN_NAME)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("inject_id")
  @NotBlank
  private String id;

  @Getter
  @Column(name = "inject_created_at")
  @JsonProperty("inject_created_at")
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Getter
  @Column(name = "inject_updated_at")
  @Queryable(filterable = true, sortable = true)
  @JsonProperty("inject_updated_at")
  @NotNull
  @UpdateTimestamp
  private Instant updatedAt = now();

  @Getter
  @OneToMany(
      mappedBy = "compositeId.injectChildren",
      fetch = FetchType.EAGER,
      orphanRemoval = true,
      cascade = CascadeType.ALL)
  @JsonProperty("inject_depends_on")
  private List<InjectDependency> dependsOn = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setDependsOn(List<InjectDependency> dependsOn) {
    this.updatedAt = now();
    this.dependsOn = dependsOn;
  }

  @Getter @Setter @Transient private boolean isListened = true;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.INJECT;

  @Getter
  @OneToMany
  @JoinColumn(
      name = "grant_resource",
      referencedColumnName = "inject_id",
      insertable = false,
      updatable = false)
  @SQLRestriction(
      "grant_resource_type = 'ATOMIC_TESTING'") // Must be present in Grant.GRANT_RESOURCE_TYPE
  @JsonIgnore
  private List<Grant> grants = new ArrayList<>();

   @JsonIgnore
  public String getParentResourceId() {
    return this.getScenario() != null
        ? this.getScenario().getId()
        : this.getExercise() != null ? this.getExercise().getId() : this.getId();
  }

  @JsonIgnore
  public ResourceType getParentResourceType() {
    return this.getScenario() != null
        ? ResourceType.SCENARIO
        : this.getExercise() != null ? ResourceType.SIMULATION : ResourceType.ATOMIC_TESTING;
  }
}
