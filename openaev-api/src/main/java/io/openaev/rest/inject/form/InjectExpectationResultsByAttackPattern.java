package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.model.AttackPattern;
import io.openaev.helper.MonoIdDeserializer;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InjectExpectationResultsByAttackPattern {

  @JsonProperty("inject_expectation_results")
  private List<InjectExpectationResultsByType> results;

  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("inject_attack_pattern")
  @Schema(type = "string")
  private AttackPattern attackPattern;

  @Builder
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InjectExpectationResultsByType {
    @JsonProperty("inject_id")
    private String injectId;

    @JsonProperty("inject_title")
    private String injectTitle;

    @JsonProperty("results")
    private List<ExpectationResultsByType> results;
  }
}
