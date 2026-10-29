package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Freezes {@code disableFilter("tenantFilter")} in production code behind a reasoned baseline
 * ({@code tenant-filter-disable-baseline.txt}) that only shrinks: a new call fails the build, every
 * remaining call carries a classified reason, a line whose call is gone must be removed, and the
 * number of lines is pinned by {@link #BASELINE_SIZE}, so neither growing nor shrinking the list
 * goes unnoticed. Why the call is frozen is on {@link
 * TenantBackgroundTransactionRules#noTenantFilterDisableOutsideBaseline}. {@link
 * TenantFilterDisableRulesTest} proves the rule fires.
 */
@DisplayName("disableFilter(\"tenantFilter\") in production stays inside its reasoned baseline")
class TenantFilterDisableArchTest {

  /**
   * Lower it when a line goes. Raising it is a decision for review, with the reason in the line.
   */
  static final int BASELINE_SIZE = 1;

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.openaev");

  private static final List<TenantFilterDisableBaseline.Entry> BASELINE =
      TenantFilterDisableBaseline.load();

  @Nested
  @DisplayName("Production code")
  class ProductionCode {

    @Test
    @DisplayName("given production code, should disable the tenant filter only at baselined sites")
    void given_productionCode_should_disableTheTenantFilterOnlyAtBaselinedSites() {
      TenantBackgroundTransactionRules.noTenantFilterDisableOutsideBaseline(
              TenantFilterDisableBaseline.sites(BASELINE))
          .check(PRODUCTION_CLASSES);
    }
  }

  @Nested
  @DisplayName("The baseline")
  class TheBaseline {

    @Test
    @DisplayName("given the baseline, should carry a classified reason on every line")
    void given_theBaseline_should_carryAClassifiedReasonOnEveryLine() {
      assertThat(TenantFilterDisableBaseline.malformed(BASELINE))
          .as("every line of " + TenantFilterDisableBaseline.RESOURCE)
          .isEmpty();
    }

    @Test
    @DisplayName("given the baseline, should list only sites that still make the call")
    void given_theBaseline_should_listOnlySitesThatStillMakeTheCall() {
      // Arrange
      Map<String, Integer> liveSites =
          TenantBackgroundTransactionRules.tenantFilterDisableSites(PRODUCTION_CLASSES);

      // Act
      Set<String> stale = new TreeSet<>(TenantFilterDisableBaseline.sites(BASELINE));
      stale.removeAll(liveSites.keySet());

      // Assert
      assertThat(stale)
          .as(
              "these lines of "
                  + TenantFilterDisableBaseline.RESOURCE
                  + " no longer match a call: remove them and lower BASELINE_SIZE")
          .isEmpty();
    }

    @Test
    @DisplayName("given the baseline, should hold exactly the pinned number of lines")
    void given_theBaseline_should_holdExactlyThePinnedNumberOfLines() {
      assertThat(BASELINE)
          .as(
              TenantFilterDisableBaseline.RESOURCE
                  + " only shrinks: lower BASELINE_SIZE with the line you remove; a new call goes"
                  + " through the primitive with an explicit scope instead")
          .hasSize(BASELINE_SIZE);
    }
  }
}
