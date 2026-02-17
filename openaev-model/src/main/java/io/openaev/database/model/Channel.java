package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
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
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "channels")
@EntityListeners(ModelBaseListener.class)
public class Channel implements Base {

  @Id
  @Column(name = "channel_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("channel_id")
  @NotBlank
  private String id;

  @Column(name = "channel_created_at")
  @JsonProperty("channel_created_at")
  @NotNull
  private Instant createdAt = now();

  @Column(name = "channel_updated_at")
  @JsonProperty("channel_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @Column(name = "channel_type")
  @JsonProperty("channel_type")
  private String type;

  @Column(name = "channel_name")
  @JsonProperty("channel_name")
  private String name;

  @Column(name = "channel_description")
  @JsonProperty("channel_description")
  private String description;

  @Column(name = "channel_mode")
  @JsonProperty("channel_mode")
  private String mode;

  @Column(name = "channel_primary_color_dark")
  @JsonProperty("channel_primary_color_dark")
  private String primaryColorDark;

  @Column(name = "channel_primary_color_light")
  @JsonProperty("channel_primary_color_light")
  private String primaryColorLight;

  @Column(name = "channel_secondary_color_dark")
  @JsonProperty("channel_secondary_color_dark")
  private String secondaryColorDark;

  @Column(name = "channel_secondary_color_light")
  @JsonProperty("channel_secondary_color_light")
  private String secondaryColorLight;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "channel_logo_dark")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("channel_logo_dark")
  @Schema(type = "string")
  private Document logoDark;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "channel_logo_light")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("channel_logo_light")
  @Schema(type = "string")
  private Document logoLight;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.CHANNEL;

  // The following field is not accessed by our code but is necessary for the ORM mapping
  // (findDistinctByArticlesExerciseId, findDistinctByArticlesScenarioId...).
  @OneToMany(mappedBy = "channel", fetch = FetchType.LAZY)
  @JsonIgnore
  private List<Article> articles;

  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
  }

  public List<Document> getLogos() {
    List<Document> logos = new ArrayList<>();
    if (logoLight != null) {
      logos.add(logoLight);
    }
    if (logoDark != null) {
      logos.add(logoDark);
    }
    return logos;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
