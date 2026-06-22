package io.openaev.annotation.processor;

import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * Forbids repository-layer annotations: {@code @Repository}, {@code @Query}, {@code @Modifying},
 * and {@code @EntityGraph}. These must live in {@code openaev-model}, not in the API layer.
 *
 * <p>When this rule is active, {@link NoSpelTenantRule} is redundant since {@code @Query} itself is
 * already forbidden.
 *
 * <pre>
 *   -Aio.openaev.annotation.processor.NoRepositoryRule=true   (default: false)
 * </pre>
 */
@SupportedAnnotationTypes({
  "org.springframework.stereotype.Repository",
  "org.springframework.data.jpa.repository.Query",
  "org.springframework.data.jpa.repository.Modifying",
  "org.springframework.data.jpa.repository.EntityGraph"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class NoRepositoryRule extends AbstractTransactionalRule {

  public NoRepositoryRule() {
    super(false);
  }

  @Override
  protected void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      String simpleName = annotation.getSimpleName().toString();
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                String.format(
                    "'%s' uses @%s, which is forbidden in this module."
                        + " Repository layer annotations must be declared in openaev-model.",
                    element.getSimpleName(), simpleName),
                element);
      }
    }
  }
}
