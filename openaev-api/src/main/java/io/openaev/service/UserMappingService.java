package io.openaev.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.sso.GroupRoleMap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
@Service
public class UserMappingService {

  private final GroupRepository groupRepository;

  public void mapCurrentUserWithGroup(String property, User user, List<String> rolesFromToken) {

    for (GroupRoleMap mapping : safeParseMappings(property)) {
      String idpRole = mapping.getIDPRole();
      String oaevGroup = mapping.getOAEVGroup();
      boolean autoCreate = mapping.isAutoCreate();
      for (String role : rolesFromToken) {
        if (idpRole.equals(role)) {
          Optional<Group> groupOptional = groupRepository.findByName(oaevGroup);
          if (groupOptional.isPresent()) {
            List<Group> userGroups = user.getGroups();
            List<Group> existing = userGroups.stream().filter(userG -> userG.getName().equals(groupOptional.get().getName())).toList();
            if (existing.isEmpty()) {
              userGroups.add(groupOptional.get());
              user.setGroups(userGroups);
            }
          } else {
            if (autoCreate) {
              Group newGroup = new Group();
              newGroup.setName(idpRole);
              groupRepository.save(newGroup);
              List<Group> userGroups = user.getGroups();
              userGroups.add(newGroup);
              user.setGroups(userGroups);
            } else {
              log.error("Did not create new group");
            }
          }
        } else {
          log.error("No corresponding group role found");
        }
      }
    }
  }

  private static List<GroupRoleMap> safeParseMappings(String json) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.readValue(json, new TypeReference<List<GroupRoleMap>>() {});
    } catch (IOException e) {
      // Log and return empty list instead of throwing
      System.err.println("Failed to parse mappings: " + e.getMessage());
      return List.of();
    }
  }

}
