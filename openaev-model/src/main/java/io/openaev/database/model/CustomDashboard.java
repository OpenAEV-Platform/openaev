package io.openaev.database.model;

import static jakarta.persistence.FetchType.LAZY;
import static java.time.Instant.now;
import static java.util.function.Function.identity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType;
import io.openaev.helper.MultiModelSerializer;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "custom_dashboards")
@EntityListeners(ModelBaseListener.class)
public class CustomDashboard implements Base {

  @Id
  @Column(name = "custom_dashboard_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("custom_dashboard_id")
  @NotBlank
  private String id;

  @Column(name = "custom_dashboard_name", nullable = false)
  @JsonProperty("custom_dashboard_name")
  @NotBlank
  @Queryable(filterable = true, searchable = true, sortable = true)
  private String name;

  @Column(name = "custom_dashboard_description")
  @JsonProperty("custom_dashboard_description")
  private String description;

  @OneToMany(
      mappedBy = "customDashboard",
      fetch = LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("custom_dashboard_widgets")
  @JsonSerialize(using = MultiModelSerializer.class)
  private List<Widget> widgets = new ArrayList<>();

  @OneToMany(
      mappedBy = "customDashboard",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @JsonProperty("custom_dashboard_parameters")
  @OrderBy("id ASC")
  private List<CustomDashboardParameters> parameters = new ArrayList<>();

  public CustomDashboard addParameter(
      @NotBlank final String name, @NotBlank final CustomDashboardParameterType type) {
    if (this.getParameters().stream().anyMatch(p -> p.getType().equals(type) && type.uniq)) {
      return this;
    }
    CustomDashboardParameters customDashboardEndDateParameter = new CustomDashboardParameters();
    customDashboardEndDateParameter.setName(name);
    customDashboardEndDateParameter.setType(type);
    customDashboardEndDateParameter.setCustomDashboard(this);
    this.parameters.add(customDashboardEndDateParameter);
    return this;
  }

  // -- AUDIT --

  @CreationTimestamp
  @Column(name = "custom_dashboard_created_at", updatable = false, nullable = false)
  @JsonProperty("custom_dashboard_created_at")
  @NotNull
  private Instant creationDate = now();

  @UpdateTimestamp
  @Column(name = "custom_dashboard_updated_at", nullable = false)
  @JsonProperty("custom_dashboard_updated_at")
  @NotNull
  private Instant updateDate = now();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.DASHBOARD;

  // -- UTILS --

  public Map<String, CustomDashboardParameters> toParametersMap() {
    return this.getParameters().stream()
        .collect(Collectors.toMap(CustomDashboardParameters::getId, identity()));
  }
}
