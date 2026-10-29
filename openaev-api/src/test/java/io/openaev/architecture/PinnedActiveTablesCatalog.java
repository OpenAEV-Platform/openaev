package io.openaev.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import io.openaev.config.ProductionActiveTables;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
 * have to copy. Every such annotation is a site, whatever it lists: its value is fixed when it is
 * written, so it cannot follow the next table production activates. A {@code DynamicPropertySource}
 * method is judged by what it registers: it is invoked with a recording registry, and the value
 * registered for the key is read the way production reads its own list ({@link
 * ProductionActiveTables#armedBy}), so a registered {@code *} is the tables it arms and {@code *}
 * beside a table cannot be evaluated. A class follows production only when every value it registers
 * holds the whole production list; anything else, including a registration that cannot be
 * evaluated, is a site. A class that runs with a pin declared on a type it extends is refused
 * ({@link #inheritedPins}). The key is matched as Spring Boot binds it, so a relaxed spelling such
 * as {@code activeTables} or {@code openaev.TENANT.active_tables} counts.
 *
 * <p>The registration methods of a class are invoked only when the key can be among what they
 * register: its class file holds the key's first two elements in a spelling Spring Boot binds to
 * them ({@link #mentionsTheKeyPrefix}), or a test-tree class those methods call, at any depth,
 * holds them, so a registration handed to a shared helper is judged by what the helper registers.
 * The calls are read from the class files, not run, so a registration that reaches no such class,
 * one that starts a server for instance, is never invoked. Once a class is invoked, all of its
 * registration methods run, whatever they do besides, so a class whose registration reaches the key
 * must keep those methods free of side effects. A supplier is evaluated when the scan runs, so one
 * that reads state set later (a field a {@code BeforeAll} fills, a started container) either fails,
 * and its class is reported as unevaluated, or is read before that state exists.
 *
 * <p>Not seen: a property file named in {@code locations}, a profile file, a {@code
 * DynamicPropertyRegistrar} bean, a {@code ContextCustomizerFactory}, a system property set by
 * hand, a registration whose key is assembled at run time from non-constant parts, and one that
 * reaches the key only through code outside the test tree or through a call the class file does not
 * name (reflection, a registry handed to a callback stored elsewhere).
 */
final class PinnedActiveTablesCatalog {

  static final String KEY = "openaev.tenant.active-tables";

  /** Test-tree helpers that pin on purpose to exercise this catalog, not real test classes. */
  static final String FIXTURE_PACKAGE = "io.openaev.architecture.fixture";

  private static final ConfigurationPropertyName KEY_NAME = ConfigurationPropertyName.of(KEY);

  /** The key's first two elements, in the canonical form Spring Boot compares them in. */
  private static final List<String> KEY_PREFIX = List.of("openaev", "tenant");

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
   * an inner class that inherits its configuration, its enclosing classes. A type reached by more
   * than one path, an interface two branches of a diamond both extend, is read once: what it
   * declares is one declaration, however many paths lead to it. Annotations repeated on one type
   * stay separate declarations.
   */
  static List<Declaration> declarations(Class<?> testClass) {
    List<Declaration> found = new ArrayList<>();
    collectDeclarations(testClass, new HashSet<>(), found);
    return found;
  }

  private static void collectDeclarations(
      Class<?> type, Set<Class<?>> visited, List<Declaration> found) {
    if (!visited.add(type)) {
      return;
    }
    MergedAnnotations annotations = MergedAnnotations.from(type, SearchStrategy.DIRECT);
    Stream.of(TestPropertySource.class, SpringBootTest.class)
        .flatMap(annotations::stream)
        .forEach(annotation -> collect(annotation, found));
    for (Class<?> parent : type.getInterfaces()) {
      collectDeclarations(parent, visited, found);
    }
    if (type.getSuperclass() != null && type.getSuperclass() != Object.class) {
      collectDeclarations(type.getSuperclass(), visited, found);
    }
    if (TestContextAnnotationUtils.searchEnclosingClass(type)) {
      collectDeclarations(type.getEnclosingClass(), visited, found);
    }
  }

  /**
   * The classes that run with a pin declared or registered on a class or interface they extend,
   * each with the type that pins. Spring hands such a class the pin: it searches the annotations of
   * superclasses and interfaces, and it runs the static {@code DynamicPropertySource} methods of
   * the class, its superclasses and every interface they implement ({@code
   * DynamicPropertiesContextCustomizerFactory} through {@code MethodIntrospector.selectMethods} in
   * spring-test 7.0, pinned against the library by the rules test). The pin's baseline reason was
   * written for the pinning type's assertions, not this class's, so the shape is refused rather
   * than read as covered by that line. A pin reached through an enclosing class is not inherited in
   * this sense: an inner class is part of the class that declares it, and that line covers it.
   */
  static Map<String, String> inheritedPins(
      Collection<Class<?>> classes, Map<String, Registration> dynamicPins) {
    Map<String, String> inherited = new TreeMap<>();
    for (Class<?> testClass : classes) {
      Set<String> supertypes = new HashSet<>();
      collectSupertypes(testClass, supertypes);
      Stream.concat(
              declarations(testClass).stream().map(Declaration::site),
              supertypes.stream().filter(dynamicPins::containsKey).sorted())
          .filter(supertypes::contains)
          .findFirst()
          .ifPresent(site -> inherited.put(testClass.getName(), site));
    }
    return inherited;
  }

  private static void collectSupertypes(Class<?> type, Set<String> supertypes) {
    for (Class<?> parent : type.getInterfaces()) {
      if (supertypes.add(parent.getName())) {
        collectSupertypes(parent, supertypes);
      }
    }
    if (type.getSuperclass() != null && supertypes.add(type.getSuperclass().getName())) {
      collectSupertypes(type.getSuperclass(), supertypes);
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
      if (methods.isEmpty()
          || !(mentionsTheKeyPrefix(testClass.getName(), testClass.getClassLoader())
              || reachesTheKeyPrefix(testClass, methods))) {
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
        registered.add(Registration.of(armedBy(supplier.get())));
      } catch (RuntimeException | LinkageError e) {
        registered.add(Registration.unevaluated(where + " supplier threw " + e));
      }
    }
    return registered;
  }

  /**
   * The tables a registered value arms, read by the production reader: the terminal {@code *} is
   * the tables it arms, and {@code *} beside a table throws, as production refuses it at startup.
   */
  private static Set<String> armedBy(Object value) {
    if (value instanceof Collection<?> collection) {
      return armedBy(collection.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }
    if (value instanceof Object[] array) {
      return armedBy(Arrays.stream(array).map(String::valueOf).collect(Collectors.joining(",")));
    }
    return ProductionActiveTables.armedBy(String.valueOf(value));
  }

  /**
   * Whether the class file holds the key's prefix, as a string constant or an annotation value. A
   * compile-time constant is copied into every class that uses it, so a key held in a constant of
   * another class is still found here.
   */
  private static boolean mentionsTheKeyPrefix(String className, ClassLoader loader) {
    String resource = className.replace('.', '/') + ".class";
    try (InputStream in = loader.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("cannot find the class file of " + className);
      }
      return mentionsTheKeyPrefix(in.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read the class file of " + className, e);
    }
  }

  /**
   * Whether the bytes hold the key's first two elements in a spelling Spring Boot binds to them.
   * Spring Boot splits a name into elements at {@code .}, {@code [} and {@code ]}, drops empty
   * elements, and compares an element ignoring case and every character that is not a letter or a
   * digit; a bracketed element is compared as written, which this reading accepts too. No character
   * outside ASCII compares equal to a letter of these two elements. So each element is matched as
   * its ASCII letters in order, case ignored, with any byte but an ASCII letter, a digit or a
   * separator between them, and the two elements are parted by at least one separator. Every
   * spelling the binder accepts is found, and a few it refuses are too ({@code [openaev.tenant]},
   * {@code openaev.tenantless}, a letter outside ASCII inside an element), which only runs a
   * registration that then registers nothing. Where the elements start and end is not checked: in a
   * class file the bytes around a string are its length and the next constant's, which may read as
   * letters.
   */
  static boolean mentionsTheKeyPrefix(byte[] classFile) {
    for (int start = 0; start < classFile.length; start++) {
      if (keyPrefixAt(classFile, start)) {
        return true;
      }
    }
    return false;
  }

  private static boolean keyPrefixAt(byte[] bytes, int start) {
    int at = start;
    for (int element = 0; element < KEY_PREFIX.size(); element++) {
      if (element > 0) {
        boolean separated = false;
        while (at < bytes.length && !isLetterOrDigit(bytes[at])) {
          separated |= isSeparator(bytes[at]);
          at++;
        }
        if (!separated) {
          return false;
        }
      }
      String name = KEY_PREFIX.get(element);
      for (int letter = 0; letter < name.length(); letter++) {
        while (letter > 0
            && at < bytes.length
            && !isLetterOrDigit(bytes[at])
            && !isSeparator(bytes[at])) {
          at++;
        }
        if (at == bytes.length || Character.toLowerCase((char) bytes[at]) != name.charAt(letter)) {
          return false;
        }
        at++;
      }
    }
    return true;
  }

  private static boolean isLetterOrDigit(byte value) {
    return (value >= 'a' && value <= 'z')
        || (value >= 'A' && value <= 'Z')
        || (value >= '0' && value <= '9');
  }

  private static boolean isSeparator(byte value) {
    return value == '.' || value == '[' || value == ']';
  }

  /**
   * Whether the registration methods call, at any depth through the test tree, a class whose file
   * holds the key's prefix. The calls and method references are read from the class files and
   * followed, never run; code outside the test tree is not followed.
   */
  private static boolean reachesTheKeyPrefix(Class<?> testClass, List<Method> methods) {
    ClassLoader loader = testClass.getClassLoader();
    Map<String, Optional<JavaClass>> imported = new HashMap<>();
    Deque<JavaCodeUnit> pending = new ArrayDeque<>();
    testTreeClass(testClass.getName(), loader, imported)
        .ifPresent(
            javaClass ->
                methods.forEach(
                    method ->
                        pending.add(
                            javaClass.getMethod(method.getName(), method.getParameterTypes()))));
    Set<String> followed = new HashSet<>();
    while (!pending.isEmpty()) {
      JavaCodeUnit unit = pending.pop();
      if (!followed.add(unit.getFullName())) {
        continue;
      }
      List<JavaCodeUnitAccess<?>> accesses = new ArrayList<>(unit.getCallsFromSelf());
      accesses.addAll(unit.getCodeUnitReferencesFromSelf());
      for (JavaCodeUnitAccess<?> access : accesses) {
        String owner = access.getTargetOwner().getName();
        Optional<JavaClass> target = testTreeClass(owner, loader, imported);
        if (target.isEmpty()) {
          continue;
        }
        if (mentionsTheKeyPrefix(owner, loader)) {
          return true;
        }
        target
            .get()
            .tryGetCodeUnitWithParameterTypeNames(
                access.getTarget().getName(),
                access.getTarget().getRawParameterTypes().stream().map(JavaClass::getName).toList())
            .ifPresent(pending::add);
      }
    }
    return false;
  }

  /** The class imported with its code, when it is a class of the test tree. */
  private static Optional<JavaClass> testTreeClass(
      String className, ClassLoader loader, Map<String, Optional<JavaClass>> imported) {
    return imported.computeIfAbsent(
        className,
        name -> {
          URL url = loader.getResource(name.replace('.', '/') + ".class");
          if (url == null
              || !ImportOption.Predefined.ONLY_INCLUDE_TESTS.includes(Location.of(url))) {
            return Optional.empty();
          }
          return Optional.of(new ClassFileImporter().importUrl(url).get(name));
        });
  }

  private static Class<?> load(String name, ClassLoader loader) {
    try {
      return Class.forName(name, false, loader);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("cannot load test class " + name, e);
    }
  }
}
