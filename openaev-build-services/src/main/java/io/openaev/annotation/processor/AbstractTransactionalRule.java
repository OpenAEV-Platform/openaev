package io.openaev.annotation.processor;

import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * Base class for transactional rules. Each subclass is enabled or disabled via a compiler option
 * named after its own fully-qualified class name, e.g.:
 *
 * <pre>
 *   -Aio.openaev.annotation.processor.EndpointTransactionalRule=false
 * </pre>
 */
abstract class AbstractTransactionalRule extends AbstractProcessor {

  private final boolean enabledByDefault;

  protected AbstractTransactionalRule(boolean enabledByDefault) {
    this.enabledByDefault = enabledByDefault;
  }

  @Override
  public Set<String> getSupportedOptions() {
    return Set.of(getClass().getName());
  }

  @Override
  public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (isEnabled()) {
      doProcess(annotations, roundEnv);
    }
    return false;
  }

  private boolean isEnabled() {
    String value = processingEnv.getOptions().get(getClass().getName());
    return value == null ? enabledByDefault : Boolean.parseBoolean(value);
  }

  protected abstract void doProcess(
      Set<? extends TypeElement> annotations, RoundEnvironment roundEnv);
}
