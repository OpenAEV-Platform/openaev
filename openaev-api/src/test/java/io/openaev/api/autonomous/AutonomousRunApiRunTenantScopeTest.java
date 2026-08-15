package io.openaev.api.autonomous;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.config.RunTenantScope;
import io.openaev.context.TxCtx;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * Pins WHICH {@link AutonomousRunApi} handlers derive their tenant scope from the parent run
 * ({@link RunTenantScope}) and which stay caller-scoped. The annotation trades the caller's tenant
 * boundary for the run's own on the legacy callback route, so its placement is a security decision,
 * not plumbing: adding it to an operator endpoint would let any authenticated EE caller act on
 * another tenant's run through that endpoint, and dropping it from a callback would break live
 * orchestration again (#7450). {@code TenantScopedEntrypointsTxCtxArchTest} already forces every
 * handler reaching the tenant-active {@code autonomous_*} tables to carry a {@code TxCtx}; this
 * test forces the NEXT decision - every {@code TxCtx} handler on this controller must be explicitly
 * classified as an orchestrator callback (run-scoped) or an operator endpoint (caller-scoped), so a
 * twelfth callback added later fails here instead of silently defaulting to a scope nobody chose.
 *
 * <p>Each run-scoped handler must also name its run through the {@code {runId}} path variable: the
 * resolver derives the scope from exactly that variable and fails closed (404) when it is absent,
 * so a renamed variable would brick the callback silently in production but loudly here.
 */
@DisplayName("@RunTenantScope stays pinned to the eleven orchestrator callbacks")
class AutonomousRunApiRunTenantScopeTest {

  /** The orchestrator callbacks: scoped from the parent run on the legacy non-prefixed route. */
  private static final Set<String> RUN_SCOPED_CALLBACKS =
      Set.of(
          "getScope",
          "setScope",
          "recordEvent",
          "updateStatus",
          "consumeDirectives",
          "appendAttackPathStep",
          "updateAttackPathStep",
          "attackPathState",
          "evaluateAttackPath",
          "promoteFindingToAsset",
          "ensureTargetTeam");

  /** The operator endpoints: tenant-isolated against the caller's own scope, never the run's. */
  private static final Set<String> CALLER_SCOPED_HANDLERS =
      Set.of(
          "create",
          "launchFromScenario",
          "planScenario",
          "list",
          "get",
          "getBySimulation",
          "getByScenario",
          "start",
          "pause",
          "resume",
          "cancel",
          "restart",
          "promote",
          "convertToManual",
          "timeline",
          "directives",
          "addDirective",
          "updateConfiguration");

  @Test
  @DisplayName("every TxCtx handler is explicitly classified, and the classification is exact")
  void everyTxCtxHandlerIsExplicitlyClassified() {
    Set<String> runScoped = new TreeSet<>();
    Set<String> callerScoped = new TreeSet<>();
    for (Method handler : handlers()) {
      if (!hasTxCtxParameter(handler)) {
        continue;
      }
      if (hasRunTenantScopedParameter(handler)) {
        runScoped.add(handler.getName());
      } else {
        callerScoped.add(handler.getName());
      }
    }

    assertThat(runScoped)
        .as(
            "handlers deriving their scope from the parent run - adding one widens what any"
                + " authenticated EE caller can do to another tenant's run, removing one breaks"
                + " orchestration; classify the change deliberately here")
        .containsExactlyInAnyOrderElementsOf(RUN_SCOPED_CALLBACKS);
    assertThat(callerScoped)
        .as(
            "caller-scoped TxCtx handlers - a new handler must be pinned either here or in the"
                + " run-scoped list, so its tenant boundary is a decision rather than a default")
        .containsExactlyInAnyOrderElementsOf(CALLER_SCOPED_HANDLERS);
  }

  @Test
  @DisplayName("every run-scoped callback names its run through the {runId} path variable")
  void everyRunScopedCallbackCarriesTheRunIdPathVariable() {
    for (Method handler : handlers()) {
      if (!hasRunTenantScopedParameter(handler)) {
        continue;
      }
      assertThat(mappingPath(handler))
          .as(
              "handler '%s' derives its scope from the {runId} path variable; renaming it would"
                  + " fail every callback closed (404) in production",
              handler.getName())
          .contains("{runId}");
    }
  }

  @Test
  @DisplayName("the annotation only ever sits on a TxCtx parameter (anywhere else it is dead)")
  void annotationOnlyAnnotatesTxCtxParameters() {
    for (Method handler : handlers()) {
      for (Parameter parameter : handler.getParameters()) {
        if (parameter.isAnnotationPresent(RunTenantScope.class)) {
          assertThat(parameter.getType())
              .as(
                  "@RunTenantScope on '%s' must annotate the TxCtx parameter; the resolver only"
                      + " honours it there",
                  handler.getName())
              .isEqualTo(TxCtx.class);
        }
      }
    }
  }

  private static List<Method> handlers() {
    return Arrays.stream(AutonomousRunApi.class.getDeclaredMethods())
        .filter(AutonomousRunApiRunTenantScopeTest::isHandler)
        .toList();
  }

  private static boolean isHandler(Method method) {
    return method.isAnnotationPresent(GetMapping.class)
        || method.isAnnotationPresent(PostMapping.class)
        || method.isAnnotationPresent(PutMapping.class);
  }

  private static boolean hasTxCtxParameter(Method method) {
    return Arrays.stream(method.getParameterTypes()).anyMatch(TxCtx.class::equals);
  }

  private static boolean hasRunTenantScopedParameter(Method method) {
    return Arrays.stream(method.getParameters())
        .anyMatch(parameter -> parameter.isAnnotationPresent(RunTenantScope.class));
  }

  private static String mappingPath(Method method) {
    GetMapping get = method.getAnnotation(GetMapping.class);
    if (get != null) {
      return first(get.value());
    }
    PostMapping post = method.getAnnotation(PostMapping.class);
    if (post != null) {
      return first(post.value());
    }
    PutMapping put = method.getAnnotation(PutMapping.class);
    if (put != null) {
      return first(put.value());
    }
    return "";
  }

  private static String first(String[] values) {
    return values.length == 0 ? "" : values[0];
  }
}
