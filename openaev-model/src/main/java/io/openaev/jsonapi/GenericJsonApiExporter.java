package io.openaev.jsonapi;

import static io.openaev.jsonapi.GenericJsonApiIUtils.resolveType;
import static io.openaev.jsonapi.IncludeOptions.shouldInclude;
import static io.openaev.utils.reflection.ClazzUtils.readId;
import static io.openaev.utils.reflection.CollectionUtils.isCollection;
import static io.openaev.utils.reflection.CollectionUtils.toCollection;
import static io.openaev.utils.reflection.FieldUtils.*;
import static io.openaev.utils.reflection.RelationUtils.isRelation;
import static java.util.Collections.emptyMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.lang.reflect.Field;
import java.util.*;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

/* Based on https://jsonapi.org/ */
@Component
public class GenericJsonApiExporter {

  public JsonApiDocument<ResourceObject> handleExport(
      Object entity, IncludeOptions includeOptions) {
    if (includeOptions == null) {
      includeOptions = IncludeOptions.of(emptyMap());
    }
    Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    List<ResourceObject> included = new ArrayList<>();
    ResourceObject root = buildResourceObject(entity, includeOptions, visited, included);
    return new JsonApiDocument<>(root, included.isEmpty() ? null : new ArrayList<>(included));
  }

  private ResourceObject buildResourceObject(
      Object entity,
      IncludeOptions includeOptions,
      Set<Object> visited,
      List<ResourceObject> included) {
    // Sanity check
    if (entity == null) {
      return null;
    }
    // Skip loop
    if (!visited.add(entity)) {
      return null;
    }
    // Force loading
    Hibernate.initialize(entity);
    entity = Hibernate.unproxy(entity);

    Class<?> clazz = entity.getClass();
    String type = resolveType(clazz);
    String id = readId(entity);

    Map<String, Object> attrs = computeAllFieldValues(entity);
    Map<String, Relationship> rels =
        computeAllRelationshipsValues(entity, includeOptions, visited, included);

    return new ResourceObject(id, type, attrs, rels);
  }

  private Map<String, Relationship> computeAllRelationshipsValues(
      Object entity,
      IncludeOptions includeOptions,
      Set<Object> visited,
      List<ResourceObject> included) {
    Map<String, Relationship> rels = new LinkedHashMap<>();
    for (Field f : getAllFields(entity.getClass())) {
      if (f.isAnnotationPresent(JsonIgnore.class)
          || !isRelation(f)
          || !shouldInclude(f, includeOptions)) {
        continue;
      }

      Object value = getField(entity, f);
      if (value == null) {
        continue;
      }

      if (isCollection(f)) {
        extractRelationshipValues(entity, includeOptions, visited, included, rels, f);
      } else {
        extractRelationshipValue(entity, includeOptions, visited, included, rels, f);
      }
    }
    return rels.isEmpty() ? null : rels;
  }

  private void extractRelationshipValues(
      Object entity,
      IncludeOptions includeOptions,
      Set<Object> visited,
      List<ResourceObject> included,
      Map<String, Relationship> rels,
      Field f) {
    String relName = resolveFieldJsonName(f);
    Object value = getField(entity, f);
    // Force loading
    Hibernate.initialize(value);
    value = Hibernate.unproxy(value);
    Collection<?> col = toCollection(value);
    if (col.isEmpty()) {
      return;
    }

    List<ResourceIdentifier> ids = new ArrayList<>();
    for (Object child : col) {
      ids.add(new ResourceIdentifier(readId(child), resolveType(child.getClass())));
    }
    rels.put(relName, new Relationship(ids));

    for (Object child : col) {
      var ro = buildResourceObject(child, includeOptions, visited, included);
      if (ro != null) {
        included.add(ro);
      }
    }
  }

  private void extractRelationshipValue(
      Object entity,
      IncludeOptions includeOptions,
      Set<Object> visited,
      List<ResourceObject> included,
      Map<String, Relationship> rels,
      Field f) {
    String relName = resolveFieldJsonName(f);
    Object value = getField(entity, f);
    // Force loading
    Hibernate.initialize(value);
    value = Hibernate.unproxy(value);
    if (value == null) {
      return;
    }
    // Force loading
    Hibernate.initialize(value);
    value = Hibernate.unproxy(value);
    rels.put(
        relName,
        new Relationship(new ResourceIdentifier(readId(value), resolveType(value.getClass()))));
    var ro = buildResourceObject(value, includeOptions, visited, included);
    if (ro != null) {
      included.add(ro);
    }
  }
}
