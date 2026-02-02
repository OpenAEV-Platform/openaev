package io.openaev.schema;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Utility class for scanning and finding subclasses of a given class at runtime.
 *
 * <p>This scanner uses Spring's classpath scanning capabilities to find all classes that extend or
 * implement a given parent class within a specified package. It's primarily used by {@link
 * SchemaUtils} to build comprehensive property schemas that include all subclass properties.
 *
 * @see SchemaUtils#schemaWithSubtypes(Class)
 */
public class SubclassScanner {

  private SubclassScanner() {
    // Utility class - prevent instantiation
  }

  /**
   * Finds all subclasses of a given class within a package.
   *
   * <p>This method scans the classpath to find all classes that extend or implement the specified
   * class. It's useful for building complete schemas that include properties from all entity
   * subtypes.
   *
   * @param basePackage the package to scan (e.g., "io.openaev.database.model")
   * @param clazz the parent class or interface to find subclasses of
   * @return a set of all found subclasses, may contain nulls if class loading fails
   */
  public static Set<Class<?>> getSubclasses(String basePackage, Class<?> clazz) {
    ClassPathScanningCandidateComponentProvider provider =
        new ClassPathScanningCandidateComponentProvider(false);
    provider.addIncludeFilter(new AssignableTypeFilter(clazz));
    return provider.findCandidateComponents(basePackage).stream()
        .map(
            beanDefinition -> {
              try {
                return Class.forName(beanDefinition.getBeanClassName());
              } catch (ClassNotFoundException e) {
                return null;
              }
            })
        .collect(Collectors.toSet());
  }
}
