package io.openaev.injectors.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.model.inject.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaseInjectContent {

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();
}
