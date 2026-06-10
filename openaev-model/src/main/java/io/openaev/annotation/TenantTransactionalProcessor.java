package io.openaev.annotation;

import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * Compile-time annotation processor that enforces architectural rules for tenant context:
 *
 * <ol>
 *   <li><b>Rule 1</b>: Every REST endpoint ({@code @PostMapping}, {@code @GetMapping}, etc.) must
 *       be annotated with {@code @Transactional}.
 *   <li><b>Rule 2</b>: Every {@code @Transactional} method that is <em>not</em> a REST endpoint
 *       must declare a {@code TxCtx} parameter. REST endpoints are exempt because the AOP aspect
 *       resolves the tenant automatically from the HTTP request.
 *   <li><b>Rule 3</b>: {@code @Query} annotations must not contain SpEL tenant expressions like
 *       {@code #{#tenantContext.currentTenant}}. Tenant filtering is now handled by the AOP aspect
 *       and the {@code TenantStatementInspector}.
 * </ol>
 *
 * <p>All rules can be toggled independently via compiler options:
 *
 * <pre>
 *   -Atenant.rule.endpoint.transactional=true       (Rule 1, default: false)
 *   -Atenant.rule.transactional.txctx=true           (Rule 2, default: false)
 *   -Atenant.rule.no.spel.tenant=true                (Rule 3, default: false)
 * </pre>
 */
@SupportedAnnotationTypes({
  "org.springframework.web.bind.annotation.PostMapping",
  "org.springframework.web.bind.annotation.GetMapping",
  "org.springframework.web.bind.annotation.PutMapping",
  "org.springframework.web.bind.annotation.DeleteMapping",
  "org.springframework.web.bind.annotation.PatchMapping",
  "org.springframework.transaction.annotation.Transactional",
  "jakarta.transaction.Transactional",
  "org.springframework.data.jpa.repository.Query"
})
@SupportedOptions({
  TenantTransactionalProcessor.OPT_RULE_ENDPOINT_TRANSACTIONAL,
  TenantTransactionalProcessor.OPT_RULE_TRANSACTIONAL_TXCTX,
  TenantTransactionalProcessor.OPT_RULE_NO_SPEL_TENANT
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class TenantTransactionalProcessor extends AbstractProcessor {

  /** Option to enable/disable Rule 1: REST endpoints must have @Transactional. */
  static final String OPT_RULE_ENDPOINT_TRANSACTIONAL = "tenant.rule.endpoint.transactional";

  /** Option to enable/disable Rule 2: Non-endpoint @Transactional methods must have TxCtx. */
  static final String OPT_RULE_TRANSACTIONAL_TXCTX = "tenant.rule.transactional.txctx";

  /** Option to enable/disable Rule 3: No SpEL tenant expressions in @Query. */
  static final String OPT_RULE_NO_SPEL_TENANT = "tenant.rule.no.spel.tenant";

  private static final Set<String> HTTP_MAPPING_ANNOTATIONS =
      Set.of(
          "org.springframework.web.bind.annotation.PostMapping",
          "org.springframework.web.bind.annotation.GetMapping",
          "org.springframework.web.bind.annotation.PutMapping",
          "org.springframework.web.bind.annotation.DeleteMapping",
          "org.springframework.web.bind.annotation.PatchMapping");

  private static final Set<String> TRANSACTIONAL_ANNOTATIONS =
      Set.of(
          "org.springframework.transaction.annotation.Transactional",
          "jakarta.transaction.Transactional");

  private static final String QUERY_ANNOTATION = "org.springframework.data.jpa.repository.Query";

  private static final String TX_CTX_TYPE = "io.openaev.context.TxCtx";

  /** Patterns that indicate legacy manual tenant filtering in @Query values. */
  private static final Set<String> FORBIDDEN_SPEL_PATTERNS =
      Set.of("tenantContext", "currentTenant", "tenantId");

  private boolean isEnabled(String option, boolean defaultValue) {
    String value = processingEnv.getOptions().get(option);
    return value == null ? defaultValue : Boolean.parseBoolean(value);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    boolean rule1 = isEnabled(OPT_RULE_ENDPOINT_TRANSACTIONAL, false);
    boolean rule2 = isEnabled(OPT_RULE_TRANSACTIONAL_TXCTX, false);
    boolean rule3 = isEnabled(OPT_RULE_NO_SPEL_TENANT, false);

    for (TypeElement annotation : annotations) {
      String annotationName = annotation.getQualifiedName().toString();

      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        if (element.getKind() != ElementKind.METHOD) {
          continue;
        }
        ExecutableElement method = (ExecutableElement) element;

        // Rule 1: HTTP mapping → must have @Transactional
        if (rule1 && HTTP_MAPPING_ANNOTATIONS.contains(annotationName)) {
          checkHttpMappingHasTransactional(method);
        }

        // Rule 2: @Transactional on non-endpoint methods → must have TxCtx parameter
        if (rule2 && TRANSACTIONAL_ANNOTATIONS.contains(annotationName)) {
          if (!hasHttpMappingAnnotation(method)) {
            checkTransactionalHasTxCtx(method);
          }
        }

        // Rule 3: @Query must not contain SpEL tenant expressions
        if (rule3 && QUERY_ANNOTATION.equals(annotationName)) {
          checkQueryHasNoSpelTenant(method);
        }
      }
    }
    // Don't claim these annotations — let Spring process them too
    return false;
  }

  private void checkHttpMappingHasTransactional(ExecutableElement method) {
    boolean hasTransactional =
        method.getAnnotationMirrors().stream()
            .anyMatch(
                am -> {
                  Element annotationElement = am.getAnnotationType().asElement();
                  String qualifiedName =
                      ((TypeElement) annotationElement).getQualifiedName().toString();
                  return TRANSACTIONAL_ANNOTATIONS.contains(qualifiedName);
                });
    if (!hasTransactional) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              String.format(
                  "REST endpoint '%s' must be annotated with @Transactional",
                  method.getSimpleName()),
              method);
    }
  }

  private void checkTransactionalHasTxCtx(ExecutableElement method) {
    boolean hasTxCtx =
        method.getParameters().stream().anyMatch(p -> p.asType().toString().equals(TX_CTX_TYPE));
    if (!hasTxCtx) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              String.format(
                  "@Transactional method '%s' must declare a TxCtx parameter"
                      + " (not a REST endpoint — tenant context cannot be resolved from HTTP)",
                  method.getSimpleName()),
              method);
    }
  }

  private void checkQueryHasNoSpelTenant(ExecutableElement method) {
    method.getAnnotationMirrors().stream()
        .filter(
            am -> {
              Element annotationElement = am.getAnnotationType().asElement();
              return ((TypeElement) annotationElement)
                  .getQualifiedName()
                  .toString()
                  .equals(QUERY_ANNOTATION);
            })
        .flatMap(am -> am.getElementValues().entrySet().stream())
        .filter(entry -> "value".equals(entry.getKey().getSimpleName().toString()))
        .forEach(
            entry -> {
              String queryValue = entry.getValue().getValue().toString();
              for (String pattern : FORBIDDEN_SPEL_PATTERNS) {
                if (queryValue.contains(pattern)) {
                  processingEnv
                      .getMessager()
                      .printMessage(
                          Diagnostic.Kind.ERROR,
                          String.format(
                              "@Query in '%s' contains legacy SpEL tenant expression '%s'."
                                  + " Tenant filtering is now handled by TenantStatementInspector."
                                  + " Remove manual tenant filtering from the query.",
                              method.getSimpleName(), pattern),
                          method);
                  return; // one error per method is enough
                }
              }
            });
  }

  /** Returns true if the method carries any Spring MVC mapping annotation. */
  private boolean hasHttpMappingAnnotation(ExecutableElement method) {
    return method.getAnnotationMirrors().stream()
        .anyMatch(
            am -> {
              Element annotationElement = am.getAnnotationType().asElement();
              String qualifiedName =
                  ((TypeElement) annotationElement).getQualifiedName().toString();
              return HTTP_MAPPING_ANNOTATIONS.contains(qualifiedName);
            });
  }
}
