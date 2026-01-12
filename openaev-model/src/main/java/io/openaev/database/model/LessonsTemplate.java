package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "lessons_templates")
@EntityListeners(ModelBaseListener.class)
@Data
public class LessonsTemplate implements Base {

  @Id
  @Column(name = "lessons_template_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("lessonstemplate_id")
  @NotBlank
  private String id;

  @Column(name = "lessons_template_created_at")
  @JsonProperty("lessons_template_created_at")
  @NotNull
  private Instant created = now();

  @Column(name = "lessons_template_updated_at")
  @JsonProperty("lessons_template_updated_at")
  @NotNull
  private Instant updated = now();

  @Queryable(sortable = true)
  @Column(name = "lessons_template_name")
  @JsonProperty("lessons_template_name")
  @NotBlank
  private String name;

  @Queryable(sortable = true)
  @Column(name = "lessons_template_description")
  @JsonProperty("lessons_template_description")
  private String description;

  @OneToMany(mappedBy = "template", fetch = FetchType.LAZY)
  @JsonIgnore
  private List<LessonsTemplateCategory> categories = new ArrayList<>();

  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
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
