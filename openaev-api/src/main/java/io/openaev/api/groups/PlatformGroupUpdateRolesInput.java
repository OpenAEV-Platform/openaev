package io.openaev.api.groups;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PlatformGroupUpdateRolesInput(
    @JsonProperty("platform_group_platform_roles") List<String> platformRoleIds) {}
