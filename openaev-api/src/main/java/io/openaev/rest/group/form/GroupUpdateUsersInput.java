package io.openaev.rest.group.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GroupUpdateUsersInput {

  @JsonProperty("group_users")
  private List<String> userIds;
}
