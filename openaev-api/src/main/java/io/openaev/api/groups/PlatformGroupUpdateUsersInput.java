package io.openaev.api.groups;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PlatformGroupUpdateUsersInput(
    @JsonProperty("platform_group_users") List<String> userIds) {}
