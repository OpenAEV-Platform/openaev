package io.openaev.executors.caldera.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Link {

  private String id;
  private String paw;
  private int status;
  private String decide;
  private Ability ability;
  private String finish;
  private String command;
}
