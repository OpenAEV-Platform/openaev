package io.openaev.annotation.processor;

import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Default-secure rule (#7726): every REST endpoint ({@code @GetMapping}, {@code @PostMapping}, ...)
 * must either take a {@code io.openaev.context.TxCtx} parameter or be marked as deliberately out of
 * tenant scope ({@code @NoTenantScope} / {@code @PlatformScoped}, on the method or its controller).
 * A new endpoint that declares neither cannot compile, so the tenant-scope decision cannot be
 * silently forgotten.
 *
 * <p>Shipped DISABLED ({@code super(false)}). It is enabled per module via {@code
 * -Aio.openaev.annotation.processor.EndpointTxScopeRule=true}; the enable flip on {@code
 * openaev-api} is done once the controller sweep is complete (see spec-4).
 *
 * <p>Everything is matched by fully-qualified name, so the processor needs no compile dependency on
 * the modules that define these types.
 */
@SupportedAnnotationTypes({
  "org.springframework.web.bind.annotation.PostMapping",
  "org.springframework.web.bind.annotation.GetMapping",
  "org.springframework.web.bind.annotation.PutMapping",
  "org.springframework.web.bind.annotation.DeleteMapping",
  "org.springframework.web.bind.annotation.PatchMapping"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class EndpointTxScopeRule extends AbstractTransactionalRule {

  private static final String TX_CTX = "io.openaev.context.TxCtx";
  private static final Set<String> MARKERS =
      Set.of("io.openaev.annotation.NoTenantScope", "io.openaev.annotation.PlatformScoped");

  public EndpointTxScopeRule() {
    super(false);
  }

  @Override
  protected void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        if (element.getKind() != ElementKind.METHOD) {
          continue;
        }
        ExecutableElement method = (ExecutableElement) element;
        if (isScoped(method)) {
          continue;
        }
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                String.format(
                    "REST endpoint '%s' must take a TxCtx parameter or be marked @NoTenantScope /"
                        + " @PlatformScoped (default-secure tenant rule, #7726)",
                    method.getSimpleName()),
                method);
      }
    }
  }

  /** Scoped iff it carries a {@code TxCtx} parameter, or the method or its controller is marked. */
  private static boolean isScoped(ExecutableElement method) {
    return hasTxCtxParameter(method) || isMarked(method) || isMarked(method.getEnclosingElement());
  }

  private static boolean hasTxCtxParameter(ExecutableElement method) {
    for (VariableElement parameter : method.getParameters()) {
      if (isType(parameter.asType(), TX_CTX)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isType(TypeMirror type, String qualifiedName) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }
    Element element = ((DeclaredType) type).asElement();
    return element instanceof TypeElement typeElement
        && typeElement.getQualifiedName().contentEquals(qualifiedName);
  }

  private static boolean isMarked(Element element) {
    return element.getAnnotationMirrors().stream()
        .map(
            mirror ->
                ((TypeElement) mirror.getAnnotationType().asElement())
                    .getQualifiedName()
                    .toString())
        .anyMatch(MARKERS::contains);
  }
}
