package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.ExcludeSuperclassListeners;
import java.util.List;

@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class EntityListenersArchTest {

  @ArchTest
  static final ArchRule no_entity_repeats_a_listener_of_its_superclass =
      classes()
          .that()
          .areAnnotatedWith(EntityListeners.class)
          .should(notRepeatAListenerOfASuperclass())
          .because(
              "a subclass inherits its superclass's listeners and Hibernate runs one per"
                  + " declaration: a repeated listener publishes every event twice (#9717)");

  private static ArchCondition<JavaClass> notRepeatAListenerOfASuperclass() {
    return new ArchCondition<>("not repeat a listener declared on a superclass") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        Class<?> entity = javaClass.reflect();
        if (entity.isAnnotationPresent(ExcludeSuperclassListeners.class)) {
          return;
        }
        List<Class<?>> own = listenersOf(entity);
        for (Class<?> parent = entity.getSuperclass();
            parent != null;
            parent = parent.getSuperclass()) {
          for (Class<?> listener : listenersOf(parent)) {
            if (own.contains(listener)) {
              events.add(
                  SimpleConditionEvent.violated(
                      javaClass,
                      "%s repeats %s, already declared on %s"
                          .formatted(
                              entity.getSimpleName(),
                              listener.getSimpleName(),
                              parent.getSimpleName())));
            }
          }
        }
      }
    };
  }

  private static List<Class<?>> listenersOf(Class<?> type) {
    EntityListeners listeners = type.getDeclaredAnnotation(EntityListeners.class);
    return listeners == null ? List.of() : List.of(listeners.value());
  }
}
