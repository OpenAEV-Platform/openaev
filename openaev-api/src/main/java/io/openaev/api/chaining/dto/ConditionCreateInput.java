package io.openaev.api.chaining.dto;

import io.openaev.database.model.CONDITION_TYPE;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConditionCreateInput {
  private String temporaryId;
  private String key;
  private String value;
  private CONDITION_TYPE type;
  private String temporaryIdConditionParent;
  private String stepFrom;
}
