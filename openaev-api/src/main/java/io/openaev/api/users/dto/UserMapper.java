package io.openaev.api.users.dto;

import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import java.util.Set;
import java.util.stream.Collectors;

public class UserMapper {

    private UserMapper() {
    }

    /**
     * Maps a User entity to a UserOutput DTO.
     *
     * <p>Requires the entity to have been loaded with the "Player.tags-organization" EntityGraph
     * so that organization and tags are eagerly fetched. If they are not initialized, a
     * LazyInitializationException will be thrown — this is intentional to catch missing EntityGraph
     * configurations early.
     */
    public static UserOutput toOutput(User user) {
        Organization org = user.getOrganization();
        Set<String> tagIds = user.getTags().stream().map(Tag::getId).collect(Collectors.toSet());

        return new UserOutput(
            user.getId(),
            user.getEmail(),
            user.getFirstname(),
            user.getLastname(),
            user.getPhone(),
            user.getPhone2(),
            org != null ? org.getId() : null,
            org != null ? org.getName() : null,
            tagIds,
            user.getPassword() != null && !user.getPassword().isBlank(),
            user.getPgpKey() != null && !user.getPgpKey().isBlank());
    }
}
