package io.openaev.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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

  private static JavaClasses production;

  private LinkedIdResolutionCatalog() {}

  /** The production classes of the API and of the model, imported once. */
  static synchronized JavaClasses productionClasses() {
    if (production == null) {
      production =
          new ClassFileImporter()
              .withImportOption(new ImportOption.DoNotIncludeTests())
              .importPackages("io.openaev");
    }
    return production;
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
      for (JavaCodeUnitAccess<?> access : accessesOf(javaClass)) {
        for (String receiver : receiversOf(javaClass, access)) {
          String table = repositories.tables().get(receiver);
          if (table != null
              && activeTables.contains(table)
              && resolvesWithoutTenant(receiver, access.getName())) {
            String site =
                javaClass.getName()
                    + "#"
                    + access.getOrigin().getName()
                    + " -> "
                    + receiver.substring(
                        Math.max(receiver.lastIndexOf('.'), receiver.lastIndexOf('$')) + 1);
            calls.merge(site, 1, Integer::sum);
          }
        }
      }
    }
    return calls;
  }

  private static List<JavaCodeUnitAccess<?>> accessesOf(JavaClass javaClass) {
    List<JavaCodeUnitAccess<?>> accesses = new ArrayList<>(javaClass.getMethodCallsFromSelf());
    accesses.addAll(javaClass.getMethodReferencesFromSelf());
    return accesses;
  }

  /**
   * The types a call or a method reference is taken on. A call names its receiver's type. A
   * reference names the type that declares the method ({@code CrudRepository} for {@code
   * tagRepository::findAllById}), so the type it is taken on is read from the class file.
   */
  private static Set<String> receiversOf(JavaClass javaClass, JavaCodeUnitAccess<?> access) {
    Set<String> receivers = new TreeSet<>();
    receivers.add(access.getTargetOwner().getName());
    if (access instanceof JavaMethodReference) {
      receivers.addAll(
          CallSites.receiversOf(
              javaClass,
              access.getOrigin().getName(),
              new CallSites.Target(access.getTargetOwner().getName(), access.getName())));
    }
    return receivers;
  }

  private static boolean resolvesWithoutTenant(String repository, String method) {
    if (BARE_METHODS.contains(method)) {
      return true;
    }
    if (!method.startsWith(DECLARED_PREFIX) || method.contains("Tenant")) {
      return false;
    }
    List<Method> declared =
        Arrays.stream(load(repository).getMethods())
            .filter(candidate -> candidate.getName().equals(method))
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
