package io.openaev.rest.attack_pattern.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Getter;

// TODO ADD DOC
@Getter
public class AnalysisResultFromTTPExtractionAIWebserviceOutput {
  @JsonProperty("text")
  String text;

  @JsonProperty("predictions")
  Map<String, Number> predictions;
}
