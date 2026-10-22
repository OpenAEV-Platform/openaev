package io.openaev.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.support.TestPropertySourceUtils;

/**
 * Finds the test classes that set {@code openaev.tenant.active-tables} themselves instead of
 * following the production list.
 *
 * <p>Two shapes are read. An inlined property on {@code @TestPropertySource} or
 * {@code @SpringBootTest} is read from the compiled class through Spring's own merged-annotation
 * search and property parser, so a concatenated literal, a composed annotation and an inherited
 * declaration all resolve as Spring would resolve them; the site is the class that declares the
 * annotation. A {@code @DynamicPropertySource} method cannot be evaluated without running it, so
 * the source file is read instead: a file that registers the key and does not read {@link
 * io.openaev.config.ProductionActiveTables} is a site.
 *
 * <p>Not seen: a property file named in {@code locations}, a key held in a constant and passed to
 * the registry, and a system property set by hand. None exists in the test tree today.
 */
final class PinnedActiveTablesCatalog {

  static final String KEY = "openaev.tenant.active-tables";

  /** Test-tree helpers that pin on purpose to exercise this catalog, not real test classes. */
  static final String FIXTURE_PACKAGE = "io.openaev.architecture.fixture";

  static final Path TEST_SOURCES = Path.of("src/test/java");

  private static final String QUOTED_KEY = "\"" + KEY + "\"";
  private static final String PRODUCTION_READER = "ProductionActiveTables.names()";

  private PinnedActiveTablesCatalog() {}

  /** Every annotation pin of the test tree, keyed by declaring class. */
  static Map<String, Set<String>> testTreeAnnotationPins() {
    ClassLoader loader = PinnedActiveTablesCatalog.class.getClassLoader();
    List<Class<?>> classes =
        new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("io.openaev")
                .stream()
                .filter(javaClass -> !javaClass.getPackageName().startsWith(FIXTURE_PACKAGE))
                .filter(javaClass -> !javaClass.isAnonymousClass() && !javaClass.isLocalClass())
                .map(JavaClass::getName)
                .<Class<?>>map(name -> load(name, loader))
                .toList();
    return annotationPins(classes);
  }

  /** Every dynamic pin of the test tree. */
  static Set<String> testTreeDynamicPins() {
    return dynamicPins(TEST_SOURCES);
  }

  /**
   * The pinned table list of each class that declares one, keyed by the class that wrote the
   * annotation. A subclass inheriting the pin is not a second site.
   */
  static Map<String, Set<String>> annotationPins(Collection<Class<?>> classes) {
    Map<String, Set<String>> pins = new TreeMap<>();
    for (Class<?> testClass : classes) {
      MergedAnnotations annotations =
          MergedAnnotations.from(testClass, SearchStrategy.TYPE_HIERARCHY);
      Stream.of(TestPropertySource.class, SpringBootTest.class)
          .flatMap(type -> annotations.stream(type))
          .forEach(annotation -> collect(annotation, pins));
    }
    return pins;
  }

  /**
   * The classes, named after their source file, whose {@code @DynamicPropertySource} registers the
   * key without reading the production list.
   */
  static Set<String> dynamicPins(Path sourceRoot) {
    try (Stream<Path> files = Files.walk(sourceRoot)) {
      return files
          .filter(file -> file.toString().endsWith(".java"))
          .filter(file -> pinsDynamically(read(file)))
          .map(file -> className(sourceRoot, file))
          .collect(Collectors.toCollection(TreeSet::new));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot walk " + sourceRoot, e);
    }
  }

  private static void collect(
      MergedAnnotation<? extends Annotation> annotation, Map<String, Set<String>> pins) {
    Map<String, Object> properties =
        TestPropertySourceUtils.convertInlinedPropertiesToMap(
            annotation.getStringArray("properties"));
    if (!properties.containsKey(KEY)) {
      return;
    }
    String site = ((Class<?>) annotation.getSource()).getName();
    pins.merge(site, tables(String.valueOf(properties.get(KEY))), (a, b) -> a);
  }

  static Set<String> tables(String value) {
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .map(name -> name.toLowerCase(Locale.ROOT))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static boolean pinsDynamically(String source) {
    return source.contains("@DynamicPropertySource")
        && source.contains(QUOTED_KEY)
        && !source.contains(PRODUCTION_READER);
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + file, e);
    }
  }

  private static String className(Path sourceRoot, Path file) {
    String relative = sourceRoot.relativize(file).toString();
    return relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
  }

  private static Class<?> load(String name, ClassLoader loader) {
    try {
      return Class.forName(name, false, loader);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("cannot load test class " + name, e);
    }
  }
}
