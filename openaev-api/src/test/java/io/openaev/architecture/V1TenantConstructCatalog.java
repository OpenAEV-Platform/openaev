package io.openaev.architecture;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the sites of the multi-tenancy v1 constructs in a set of classes:
 *
 * <ul>
 *   <li>a call to a method of {@code TenantContext} ({@code setCurrentTenant}, {@code
 *       getCurrentTenant}, {@code hasCurrentTenant}, {@code clearCurrentTenant}, and any method it
 *       gains), method references included;
 *   <li>a call to a method of {@code AmbientTenantBridge}, method references included;
 *   <li>an {@code @EntityListeners} naming a tenant listener, which is a listener whose own code,
 *       or the code of a superclass among the scanned classes, calls one of the two classes above;
 *   <li>a SpEL expression of a method annotation (the {@code value} or the {@code countQuery} of a
 *       {@code @Query}, or any other string attribute) that names {@code tenantContext} or {@code
 *       currentTenant}, read from the compiled annotation, so a constant concatenated into the
 *       query is seen.
 * </ul>
 *
 * <p>A site is {@code fully.qualified.Class#method} for a call or an annotated method, and the
 * entity class for an {@code @EntityListeners}. The calls of a lambda belong to the method that
 * creates it, overloads of a method are one site, and the count of a site is the number of source
 * lines holding a call or a method reference to that construct, or the number of SpEL expressions
 * its annotations hold, or 1 for a listener. Two calls to one construct on one source line count
 * once. The class of a construct is not a site of its own construct: the bridge calling itself, or
 * {@code TenantContext} reading its own thread-local, go with the class.
 *
 * <p>A method reference names the type that declares the method, and both classes declare every
 * method they are called on, so the declaring type is the receiver and no class file needs to be
 * read for it. What the scan cannot see, so a reviewer reads it: a call made by reflection, a
 * listener that reaches the ambient tenant through a helper of its own rather than by calling one
 * of the two classes, a SpEL expression built at run time, and a query written in an XML or
 * properties file.
 */
final class V1TenantConstructCatalog {

  static final String TENANT_CONTEXT = "io.openaev.context.TenantContext";
  static final String AMBIENT_TENANT_BRIDGE = "io.openaev.context.AmbientTenantBridge";
  static final Set<String> CALLED_CLASSES = Set.of(TENANT_CONTEXT, AMBIENT_TENANT_BRIDGE);

  private static final String ENTITY_LISTENERS = "jakarta.persistence.EntityListeners";

  /** A SpEL expression {@code #{...}} that reads the tenant of the v1 extension. */
  private static final Pattern TENANT_SPEL =
      Pattern.compile("#\\{[^}]*\\b(?:tenantContext|currentTenant)\\b[^}]*}");

  private V1TenantConstructCatalog() {}

  /** One site of one construct: where it is, and what it is. */
  record Site(String location, String construct) implements Comparable<Site> {

    @Override
    public int compareTo(Site other) {
      int byLocation = location.compareTo(other.location);
      return byLocation != 0 ? byLocation : construct.compareTo(other.construct);
    }

    @Override
    public String toString() {
      return location + " | " + construct;
    }
  }

  /** Every site of {@code classes}, with its count. */
  static Map<Site, Integer> sites(JavaClasses classes) {
    Map<Site, Integer> sites = new TreeMap<>();
    for (JavaClass javaClass : classes) {
      calls(javaClass, sites);
      listeners(javaClass, classes, sites);
      queries(javaClass, sites);
    }
    return sites;
  }

  /**
   * The calls and method references the class makes on the two v1 classes, counted by source line:
   * javac copies a {@code finally} block into every exit of its {@code try}, so one {@code
   * clearCurrentTenant()} written in a {@code finally} is two or three calls in the class file and
   * one line in the source. An access with no line number counts on its own.
   */
  private static void calls(JavaClass javaClass, Map<Site, Integer> sites) {
    List<JavaCodeUnitAccess<?>> accesses = new ArrayList<>(javaClass.getMethodCallsFromSelf());
    accesses.addAll(javaClass.getMethodReferencesFromSelf());
    Map<Site, Set<Integer>> lines = new TreeMap<>();
    int unnumbered = 0;
    for (JavaCodeUnitAccess<?> access : accesses) {
      String owner = access.getTargetOwner().getName();
      if (!CALLED_CLASSES.contains(owner) || belongsTo(javaClass.getName(), owner)) {
        continue;
      }
      int line = access.getLineNumber() > 0 ? access.getLineNumber() : --unnumbered;
      lines
          .computeIfAbsent(
              new Site(
                  javaClass.getName() + "#" + access.getOrigin().getName(),
                  simpleName(owner) + "." + access.getName()),
              site -> new HashSet<>())
          .add(line);
    }
    lines.forEach((site, numbers) -> sites.merge(site, numbers.size(), Integer::sum));
  }

  /** The tenant listeners the class names in its {@code @EntityListeners}. */
  private static void listeners(
      JavaClass javaClass, JavaClasses classes, Map<Site, Integer> sites) {
    Optional<JavaAnnotation<JavaClass>> annotation =
        javaClass.tryGetAnnotationOfType(ENTITY_LISTENERS);
    if (annotation.isEmpty()) {
      return;
    }
    Object value = annotation.get().get("value").orElse(new JavaClass[0]);
    for (JavaClass listener : (JavaClass[]) value) {
      if (isTenantListener(listener, classes)) {
        sites.put(
            new Site(
                javaClass.getName(), "@EntityListeners(" + simpleName(listener.getName()) + ")"),
            1);
      }
    }
  }

  /**
   * True when the listener, or a superclass of it among the scanned classes, calls one of the two
   * v1 classes. A listener the scan did not import has no code to read and is not one.
   */
  static boolean isTenantListener(JavaClass listener, JavaClasses classes) {
    Optional<JavaClass> current =
        classes.contain(listener.getName())
            ? Optional.of(classes.get(listener.getName()))
            : Optional.empty();
    while (current.isPresent()) {
      JavaClass type = current.get();
      boolean callsTheAmbientTenant =
          type.getMethodCallsFromSelf().stream()
                  .anyMatch(call -> CALLED_CLASSES.contains(call.getTargetOwner().getName()))
              || type.getMethodReferencesFromSelf().stream()
                  .anyMatch(ref -> CALLED_CLASSES.contains(ref.getTargetOwner().getName()));
      if (callsTheAmbientTenant) {
        return true;
      }
      current =
          type.getRawSuperclass()
              .filter(parent -> classes.contain(parent.getName()))
              .map(parent -> classes.get(parent.getName()));
    }
    return false;
  }

  /**
   * The SpEL expressions of the method annotations of the class that read the v1 tenant. A bridge
   * method is left out: javac copies the annotations of a method that overrides a generic one
   * ({@code deleteById(String)} over {@code CrudRepository.deleteById(ID)}) onto the bridge it
   * generates, which would count one query twice.
   */
  private static void queries(JavaClass javaClass, Map<Site, Integer> sites) {
    for (JavaMethod method : javaClass.getMethods()) {
      if (method.getModifiers().contains(JavaModifier.BRIDGE)
          || method.getModifiers().contains(JavaModifier.SYNTHETIC)) {
        continue;
      }
      for (JavaAnnotation<JavaMethod> annotation : method.getAnnotations()) {
        int expressions = 0;
        for (Object value : annotation.getProperties().values()) {
          expressions += tenantExpressions(value);
        }
        if (expressions > 0) {
          sites.merge(
              new Site(
                  javaClass.getName() + "#" + method.getName(),
                  "@" + annotation.getRawType().getSimpleName() + "(#tenantContext)"),
              expressions,
              Integer::sum);
        }
      }
    }
  }

  private static int tenantExpressions(Object value) {
    if (value instanceof String text) {
      int found = 0;
      Matcher matcher = TENANT_SPEL.matcher(text);
      while (matcher.find()) {
        found++;
      }
      return found;
    }
    if (value instanceof Object[] values) {
      int found = 0;
      for (Object each : values) {
        found += tenantExpressions(each);
      }
      return found;
    }
    return 0;
  }

  /** True when {@code caller} is {@code owner} or one of its nested classes. */
  private static boolean belongsTo(String caller, String owner) {
    return caller.equals(owner) || caller.startsWith(owner + "$");
  }

  private static String simpleName(String name) {
    return name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1);
  }
}
