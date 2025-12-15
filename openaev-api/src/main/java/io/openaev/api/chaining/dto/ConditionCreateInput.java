package io.openaev.api.chaining.dto;

import io.openaev.database.model.CONDITION_TYPE;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ConditionCreateInput {
  private String temporaryId;
  private String key;
  private String value;
  private CONDITION_TYPE type;
  private String temporaryIdConditionParent;
  private String stepFrom;
}
