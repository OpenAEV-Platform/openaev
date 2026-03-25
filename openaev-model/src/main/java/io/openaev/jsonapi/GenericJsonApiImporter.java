package io.openaev.jsonapi;

import static io.openaev.jsonapi.GenericJsonApiIUtils.resolveType;
import static io.openaev.utils.reflection.ClazzUtils.instantiate;
import static io.openaev.utils.reflection.CollectionUtils.*;
import static io.openaev.utils.reflection.FieldUtils.getAllFieldsAsMap;
import static io.openaev.utils.reflection.FieldUtils.setField;
import static io.openaev.utils.reflection.RelationUtils.getAllRelationsAsMap;
import static io.openaev.utils.reflection.RelationUtils.setInverseRelation;
import static java.util.Collections.emptyMap;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Base;
import io.openaev.service.FileService;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.metamodel.EntityType;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/* Based on https://jsonapi.org/ */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenericJsonApiImporter<T extends Base> {

  private final EntityManager entityManager;
  @Resource private final ObjectMapper objectMapper;
  private final FileService fileService;

  @Transactional
  public T handleImportEntity(
      JsonApiDocument<ResourceObject> doc,
      IncludeOptions includeOptions,
      Function<T, T> sanityCheck) {
    if (doc == null || doc.data() == null) {
      throw new IllegalArgumentException("Data is required to import document");
    }
    if (includeOptions == null) {
      includeOptions = IncludeOptions.of(emptyMap());
    }
    Map<String, ResourceObject> includedMap = toMap(doc.included());
    // Cache keyed by id, with a boolean indicating whether the entity should be persisted or not at
    // the end
    Map<String, Pair<T, Boolean>> entityCache = new HashMap<>();
    List<CanRemapWeakRelationships> entitiesNeedingRemapping = new ArrayList<>();

    T entity =
        buildEntity(
            doc.data(), includedMap, entityCache, entitiesNeedingRemapping, includeOptions, true);
    T toPersist = Optional.ofNullable(sanityCheck).map(check -> check.apply(entity)).orElse(entity);

    // Persist included entities that not inner relationship
    for (Pair<T, Boolean> value : entityCache.values()) {
      if (!entityManager.contains(value.getLeft()) && !value.getRight()) {
        T e = value.getLeft();
        if (e.getId() != null && entityManager.find(e.getClass(), e.getId()) != null) {
          entityManager.merge(e);
        } else {
          entityManager.persist(e);
        }
      }
    }

    // Validate constraint
    entityManager.flush();

    // persists a first time to obtain generated IDs for all db-bound entities
    // to enable establishing a map from exported ID to new ID
    entityManager.persist(toPersist);

    Map<String, String> swappedIds =
        entityCache.entrySet().stream()
            .collect(
                Collectors.toMap(Map.Entry::getKey, (entry) -> entry.getValue().getLeft().getId()));

    for (CanRemapWeakRelationships crwr : entitiesNeedingRemapping) {
      crwr.remap(swappedIds);
    }
    // persist a second time to account for remappedIds
    entityManager.persist(toPersist);
    return entity;
  }

  public void handleImportDocument(
      JsonApiDocument<ResourceObject> doc, Map<String, byte[]> extras) {
    if (doc.included() != null) {
      for (Object o : doc.included()) {
        if (o instanceof ResourceObject ro && "document".equals(ro.type())) {
          Map<String, Object> attrs = ro.attributes();
          if (attrs != null && attrs.containsKey("document_name")) {
            String target = String.valueOf(attrs.get("document_name"));
            byte[] fileBytes = extras.get(target);
            if (fileBytes != null) {
              try (InputStream in = new ByteArrayInputStream(fileBytes)) {
                fileService.uploadFile(
                    target, in, fileBytes.length, Files.probeContentType(Path.of(target)));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
          }
        }
      }
    }
  }

  private Map<String, ResourceObject> toMap(List<Object> included) {
    if (included == null || included.isEmpty()) {
      return Map.of();
    }
    Map<String, ResourceObject> map = new LinkedHashMap<>();
    for (Object o : included) {
      ResourceObject ro = (ResourceObject) safeConvert(o, ResourceObject.class);
      if (ro != null) {
        map.put(ro.id(), ro);
      }
    }
    return map;
  }

  private void addToRemapping(
      List<CanRemapWeakRelationships> entitiesNeedingRemapping, Object obj) {
    if (!(obj instanceof CanRemapWeakRelationships) || entitiesNeedingRemapping.contains(obj)) {
      return;
    }
    entitiesNeedingRemapping.add((CanRemapWeakRelationships) obj);
  }

  private T buildEntity(
      ResourceObject resource,
      Map<String, ResourceObject> includedMap,
      Map<String, Pair<T, Boolean>> entityCache,
      List<CanRemapWeakRelationships> entitiesNeedingRemapping,
      IncludeOptions includeOptions,
      boolean rootEntity) {
    // Sanity check
    if (resource == null) {
      return null;
    }
    if (entityCache == null) {
      entityCache = new HashMap<>();
    }

    String id = resource.id();
    String type = resource.type();

    if (entityCache.containsKey(id)) {
      return entityCache.get(id).getLeft();
    }

    Class<T> clazz = classForTypeOrThrow(type);

    // Instantiate or load
    T entity = null;

    // For non-root entities with a valid ID and not marked as @InnerRelationship,
    // try loading the existing entity from the database.
    if (!rootEntity && !clazz.isAnnotationPresent(InnerRelationship.class)) {
      List<Field> businessIdFields =
          Arrays.stream(clazz.getDeclaredFields())
              .filter(f -> f.isAnnotationPresent(BusinessId.class))
              .toList();

      if (!businessIdFields.isEmpty()) {
        String jpql =
            "SELECT e FROM "
                + clazz.getSimpleName()
                + " e WHERE "
                + businessIdFields.stream()
                    .map(f -> "e." + f.getName() + " = :" + f.getName())
                    .collect(Collectors.joining(" AND "));

        TypedQuery<T> query = entityManager.createQuery(jpql, clazz);

        for (Field field : businessIdFields) {
          JsonProperty annotation = field.getAnnotation(JsonProperty.class);
          Object value = resource.attributes().get(annotation.value());
          query.setParameter(field.getName(), value);
        }
        List<T> results = query.getResultList();
        if (!results.isEmpty()) {
          entity = results.get(0);
          entityCache.put(entity.getId(), Pair.of(entity, true));
          return entity;
        }
      }
    }
    // Create new instance if not found.
    if (entity == null) {
      entity = instantiate(clazz);
    }
    if (!rootEntity) {
      entityCache.put(id, Pair.of(entity, clazz.isAnnotationPresent(InnerRelationship.class)));
    }

    this.addToRemapping(entitiesNeedingRemapping, entity);

    // Populate
    applyAttributes(entity, entitiesNeedingRemapping, resource.attributes());
    applyRelationships(
        entity,
        resource.relationships(),
        includedMap,
        entityCache,
        entitiesNeedingRemapping,
        includeOptions);

    return entity;
  }

  private void applyAttributes(
      T entity,
      List<CanRemapWeakRelationships> entitiesNeedingRemapping,
      Map<String, Object> attributes) {
    if (entity == null || attributes == null || attributes.isEmpty()) {
      return;
    }
    Map<String, Field> fields = getAllFieldsAsMap(entity.getClass());
    for (var e : attributes.entrySet()) {
      Field f = fields.get(e.getKey());
      if (f == null || f.isAnnotationPresent(Id.class)) {
        continue;
      }
      Object cast = safeConvert(e.getValue(), f.getType());
      setField(entity, f, cast);
      this.addToRemapping(entitiesNeedingRemapping, cast);
    }
  }

  private void applyRelationships(
      Object entity,
      Map<String, Relationship> rels,
      Map<String, ResourceObject> includedMap,
      Map<String, Pair<T, Boolean>> entityCache,
      List<CanRemapWeakRelationships> entitiesNeedingRemapping,
      IncludeOptions includeOptions) {
    if (entity == null || rels == null || rels.isEmpty()) {
      return;
    }

    Map<String, Field> relations = getAllRelationsAsMap(entity.getClass());

    for (var e : rels.entrySet()) {
      if (!includeOptions.include(e.getKey())) {
        continue;
      }

      Field f = relations.get(e.getKey());
      if (f == null) {
        continue;
      }

      Relationship rel = e.getValue();
      if (isCollection(f)) {
        List<ResourceIdentifier> ids = rel.asMany();

        Collection<Object> target = instantiateCollection(f);
        for (ResourceIdentifier ri : ids) {
          Object child =
              resolveOrBuildEntity(
                  ri, includedMap, entityCache, entitiesNeedingRemapping, includeOptions);
          if (child != null) {
            target.add(child);
            setInverseRelation(child, entity);
          }
        }
        replaceCollection(entity, f, target);

      } else {
        ResourceIdentifier ri = rel.asOne();
        Object child =
            (ri != null)
                ? resolveOrBuildEntity(
                    ri, includedMap, entityCache, entitiesNeedingRemapping, includeOptions)
                : null;
        setField(entity, f, child);
        if (child != null) {
          setInverseRelation(child, entity);
        }
      }
    }
  }

  private T resolveOrBuildEntity(
      ResourceIdentifier resourceIdentifier,
      Map<String, ResourceObject> includedMap,
      Map<String, Pair<T, Boolean>> entityCache,
      List<CanRemapWeakRelationships> entitiesNeedingRemapping,
      IncludeOptions includeOptions) {
    if (resourceIdentifier == null) {
      return null;
    }

    String id = resourceIdentifier.id();
    String type = resourceIdentifier.type();

    // Available in the bundle
    ResourceObject included = includedMap.get(id);
    if (included != null) {
      return buildEntity(
          included, includedMap, entityCache, entitiesNeedingRemapping, includeOptions, false);
    }

    // Not present in the bundle, resolve it
    Class<T> clazz = classForTypeOrThrow(type);
    return hasText(id) ? entityManager.getReference(clazz, id) : null;
  }

  private final Map<String, Class<T>> typeToClassCache = new HashMap<>();

  @SuppressWarnings("unchecked")
  private Class<T> classForTypeOrThrow(String type) {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("Type is required");
    }

    Class<T> cached = typeToClassCache.get(type);
    if (cached != null) {
      return cached;
    }

    for (EntityType<?> et : entityManager.getMetamodel().getEntities()) {
      Class<T> javaType = (Class<T>) et.getJavaType();
      String resolved = resolveType(javaType);
      typeToClassCache.putIfAbsent(resolved, javaType);
    }

    Class<T> found = typeToClassCache.get(type);
    if (found != null) {
      return found;
    }

    throw new IllegalArgumentException("Unknown type: " + type);
  }

  private Object safeConvert(Object value, Class<?> targetType) {
    if (value == null) {
      return null;
    }
    if (targetType.isInstance(value)) {
      return value;
    }
    return objectMapper.convertValue(value, targetType);
  }
}
