package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.engine.api.WidgetConfiguration;
import io.openaev.engine.api.WidgetType;
import io.openaev.jsonapi.InnerRelationship;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "widgets")
@EntityListeners(ModelBaseListener.class)
@InnerRelationship
public class Widget implements Base {

  @Id
  @Column(name = "widget_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("widget_id")
  @NotBlank
  private String id;

  @Column(name = "widget_type", nullable = false)
  @Enumerated(EnumType.STRING)
  @JsonProperty("widget_type")
  @NotNull
  protected WidgetType type;

  @Type(JsonType.class)
  @Column(name = "widget_config", columnDefinition = "JSONB")
  @JsonProperty("widget_config")
  @NotNull
  private WidgetConfiguration widgetConfiguration;

  @Type(JsonType.class)
  @Column(name = "widget_layout", columnDefinition = "JSONB")
  @JsonProperty("widget_layout")
  @NotNull
  private WidgetLayout layout;

  // -- RELATIONS --

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "widget_custom_dashboard")
  @JsonIgnore
  private CustomDashboard customDashboard;

  // -- AUDIT --

  @CreationTimestamp
  @Column(name = "widget_created_at", updatable = false, nullable = false)
  @JsonProperty("widget_created_at")
  @NotNull
  private Instant creationDate = now();

  @UpdateTimestamp
  @Column(name = "widget_updated_at", nullable = false)
  @JsonProperty("widget_updated_at")
  @NotNull
  private Instant updateDate = now();
}
