package io.openaev.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.repository.query.Param;

/**
 * Finds the call sites that resolve ids on the repository of a tenant table without naming a
 * tenant: the inherited {@code findAllById} and {@code getReferenceById}, and a declared {@code
 * findAllByIdIn*} method that has no {@code Tenant} in its name and no {@code tenantId} parameter.
 *
 * <p>A repository is a Spring Data interface; its table is the one its domain type is stored in,
 * read from the {@code Repository<T, ID>} root, so a repository that extends the bare marker
 * interface is read like the others. A repository whose table does not resolve is reported, never
 * skipped: a guard that skips what it cannot read passes on exactly the repositories it should
 * judge. A generic base interface (it declares type parameters) is no repository of one table and
 * is left out.
 *
 * <p>What the call graph cannot see, so a reviewer reads it: a call whose static receiver type is
 * not a repository of the code base ({@code JpaRepository} or {@code CrudRepository} named as the
 * field type, the generic receivers of {@code DatabaseHelper}), {@code findById} on a body id (a
 * path id and a body id look alike), id lookups declared under other names ({@code
 * SecurityPlatformRepository.findAllByIds}, {@code AiTargetRepository.findAiTargetsByIds}, {@code
 * InjectorContractRepository.findAllByIdOrExternalIdIn}), {@code deleteAllById}, {@code
 * findAll(Specification)}, {@code entityManager.find}, native queries and a call made by
 * reflection.
 */
final class LinkedIdResolutionCatalog {

  private static final String REPOSITORY = "org.springframework.data.repository.Repository";
  private static final Set<String> BARE_METHODS = Set.of("findAllById", "getReferenceById");
  private static final String DECLARED_PREFIX = "findAllByIdIn";

  private LinkedIdResolutionCatalog() {}

  /**
   * The production classes of the API and of the model. Each call imports them again and the result
   * is not kept in a static field, so the import leaves the heap once the guard has run, as the
   * other architecture tests that share its test JVM expect.
   */
  static JavaClasses productionClasses() {
    return new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("io.openaev");
  }

  /**
   * The repositories of the classes with their table, and the ones whose table does not resolve.
   */
  record Repositories(Map<String, String> tables, List<String> unresolved) {}

  static Repositories repositories(JavaClasses classes) {
    Map<String, String> tables = new TreeMap<>();
    List<String> unresolved = new ArrayList<>();
    for (JavaClass javaClass : classes) {
      if (!javaClass.isInterface()
          || javaClass.getName().equals(REPOSITORY)
          || !javaClass.isAssignableTo(REPOSITORY)) {
        continue;
      }
      Class<?> type = javaClass.reflect();
      if (type.getTypeParameters().length > 0) {
        continue;
      }
      Class<?>[] arguments = GenericTypeResolver.resolveTypeArguments(type, repositoryRoot());
      Class<?> domain = arguments == null ? null : arguments[0];
      if (domain == null || domain == Object.class) {
        unresolved.add(javaClass.getName() + ": no domain type in its Repository<T, ID>");
        continue;
      }
      try {
        tables.put(javaClass.getName(), JsonTypeDirtyOnLoadCatalog.tableOf(domain));
      } catch (IllegalStateException e) {
        unresolved.add(javaClass.getName() + ": " + e.getMessage());
      }
    }
    return new Repositories(tables, unresolved);
  }

  /**
   * The sites of {@code classes} that resolve ids without a tenant on a repository of one of the
   * {@code activeTables}, with the number of calls and method references each holds. Several calls
   * in one method on one repository are one site, whose number the baseline pins, and the calls of
   * a lambda belong to the method that creates it.
   */
  static Map<String, Integer> sites(
      JavaClasses classes, Repositories repositories, Set<String> activeTables) {
    Map<String, Integer> calls = new TreeMap<>();
    for (JavaClass javaClass : classes) {
      for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
        count(
            calls,
            javaClass,
            call.getOrigin().getName(),
            Map.of(call.getTargetOwner().getName(), 1),
            List.of(call),
            repositories,
            activeTables);
      }
      referencesOf(javaClass)
          .forEach(
              (key, references) ->
                  count(
                      calls,
                      javaClass,
                      key.origin(),
                      receiversOf(javaClass, key, references.size()),
                      references,
                      repositories,
                      activeTables));
    }
    return calls;
  }

  /** Where a method reference is made: the method, and the method it refers to. */
  private record ReferenceKey(String origin, String owner, String name) {}

  private static Map<ReferenceKey, List<JavaCodeUnitAccess<?>>> referencesOf(JavaClass javaClass) {
    Map<ReferenceKey, List<JavaCodeUnitAccess<?>>> references =
        new TreeMap<>(
            Comparator.comparing(ReferenceKey::origin)
                .thenComparing(ReferenceKey::owner)
                .thenComparing(ReferenceKey::name));
    for (JavaMethodReference reference : javaClass.getMethodReferencesFromSelf()) {
      references
          .computeIfAbsent(
              new ReferenceKey(
                  reference.getOrigin().getName(),
                  reference.getTargetOwner().getName(),
                  reference.getName()),
              key -> new ArrayList<>())
          .add(reference);
    }
    return references;
  }

  /**
   * The type each reference to one method is taken on, with the number of references on it. A
   * reference names the type that declares the method ({@code CrudRepository} for {@code
   * tagRepository::findAllById}), so the type it is taken on is read from the class file, one entry
   * per reference: two references to the same inherited method on two repositories are one each,
   * never two on both. A reference whose receiver the class file does not give is counted on the
   * declaring type.
   */
  private static Map<String, Integer> receiversOf(
      JavaClass javaClass, ReferenceKey key, int references) {
    List<String> receivers =
        CallSites.receiverList(
            javaClass, key.origin(), new CallSites.Target(key.owner(), key.name()));
    if (receivers.isEmpty()) {
      return Map.of(key.owner(), references);
    }
    Map<String, Integer> counted = new TreeMap<>();
    receivers.forEach(receiver -> counted.merge(receiver, 1, Integer::sum));
    return counted;
  }

  private static void count(
      Map<String, Integer> calls,
      JavaClass javaClass,
      String origin,
      Map<String, Integer> receivers,
      List<? extends JavaCodeUnitAccess<?>> accesses,
      Repositories repositories,
      Set<String> activeTables) {
    receivers.forEach(
        (receiver, times) -> {
          String table = repositories.tables().get(receiver);
          if (table != null
              && activeTables.contains(table)
              && accesses.stream().anyMatch(access -> resolvesWithoutTenant(receiver, access))) {
            calls.merge(
                javaClass.getName()
                    + "#"
                    + origin
                    + " -> "
                    + receiver.substring(
                        Math.max(receiver.lastIndexOf('.'), receiver.lastIndexOf('$')) + 1),
                times,
                Integer::sum);
          }
        });
  }

  /**
   * True when the method an access names is one of the bare ones, or a declared {@code
   * findAllByIdIn*} that takes no tenant. Overloads are told apart by their parameter types: a
   * method with a tenant next to one without is judged on the one the access names. A method that
   * no declaration of the repository matches is judged unscoped.
   */
  private static boolean resolvesWithoutTenant(String repository, JavaCodeUnitAccess<?> access) {
    String method = access.getName();
    if (BARE_METHODS.contains(method)) {
      return true;
    }
    if (!method.startsWith(DECLARED_PREFIX) || method.contains("Tenant")) {
      return false;
    }
    List<String> parameters =
        access.getTarget().getRawParameterTypes().stream().map(JavaClass::getName).toList();
    List<Method> declared =
        Arrays.stream(load(repository).getMethods())
            .filter(candidate -> candidate.getName().equals(method))
            .filter(
                candidate ->
                    Arrays.stream(candidate.getParameterTypes())
                        .map(Class::getName)
                        .toList()
                        .equals(parameters))
            .toList();
    return declared.isEmpty()
        || declared.stream().anyMatch(candidate -> !takesTheTenant(candidate));
  }

  private static boolean takesTheTenant(Method method) {
    for (Parameter parameter : method.getParameters()) {
      Param named = parameter.getAnnotation(Param.class);
      if ("tenantId".equals(parameter.getName())
          || (named != null && "tenantId".equals(named.value()))) {
        return true;
      }
    }
    return false;
  }

  private static Class<?> load(String name) {
    try {
      return Class.forName(name, false, LinkedIdResolutionCatalog.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("cannot load " + name, e);
    }
  }

  private static Class<?> repositoryRoot() {
    return load(REPOSITORY);
  }
}
