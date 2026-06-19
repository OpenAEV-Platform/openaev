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
 * Rule 4: {@code jakarta.transaction.Transactional} is forbidden. Use {@code
 * org.springframework.transaction.annotation.Transactional} instead.
 *
 * <pre>
 *   -Aio.openaev.annotation.processor.NoJakartaTransactionalRule=true   (default: false)
 * </pre>
 */
@SupportedAnnotationTypes("jakarta.transaction.Transactional")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class NoJakartaTransactionalRule extends AbstractTransactionalRule {

  public NoJakartaTransactionalRule() {
    super(false);
  }

  @Override
  protected void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                String.format(
                    "'%s' uses jakarta.transaction.Transactional."
                        + " Use org.springframework.transaction.annotation.Transactional instead.",
                    element.getSimpleName()),
                element);
      }
    }
  }
}
