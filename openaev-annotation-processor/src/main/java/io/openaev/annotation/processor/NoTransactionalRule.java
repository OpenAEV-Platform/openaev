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
 * Rule 5: All {@code @Transactional} annotations (Spring or Jakarta) are forbidden. Enable this on
 * modules that must not declare transactions (e.g. pure model/repository modules where transaction
 * boundaries belong to the service layer).
 *
 * <pre>
 *   -Aio.openaev.annotation.processor.NoTransactionalRule=true   (default: false)
 * </pre>
 */
@SupportedAnnotationTypes({
  "org.springframework.transaction.annotation.Transactional",
  "jakarta.transaction.Transactional"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class NoTransactionalRule extends AbstractTransactionalRule {

  public NoTransactionalRule() {
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
                    "'%s' uses @Transactional, which is forbidden in this module."
                        + " Transaction boundaries must be declared in the service layer.",
                    element.getSimpleName()),
                element);
      }
    }
  }
}
