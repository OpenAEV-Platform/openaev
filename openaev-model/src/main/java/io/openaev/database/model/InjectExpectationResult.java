package io.openaev.database.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InjectExpectationResult {

  private String sourceId;

  private String sourceType;

  private String sourceName;

  private String sourcePlatform;

  private String date;

  private Double score;

  @NotBlank private String result;

  private Map<String, String> metadata;
}
