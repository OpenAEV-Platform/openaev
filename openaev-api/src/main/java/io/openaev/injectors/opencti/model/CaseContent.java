package io.openaev.injectors.opencti.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.injectors.common.model.BaseInjectContent;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CaseContent extends BaseInjectContent {

  @JsonProperty("name")
  private String name;

  @JsonProperty("description")
  private String description;

  public CaseContent() {
    // For mapper
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CaseContent that = (CaseContent) o;
    return Objects.equals(name, that.name) && Objects.equals(description, that.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, description);
  }
}
