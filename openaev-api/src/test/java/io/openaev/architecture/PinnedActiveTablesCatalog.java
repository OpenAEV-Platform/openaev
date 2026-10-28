package io.openaev.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.support.TestPropertySourceUtils;

/**
 * Finds the test classes that set {@code openaev.tenant.active-tables} themselves instead of
 * following the production list.
 *
 * <p>Two shapes are read, both from the compiled classes. An inlined property on {@code
 * TestPropertySource} or {@code SpringBootTest} is read through Spring's own merged-annotation
 * model and property parser, so a concatenated literal and a composed annotation resolve as Spring
 * resolves them; the site is the class that declares the annotation. The annotations are searched
 * where Spring searches them (the class, its interfaces, its superclasses, the enclosing class of
 * an inner class), and a class that sets the key in more than one of them is reported as
 * conflicting rather than resolved, since Spring keeps one value by a precedence the guard would
 * have to copy. A {@code DynamicPropertySource} method is judged by what it registers: it is
 * invoked with a recording registry, and the value registered for the key is read. A class follows
 * production only when every value it registers holds the whole production list; anything else,
 * including a registration that cannot be evaluated, is a site. The key is matched as Spring Boot
 * binds it, so a relaxed spelling such as {@code activeTables} counts.
 *
 * <p>Not seen: a property file named in {@code locations}, a profile file, a {@code
 * DynamicPropertyRegistrar} bean, a {@code ContextCustomizerFactory}, a system property set by
 * hand, and a {@code DynamicPropertySource} whose class file does not hold the text {@code
 * openaev.tenant} (a key assembled at run time from non-constant parts). The registration methods
 * of a class are invoked only when its class file holds that text; then all of them run, whatever
 * they do besides, so a class that names the key must keep its registration methods free of side
 * effects such as starting a server. A supplier is evaluated when the scan runs, so one that reads
 * state set later (a field a {@code BeforeAll} fills, a started container) either fails, and its
 * class is reported as unevaluated, or is read before that state exists.
 */
final class PinnedActiveTablesCatalog {

  static final String KEY = "openaev.tenant.active-tables";

  /** Test-tree helpers that pin on purpose to exercise this catalog, not real test classes. */
  static final String FIXTURE_PACKAGE = "io.openaev.architecture.fixture";

  private static final ConfigurationPropertyName KEY_NAME = ConfigurationPropertyName.of(KEY);
  private static final byte[] KEY_PREFIX = "openaev.tenant".getBytes(StandardCharsets.US_ASCII);

  /**
   * What a class's dynamic registrations give the key: the tables, or, when they cannot be read,
   * why. {@code tables} is null exactly when {@code failure} is set.
   */
  record Registration(Set<String> tables, String failure) {

    static Registration of(Set<String> tables) {
      return new Registration(tables, null);
    }

    static Registration unevaluated(String failure) {
      return new Registration(null, failure);
    }

    boolean evaluated() {
      return failure == null;
    }

    boolean holdsAll(Set<String> production) {
      return evaluated() && tables.containsAll(production);
    }
  }

  private PinnedActiveTablesCatalog() {}

  /** Every class of the test tree, the catalog's own fixtures excepted. */
  static List<Class<?>> testTreeClasses() {
    return testClasses(
        "io.openaev", javaClass -> !FIXTURE_PACKAGE.equals(javaClass.getPackageName()));
  }

  /** The catalog's fixtures, imported the way the test tree is. */
  static List<Class<?>> fixtureClasses() {
    return testClasses(FIXTURE_PACKAGE, javaClass -> true);
  }

  private static List<Class<?>> testClasses(
      String packageName, java.util.function.Predicate<JavaClass> keep) {
    ClassLoader loader = PinnedActiveTablesCatalog.class.getClassLoader();
    return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages(packageName)
            .stream()
            .filter(keep)
            .filter(javaClass -> !javaClass.isAnonymousClass() && !javaClass.isLocalClass())
            .map(JavaClass::getName)
            .sorted()
            .<Class<?>>map(name -> load(name, loader))
            .toList();
  }

  /** One annotation that sets the key: the class that wrote it and the tables it sets. */
  record Declaration(String site, Set<String> tables) {}

  /**
   * The pinned table list of each class that declares one, keyed by the class that wrote the
   * annotation. A subclass inheriting the pin is not a second site. A class that writes the key in
   * several annotations has no single list, so it maps to null and reads as unevaluated.
   */
  static Map<String, Set<String>> annotationPins(Collection<Class<?>> classes) {
    Map<String, Set<String>> pins = new TreeMap<>();
    for (Class<?> testClass : classes) {
      Map<String, List<Set<String>>> bySite = new TreeMap<>();
      declarations(testClass)
          .forEach(
              declaration ->
                  bySite
                      .computeIfAbsent(declaration.site(), site -> new ArrayList<>())
                      .add(declaration.tables()));
      bySite.forEach((site, values) -> pins.put(site, values.size() == 1 ? values.get(0) : null));
    }
    return pins;
  }

  /**
   * The classes that set the key in more than one annotation, with the sites that do. Spring keeps
   * one of the values by a precedence of its own (the last repeated declaration, a subclass over
   * its parent, an inner class over its enclosing class, a {@code TestPropertySource} over a {@code
   * SpringBootTest}), so the guard does not reproduce it: such a class must declare the key once.
   */
  static Map<String, List<String>> conflictingDeclarations(Collection<Class<?>> classes) {
    Map<String, List<String>> conflicts = new TreeMap<>();
    for (Class<?> testClass : classes) {
      List<Declaration> declarations = declarations(testClass);
      if (declarations.size() > 1) {
        conflicts.put(testClass.getName(), declarations.stream().map(Declaration::site).toList());
      }
    }
    return conflicts;
  }

  /**
   * Every annotation that sets the key for a class, searched where Spring searches: the class
   * itself (repeated and composed annotations included), its interfaces, its superclasses and, for
   * an inner class that inherits its configuration, its enclosing classes.
   */
  static List<Declaration> declarations(Class<?> testClass) {
    List<Declaration> found = new ArrayList<>();
    collectDeclarations(testClass, found);
    return found;
  }

  private static void collectDeclarations(Class<?> type, List<Declaration> found) {
    MergedAnnotations annotations = MergedAnnotations.from(type, SearchStrategy.DIRECT);
    Stream.of(TestPropertySource.class, SpringBootTest.class)
        .flatMap(annotations::stream)
        .forEach(annotation -> collect(annotation, found));
    for (Class<?> parent : type.getInterfaces()) {
      collectDeclarations(parent, found);
    }
    if (type.getSuperclass() != null && type.getSuperclass() != Object.class) {
      collectDeclarations(type.getSuperclass(), found);
    }
    if (TestContextAnnotationUtils.searchEnclosingClass(type)) {
      collectDeclarations(type.getEnclosingClass(), found);
    }
  }

  /**
   * What each class's own {@code DynamicPropertySource} methods register for the key, keyed by the
   * class that declares them. A class whose methods never register the key is absent.
   */
  static Map<String, Registration> dynamicRegistrations(Collection<Class<?>> classes) {
    Map<String, Registration> registrations = new TreeMap<>();
    for (Class<?> testClass : classes) {
      List<Method> methods =
          Arrays.stream(testClass.getDeclaredMethods())
              .filter(method -> method.isAnnotationPresent(DynamicPropertySource.class))
              .filter(method -> Modifier.isStatic(method.getModifiers()))
              .toList();
      if (methods.isEmpty() || !mentionsTheKeyPrefix(testClass)) {
        continue;
      }
      List<Registration> found = new ArrayList<>();
      methods.forEach(method -> found.addAll(evaluate(method)));
      if (found.size() == 1) {
        registrations.put(testClass.getName(), found.get(0));
      } else if (found.size() > 1) {
        registrations.put(
            testClass.getName(),
            Registration.unevaluated(
                "registers the key "
                    + found.size()
                    + " times, and Spring keeps whichever runs last"));
      }
    }
    return registrations;
  }

  /** The registrations that do not hold the whole production list: each one is a pin. */
  static Map<String, Registration> dynamicPins(
      Map<String, Registration> registrations, Set<String> production) {
    Map<String, Registration> pins = new TreeMap<>();
    registrations.forEach(
        (testClass, registration) -> {
          if (!registration.holdsAll(production)) {
            pins.put(testClass, registration);
          }
        });
    return pins;
  }

  static Set<String> tables(String value) {
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .map(name -> name.toLowerCase(Locale.ROOT))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** Whether Spring Boot binds this property name to the key. */
  static boolean isKey(String name) {
    return KEY_NAME.equals(ConfigurationPropertyName.adapt(name, '.'));
  }

  private static void collect(
      MergedAnnotation<? extends Annotation> annotation, List<Declaration> found) {
    Map<String, Object> properties =
        TestPropertySourceUtils.convertInlinedPropertiesToMap(
            annotation.getStringArray("properties"));
    properties.forEach(
        (name, value) -> {
          if (isKey(name)) {
            String site = ((Class<?>) annotation.getSource()).getName();
            found.add(new Declaration(site, tables(String.valueOf(value))));
          }
        });
  }

  /** Runs one registration method and returns what it registered for the key, if anything. */
  private static List<Registration> evaluate(Method method) {
    String where = method.getDeclaringClass().getSimpleName() + "." + method.getName();
    List<Supplier<Object>> suppliers = new ArrayList<>();
    DynamicPropertyRegistry recorder =
        (name, supplier) -> {
          if (isKey(name)) {
            suppliers.add(supplier);
          }
        };
    try {
      method.setAccessible(true);
      method.invoke(null, recorder);
    } catch (InvocationTargetException e) {
      return List.of(Registration.unevaluated(where + " threw " + e.getCause()));
    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
      return List.of(Registration.unevaluated(where + " could not be invoked: " + e));
    }
    List<Registration> registered = new ArrayList<>();
    for (Supplier<Object> supplier : suppliers) {
      try {
        registered.add(Registration.of(tables(supplier.get())));
      } catch (RuntimeException | LinkageError e) {
        registered.add(Registration.unevaluated(where + " supplier threw " + e));
      }
    }
    return registered;
  }

  private static Set<String> tables(Object value) {
    if (value instanceof Collection<?> collection) {
      return tables(collection.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }
    if (value instanceof Object[] array) {
      return tables(Arrays.stream(array).map(String::valueOf).collect(Collectors.joining(",")));
    }
    return tables(String.valueOf(value));
  }

  /**
   * Whether the class file holds the key's prefix, as a string constant or an annotation value. A
   * compile-time constant is copied into every class that uses it, so a key held in a constant of
   * another class is still found here.
   */
  private static boolean mentionsTheKeyPrefix(Class<?> testClass) {
    String resource = testClass.getName().replace('.', '/') + ".class";
    try (InputStream in = testClass.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("cannot find the class file of " + testClass.getName());
      }
      return contains(in.readAllBytes(), KEY_PREFIX);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read the class file of " + testClass.getName(), e);
    }
  }

  private static boolean contains(byte[] haystack, byte[] needle) {
    outer:
    for (int i = 0; i <= haystack.length - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return true;
    }
    return false;
  }

  private static Class<?> load(String name, ClassLoader loader) {
    try {
      return Class.forName(name, false, loader);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("cannot load test class " + name, e);
    }
  }
}
