package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.jsonapi.InnerRelationship;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "custom_dashboards_parameters")
@EntityListeners(ModelBaseListener.class)
@InnerRelationship
public class CustomDashboardParameters implements Base {

  public enum CustomDashboardParameterType {
    simulation("simulation", true, false),
    timeRange("timeRange", false, true),
    startDate("startDate", false, true),
    endDate("endDate", false, true),
    scenario("scenario", true, false);

    public final String name;
    public final boolean isInstance;
    public final boolean uniq;

    CustomDashboardParameterType(String name, boolean isInstance, boolean uniq) {
      this.name = name;
      this.isInstance = isInstance;
      this.uniq = uniq;
    }
  }

  @Id
  @Column(name = "custom_dashboards_parameter_id", updatable = false, nullable = false)
  @JsonProperty("custom_dashboards_parameter_id")
  @NotBlank
  private String id;

  /**
   * Generates a UUID **only if the ID is not already set**, just before persisting.
   *
   * <p>This allows: - Keeping provided IDs (e.g. from import/export). - Auto-generating IDs for new
   * entities (e.g. via UI or tests).
   *
   * <p>Using `@PrePersist` avoids issues with `@GeneratedValue`, which always overrides or fails
   * when an ID is already present on persist.
   */
  @PrePersist
  protected void onCreate() {
    if (Objects.isNull(this.id)) {
      this.id = UUID.randomUUID().toString();
    }
  }

  @Column(name = "custom_dashboards_parameter_name", nullable = false)
  @NotNull
  @JsonProperty("custom_dashboards_parameter_name")
  private String name;

  @Column(name = "custom_dashboards_parameter_type", nullable = false)
  @NotNull
  @Enumerated(EnumType.STRING)
  @JsonProperty("custom_dashboards_parameter_type")
  private CustomDashboardParameterType type;

  // -- RELATION --

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "custom_dashboard_id", nullable = false)
  @JsonIgnore
  private CustomDashboard customDashboard;
}
