package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChainingTimeOut {

  @JsonProperty("chaining_enable_time_out")
  private boolean isTimeOut;

  @JsonProperty("chaining_time_out_seconds")
  @Min(0)
  @Max(86400)
  private Integer timeOutSeconds;
}
