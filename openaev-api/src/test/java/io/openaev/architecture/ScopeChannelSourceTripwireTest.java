package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The scope channel itself is not guarded by the inspector: a {@code SELECT set_config(...)}
 * touches no tenant table, so any native query could silently widen (or narrow) the transaction's
 * tenant scope. Until the inspector learns to reject unsanctioned {@code set_config} calls on the
 * scope GUC (follow-up on the #6398 arbitration list), this tripwire pins the inventory: one
 * production class sets {@code app.current_tenants} and records the tenant that scope pins, and
 * only the HTTP aspect and the background primitive call it. Any new toucher must be a deliberate,
 * reviewed decision.
 */
@DisplayName("Only the aspect and the primitive may touch the tenant-scope GUC")
class ScopeChannelSourceTripwireTest {

  private static final String SCOPE_SET_CALL = "SELECT set_config('app.current_tenants'";
  private static final String SCOPE_WRITE_CALL = "TransactionTenantScope.write(";
  private static final Set<String> SCOPE_SETTERS = Set.of("TransactionTenantScope.java");
  private static final Set<String> SCOPE_WRITERS =
      Set.of("TenantScopeTransactionAspect.java", "TenantScopedTransaction.java");

  /** Every production module's sources, derived so a new module cannot silently escape the scan. */
  private static List<Path> productionRoots() throws IOException {
    // Surefire runs with the module directory as working dir; ".." is the repo root.
    try (Stream<Path> modules = Files.list(Path.of("..").toAbsolutePath().normalize())) {
      return modules
          .filter(module -> module.getFileName().toString().startsWith("openaev-"))
          .map(module -> module.resolve("src/main/java"))
          .filter(Files::isDirectory)
          .toList();
    }
  }

  /** The names of the production source files that contain {@code text}. */
  private static Set<String> productionFilesContaining(String text) throws IOException {
    List<Path> roots = productionRoots();
    // Pin the two known scope-channel homes by NAME instead of counting modules: a renamed or
    // restructured module cannot silently drop out of the scan, and retiring a deprecated module
    // does not falsely fail this tripwire.
    assertTrue(
        roots.stream().anyMatch(root -> root.endsWith(Path.of("openaev-model/src/main/java"))),
        "openaev-model production sources must be scanned, got " + roots);
    assertTrue(
        roots.stream().anyMatch(root -> root.endsWith(Path.of("openaev-api/src/main/java"))),
        "openaev-api production sources must be scanned, got " + roots);
    Set<String> found = new TreeSet<>();
    for (Path root : roots) {
      try (Stream<Path> sources = Files.walk(root)) {
        for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
          if (Files.readString(source).contains(text)) {
            found.add(source.getFileName().toString());
          }
        }
      }
    }
    return found;
  }

  @Test
  @DisplayName("given the production sources, should set app.current_tenants in one class only")
  void given_productionSources_should_setTheScopeGucInOneClassOnly() throws IOException {
    // Act
    Set<String> setters = productionFilesContaining(SCOPE_SET_CALL);

    // Assert
    assertEquals(SCOPE_SETTERS, setters, "the classes that set the tenant-scope GUC");
  }

  @Test
  @DisplayName("given the production sources, should write the scope from the aspect and primitive")
  void given_productionSources_should_writeTheScopeFromTheAspectAndThePrimitiveOnly()
      throws IOException {
    // Act
    Set<String> writers = productionFilesContaining(SCOPE_WRITE_CALL);

    // Assert
    assertEquals(SCOPE_WRITERS, writers, "the classes that write the tenant scope");
  }
}
