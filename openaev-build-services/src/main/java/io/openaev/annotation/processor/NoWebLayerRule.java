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
 * Forbids web layer annotations ({@code @RestController}, {@code @RequestMapping},
 * {@code @GetMapping}, etc.). Controllers must live in {@code openaev-api}, not in the model layer.
 *
 * <pre>
 *   -Aio.openaev.annotation.processor.NoWebLayerRule=true   (default: false)
 * </pre>
 */
@SupportedAnnotationTypes({
  "org.springframework.web.bind.annotation.RestController",
  "org.springframework.web.bind.annotation.RequestMapping",
  "org.springframework.web.bind.annotation.GetMapping",
  "org.springframework.web.bind.annotation.PostMapping",
  "org.springframework.web.bind.annotation.PutMapping",
  "org.springframework.web.bind.annotation.DeleteMapping",
  "org.springframework.web.bind.annotation.PatchMapping"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class NoWebLayerRule extends AbstractTransactionalRule {

  public NoWebLayerRule() {
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
                        + " Web layer annotations must be declared in openaev-api.",
                    element.getSimpleName(), simpleName),
                element);
      }
    }
  }
}
