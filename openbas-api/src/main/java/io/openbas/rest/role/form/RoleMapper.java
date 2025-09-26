package io.openbas.rest.role.form;

import io.openbas.database.model.Capability;
import io.openbas.database.model.Role;
import jakarta.validation.constraints.NotNull;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RoleMapper {
  public RoleOutput toRoleOutput(@NotNull final Role role) {
    return RoleOutput.builder()
        .id(role.getId())
        .name(role.getName())
        .description(role.getDescription())
        .updatedAt(role.getUpdatedAt().toString())
        .createdAt(role.getCreatedAt().toString())
        .capabilities(
            role.getCapabilities().stream().map(Capability::name).collect(Collectors.toSet()))
        .build();
  }
}
