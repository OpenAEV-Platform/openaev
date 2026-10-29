package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

@DisplayName("Lazy-after-commit probe context customizer")
class LazyAfterCommitProbeContextCustomizerFactoryTest {

  private static final String ACTIVE_TABLES = "openaev.tenant.active-tables";

  @Nested
  @DisplayName("Arming the production activation list")
  class ProductionTables {

    @Test
    @DisplayName(
        "given a class arming its own list, when production tables are forced, then production's list wins")
    void given_classArmingItsOwnList_should_runWithProductionList() {
      // -- Arrange --
      GenericApplicationContext context = contextArming("challenges");

      // -- Act --
      new LazyAfterCommitProbeContextCustomizerFactory.ProbeCustomizer(true)
          .customizeContext(context, null);

      // -- Assert --
      assertThat(context.getEnvironment().getProperty(ACTIVE_TABLES))
          .isEqualTo(String.join(",", ProductionActiveTables.names()));
    }

    @Test
    @DisplayName(
        "given a class arming its own list, when production tables are not forced, then its list is kept")
    void given_classArmingItsOwnList_should_keepItWhenNotForced() {
      // -- Arrange --
      GenericApplicationContext context = contextArming("challenges");

      // -- Act --
      new LazyAfterCommitProbeContextCustomizerFactory.ProbeCustomizer(false)
          .customizeContext(context, null);

      // -- Assert --
      assertThat(context.getEnvironment().getProperty(ACTIVE_TABLES)).isEqualTo("challenges");
    }
  }

  private static GenericApplicationContext contextArming(String tables) {
    GenericApplicationContext context = new GenericApplicationContext();
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(new MapPropertySource("Inlined Test Properties", Map.of(ACTIVE_TABLES, tables)));
    return context;
  }
}
