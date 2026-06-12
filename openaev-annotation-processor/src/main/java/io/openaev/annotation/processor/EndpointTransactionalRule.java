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
import javax.tools.Diagnostic;

/**
 * Rule 1: Every REST endpoint ({@code @PostMapping}, {@code @GetMapping}, etc.) must be annotated
 * with {@code @Transactional}.
 *
 * <pre>
 *   -Aio.openaev.annotation.processor.EndpointTransactionalRule=true   (default: true)
 * </pre>
 */
@SupportedAnnotationTypes({
  "org.springframework.web.bind.annotation.PostMapping",
  "org.springframework.web.bind.annotation.GetMapping",
  "org.springframework.web.bind.annotation.PutMapping",
  "org.springframework.web.bind.annotation.DeleteMapping",
  "org.springframework.web.bind.annotation.PatchMapping"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class EndpointTransactionalRule extends AbstractTransactionalRule {

  public EndpointTransactionalRule() {
    super(true);
  }

  private static final Set<String> TRANSACTIONAL_ANNOTATIONS =
      Set.of(
          "org.springframework.transaction.annotation.Transactional",
          "jakarta.transaction.Transactional");

  @Override
  protected void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        if (element.getKind() != ElementKind.METHOD) {
          continue;
        }
        ExecutableElement method = (ExecutableElement) element;
        boolean hasTransactional =
            method.getAnnotationMirrors().stream()
                .anyMatch(
                    am -> {
                      String name =
                          ((TypeElement) am.getAnnotationType().asElement())
                              .getQualifiedName()
                              .toString();
                      return TRANSACTIONAL_ANNOTATIONS.contains(name);
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
    }
  }
}
