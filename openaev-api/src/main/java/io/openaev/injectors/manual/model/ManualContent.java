package io.openaev.injectors.manual.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.model.inject.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManualContent {
  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();
}
