package io.openaev.injectors.opencti.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.model.inject.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CaseContent {

  @JsonProperty("name")
  private String name;

  @JsonProperty("description")
  private String description;

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();

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
