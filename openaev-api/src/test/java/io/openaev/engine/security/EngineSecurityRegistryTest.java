package io.openaev.engine.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.annotation.Indexable;
import io.openaev.engine.model.EsBase;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * The registry must describe every indexed type, and only with fields that exist: a type missing
 * from it is unreadable by any restricted reader, and a misspelled field silently filters nothing.
 */
@DisplayName("Engine security registry")
class EngineSecurityRegistryTest {

  private static Map<String, Class<?>> indexedTypes() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Indexable.class));
    Map<String, Class<?>> types = new java.util.HashMap<>();
    for (BeanDefinition definition : scanner.findCandidateComponents("io.openaev.engine.model")) {
      Class<?> type = Class.forName(definition.getBeanClassName());
      if (EsBase.class.isAssignableFrom(type)) {
        types.put(type.getAnnotation(Indexable.class).index(), type);
      }
    }
    return types;
  }

  private static Set<String> fieldNames(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      fields.addAll(List.of(current.getDeclaredFields()));
    }
    return fields.stream().map(Field::getName).collect(Collectors.toSet());
  }

  @Test
  @DisplayName("given every indexed type, should be registered")
  void given_everyIndexedType_should_beRegistered() throws ClassNotFoundException {
    // -- ACT --
    Set<String> indexed = indexedTypes().keySet();

    // -- ASSERT --
    assertThat(EngineSecurityRegistry.entities().keySet()).containsAll(indexed);
  }

  @Test
  @DisplayName("given a registered type, should only name fields of its document")
  void given_registeredType_should_onlyNameExistingFields() throws ClassNotFoundException {
    // -- ARRANGE --
    Map<String, Class<?>> types = indexedTypes();

    // -- ACT / ASSERT --
    EngineSecurityRegistry.entities()
        .forEach(
            (entity, security) -> {
              Set<String> fields = fieldNames(types.get(entity));
              assertThat(fields).as(entity + " asset fields").containsAll(security.assetFields());
              assertThat(fields)
                  .as(entity + " grant keys")
                  .containsAll(
                      security.grantKeys().stream()
                          .map(EngineSecurityRegistry.GrantKeyField::field)
                          .toList());
            });
  }
}
