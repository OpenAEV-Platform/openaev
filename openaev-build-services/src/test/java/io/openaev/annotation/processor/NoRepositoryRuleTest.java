package io.openaev.annotation.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit test for {@link NoRepositoryRule}, run as an in-process compilation. The annotations the
 * rule forbids live in libraries this module does not depend on, so they are provided as in-memory
 * stub sources with matching fully-qualified names; the processor matches by FQN.
 */
@DisplayName("NoRepositoryRule")
class NoRepositoryRuleTest {

  private static final String RULE = NoRepositoryRule.class.getName();
  private static final String REPOSITORY_PACKAGE = "org.springframework.data.jpa.repository";

  @Nested
  @DisplayName("A repository-layer annotation in the module")
  class ForbiddenAnnotations {

    @ParameterizedTest(name = "@{0}")
    @ValueSource(strings = {"Query", "Modifying", "EntityGraph", "NativeQuery"})
    @DisplayName("given_repositoryAnnotationOnAMethod_should_failCompilation")
    void given_repositoryAnnotationOnAMethod_should_failCompilation(String annotation) {
      // Arrange
      JavaFileObject stub = stubIn(REPOSITORY_PACKAGE, annotation);

      // Act
      List<Diagnostic<? extends JavaFileObject>> diagnostics =
          compile(
              stub,
              source(
                  "io.openaev.test.SomeRepository",
                  "package io.openaev.test;",
                  "import " + REPOSITORY_PACKAGE + "." + annotation + ";",
                  "public interface SomeRepository {",
                  "  @" + annotation + " void touch();",
                  "}"));

      // Assert
      assertTrue(
          hasRuleError(diagnostics, annotation),
          "expected the rule's ERROR for @" + annotation + ", got: " + diagnostics);
    }

    @Test
    @DisplayName("given_springStereotypeRepository_should_failCompilation")
    void given_springStereotypeRepository_should_failCompilation() {
      // Arrange
      JavaFileObject stub = stubIn("org.springframework.stereotype", "Repository");

      // Act
      List<Diagnostic<? extends JavaFileObject>> diagnostics =
          compile(
              stub,
              source(
                  "io.openaev.test.AnyRepository",
                  "package io.openaev.test;",
                  "import org.springframework.stereotype.Repository;",
                  "@Repository",
                  "public class AnyRepository {}"));

      // Assert
      assertTrue(hasRuleError(diagnostics, "Repository"), "got: " + diagnostics);
    }
  }

  @Nested
  @DisplayName("A class without any repository-layer annotation")
  class Clean {

    @Test
    @DisplayName("given_noRepositoryAnnotation_should_compile")
    void given_noRepositoryAnnotation_should_compile() {
      // Act
      List<Diagnostic<? extends JavaFileObject>> diagnostics =
          compile(
              source(
                  "io.openaev.test.PlainService",
                  "package io.openaev.test;",
                  "public class PlainService {",
                  "  public String helper() { return \"x\"; }",
                  "}"));

      // Assert
      assertFalse(
          diagnostics.stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR),
          "got: " + diagnostics);
    }
  }

  // --- in-process compile harness (JDK only, no extra dependency) ---

  private static List<Diagnostic<? extends JavaFileObject>> compile(JavaFileObject... sources) {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("no system Java compiler available (a JDK is required)");
    }
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
      List<String> options = List.of("-proc:only", "-A" + RULE + "=true");
      JavaCompiler.CompilationTask task =
          compiler.getTask(null, fileManager, diagnostics, options, null, List.of(sources));
      task.setProcessors(List.of(new NoRepositoryRule()));
      task.call();
      return diagnostics.getDiagnostics();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** An error raised by this rule for the given annotation, not any error. */
  private static boolean hasRuleError(
      List<Diagnostic<? extends JavaFileObject>> diagnostics, String annotation) {
    return diagnostics.stream()
        .anyMatch(
            d ->
                d.getKind() == Diagnostic.Kind.ERROR
                    && d.getMessage(Locale.ROOT) != null
                    && d.getMessage(Locale.ROOT).contains("uses @" + annotation + ","));
  }

  private static JavaFileObject stubIn(String packageName, String simpleName) {
    return source(
        packageName + "." + simpleName,
        "package " + packageName + ";",
        "public @interface " + simpleName + " {}");
  }

  private static JavaFileObject source(String fqn, String... lines) {
    String code = String.join("\n", lines);
    URI uri = URI.create("string:///" + fqn.replace('.', '/') + ".java");
    return new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
      }
    };
  }
}
