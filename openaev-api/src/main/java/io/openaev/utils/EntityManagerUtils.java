package io.openaev.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntityManagerUtils {

  @PersistenceContext private EntityManager entityManager;

  private final ObjectMapper objectMapper;

  public <T> T findEntity(Class<T> entityClass, Object entityId) {
    try {
      return entityManager.find(entityClass, entityId);
    } catch (Exception e) {
      log.debug(
          "[EntityManagerUtils] Failed to find entity {}/{}: {}",
          entityClass,
          entityId,
          e.getMessage());
    }
    return null;
  }

  public <T> JsonNode snapshotEntity(Class<T> entityClass, Object entityId) {
    try {
      T entity = findEntity(entityClass, entityId);

      if (entity != null) {
        return objectMapper.valueToTree(entity);
      }
    } catch (Exception e) {
      log.debug(
          "[EntityManagerUtils] Failed to snapshot entity {}/{}: {}",
          entityClass,
          entityId,
          e.getMessage());
    }
    return null;
  }

  /** Extracts a name from a snapshotted JSON node. */
  public static String extractNameFromSnapshot(JsonNode snapshot) {
    if (snapshot == null) {
      return null;
    }
    // Try common name fields in order of precedence
    for (String field :
        new String[] {
          "scenario_name",
          "exercise_name",
          "inject_title",
          "user_firstname",
          "name",
          "role_name",
          "group_name"
        }) {
      JsonNode node = snapshot.get(field);
      if (node != null && node.isTextual()) {
        return node.asText();
      }
    }
    return null;
  }
}
