package io.openaev.schema;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

public class SubclassScanner {
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
