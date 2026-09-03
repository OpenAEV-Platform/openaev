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
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link EndpointTxScopeRule}, run as an in-process compilation. The module is
 * upstream of the types it checks, so the referenced types (@GetMapping, TxCtx, the markers) are
 * provided as in-memory stub sources with matching fully-qualified names; the processor matches by
 * FQN, so a stub is indistinguishable from the real type for its purpose.
 */
class EndpointTxScopeRuleTest {

  private static final String RULE = EndpointTxScopeRule.class.getName();

  @Test
  @DisplayName("an endpoint with neither a TxCtx param nor a marker fails compilation")
  void unmarkedEndpointFailsCompilation() {
    List<Diagnostic<? extends JavaFileObject>> diagnostics =
        compile(
            stubGetMapping(),
            source(
                "io.openaev.test.UnmarkedController",
                "package io.openaev.test;",
                "import org.springframework.web.bind.annotation.GetMapping;",
                "public class UnmarkedController {",
                "  @GetMapping",
                "  public String list() { return \"x\"; }",
                "}"));

    assertTrue(
        hasRuleError(diagnostics),
        "expected the rule's ERROR for an endpoint with neither a TxCtx parameter nor an exclusion"
            + " marker, got: "
            + diagnostics);
  }

  @Test
  @DisplayName("an endpoint with a TxCtx parameter passes")
  void endpointWithTxCtxParameterPasses() {
    List<Diagnostic<? extends JavaFileObject>> diagnostics =
        compile(
            stubGetMapping(),
            stubTxCtx(),
            source(
                "io.openaev.test.ScopedController",
                "package io.openaev.test;",
                "import org.springframework.web.bind.annotation.GetMapping;",
                "import io.openaev.context.TxCtx;",
                "public class ScopedController {",
                "  @GetMapping",
                "  public String list(TxCtx ctx) { return \"x\"; }",
                "}"));

    assertFalse(
        hasError(diagnostics), "a TxCtx parameter must satisfy the rule, got: " + diagnostics);
  }

  @Test
  @DisplayName("an endpoint marked @NoTenantScope on the method passes")
  void methodMarkerPasses() {
    List<Diagnostic<? extends JavaFileObject>> diagnostics =
        compile(
            stubGetMapping(),
            stubMarker("NoTenantScope"),
            source(
                "io.openaev.test.PublicController",
                "package io.openaev.test;",
                "import org.springframework.web.bind.annotation.GetMapping;",
                "import io.openaev.annotation.NoTenantScope;",
                "public class PublicController {",
                "  @GetMapping @NoTenantScope",
                "  public String health() { return \"ok\"; }",
                "}"));

    assertFalse(
        hasError(diagnostics), "a method marker must satisfy the rule, got: " + diagnostics);
  }

  @Test
  @DisplayName("a controller marked @PlatformScoped covers its unmarked endpoints")
  void typeMarkerPasses() {
    List<Diagnostic<? extends JavaFileObject>> diagnostics =
        compile(
            stubGetMapping(),
            stubMarker("PlatformScoped"),
            source(
                "io.openaev.test.RolesController",
                "package io.openaev.test;",
                "import org.springframework.web.bind.annotation.GetMapping;",
                "import io.openaev.annotation.PlatformScoped;",
                "@PlatformScoped",
                "public class RolesController {",
                "  @GetMapping",
                "  public String roles() { return \"r\"; }",
                "}"));

    assertFalse(
        hasError(diagnostics), "a type-level marker must cover its methods, got: " + diagnostics);
  }

  @Test
  @DisplayName("a non-endpoint method is ignored")
  void nonEndpointMethodIgnored() {
    List<Diagnostic<? extends JavaFileObject>> diagnostics =
        compile(
            stubGetMapping(),
            source(
                "io.openaev.test.PlainService",
                "package io.openaev.test;",
                "public class PlainService {",
                "  public String helper() { return \"x\"; }",
                "}"));

    assertFalse(
        hasError(diagnostics), "a non-@*Mapping method must not be flagged, got: " + diagnostics);
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
      task.setProcessors(List.of(new EndpointTxScopeRule()));
      task.call();
      return diagnostics.getDiagnostics();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean hasError(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    return diagnostics.stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR);
  }

  /**
   * An error raised by this rule specifically (its message carries the issue ref), not any error.
   */
  private static boolean hasRuleError(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    return diagnostics.stream()
        .anyMatch(
            d ->
                d.getKind() == Diagnostic.Kind.ERROR
                    && d.getMessage(Locale.ROOT) != null
                    && d.getMessage(Locale.ROOT).contains("#7726"));
  }

  private static JavaFileObject stubGetMapping() {
    return source(
        "org.springframework.web.bind.annotation.GetMapping",
        "package org.springframework.web.bind.annotation;",
        "public @interface GetMapping {}");
  }

  private static JavaFileObject stubTxCtx() {
    return source(
        "io.openaev.context.TxCtx", "package io.openaev.context;", "public class TxCtx {}");
  }

  private static JavaFileObject stubMarker(String simpleName) {
    return source(
        "io.openaev.annotation." + simpleName,
        "package io.openaev.annotation;",
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
