package io.openaev.injectors.caldera.model;

import java.time.Instant;
import lombok.Data;

@Data
public class ResultStatus {

  private String paw;
  private boolean complete;
  private boolean fail;
  private Instant finish;
  private String content;
}
