package io.openaev.architecture.fixture;

import java.util.Map;
import org.springframework.data.spel.spi.EvaluationContextExtension;

/**
 * The shapes the SpEL extension guard must find, and the code it must leave alone. Nothing here
 * runs: the scan reads the class files only.
 */
public final class TenantSpelExtensionFixtures {

  private TenantSpelExtensionFixtures() {}

  public static class ProvidesAnExtension implements EvaluationContextExtension {
    @Override
    public String getExtensionId() {
      return "ambient";
    }

    @Override
    public Map<String, Object> getProperties() {
      return Map.of();
    }
  }

  public abstract static class ExtendsAnExtensionProvider extends ProvidesAnExtension {}

  public static class ProvidesNoExtension {
    public String id() {
      return "plain";
    }
  }
}
