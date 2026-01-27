package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class InjectDependencyId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @ManyToOne
  @JsonProperty("inject_parent_id")
  @JoinColumn(referencedColumnName = "inject_id", name = "inject_parent_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Inject injectParent;

  @ManyToOne
  @JsonProperty("inject_children_id")
  @JoinColumn(referencedColumnName = "inject_id", name = "inject_children_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Inject injectChildren;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InjectDependencyId that = (InjectDependencyId) o;
    return injectParent.equals(that.injectParent) && injectChildren.equals(that.injectChildren);
  }

  @Override
  public int hashCode() {
    return Objects.hash(injectParent, injectChildren);
  }
}
