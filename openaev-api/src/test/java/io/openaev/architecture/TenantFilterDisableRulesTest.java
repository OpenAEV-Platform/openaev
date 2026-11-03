package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import io.openaev.architecture.fixture.TenantFilterDisableFixture;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proves the disableFilter guard fires on a fixture, honours its baseline, and rejects a baseline
 * line nobody can trust. {@link TenantFilterDisableArchTest} runs the same rule over production.
 */
@DisplayName("disableFilter guard: it fires, it honours the baseline, it rejects unreasoned lines")
class TenantFilterDisableRulesTest {

  private static final String FIXTURE = TenantFilterDisableFixture.class.getName();
  private static final JavaClasses FIXTURE_CLASSES =
      new ClassFileImporter().importClasses(TenantFilterDisableFixture.class);

  private static final String GOOD_REASON =
      "restores-captured-state: puts the filter back to what the method found on entry";

  @Nested
  @DisplayName("Detection")
  class Detection {

    @Test
    @DisplayName("given every shape of the call, should find each site once with its call count")
    void given_everyShapeOfTheCall_should_findEachSiteWithItsCallCount() {
      // Act
      var sites = TenantBackgroundTransactionRules.tenantFilterDisableSites(FIXTURE_CLASSES);

      // Assert: any session type counts, a lambda belongs to its method, enableFilter does not
      assertThat(sites)
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  FIXTURE + "#widenRead", 1,
                  FIXTURE + "#widenThroughASubtype", 1,
                  FIXTURE + "#widenThroughAStatelessSession", 1,
                  FIXTURE + "#widenInsideALambda", 1,
                  FIXTURE + "#widenTwice", 2));
    }
  }

  @Nested
  @DisplayName("The rule")
  class TheRule {

    @Test
    @DisplayName("given an empty baseline, should flag every call")
    void given_emptyBaseline_should_flagEveryCall() {
      // Act
      EvaluationResult result =
          TenantBackgroundTransactionRules.noTenantFilterDisableOutsideBaseline(Set.of())
              .evaluate(FIXTURE_CLASSES);

      // Assert
      assertThat(result.hasViolation()).isTrue();
      assertThat(result.getFailureReport().getDetails()).hasSize(6);
    }

    @Test
    @DisplayName(
        "given every single-call site baselined, should flag only the second call of a site")
    void given_everySiteBaselined_should_flagOnlyTheSecondCallOfASite() {
      // Arrange
      Set<String> baseline =
          Set.of(
              FIXTURE + "#widenRead",
              FIXTURE + "#widenThroughASubtype",
              FIXTURE + "#widenThroughAStatelessSession",
              FIXTURE + "#widenInsideALambda",
              FIXTURE + "#widenTwice");

      // Act
      EvaluationResult result =
          TenantBackgroundTransactionRules.noTenantFilterDisableOutsideBaseline(baseline)
              .evaluate(FIXTURE_CLASSES);

      // Assert
      assertThat(result.getFailureReport().getDetails())
          .singleElement()
          .asString()
          .contains(FIXTURE + "#widenTwice")
          .contains("more than once");
    }
  }

  @Nested
  @DisplayName("Baseline grammar")
  class BaselineGrammar {

    @Test
    @DisplayName("given a classified and explained line, should accept it")
    void given_classifiedAndExplainedLine_should_acceptIt() {
      // Arrange
      var entries = TenantFilterDisableBaseline.parse(List.of("a.B#c  # " + GOOD_REASON));

      // Act
      List<String> problems = TenantFilterDisableBaseline.malformed(entries);

      // Assert
      assertThat(problems).isEmpty();
      assertThat(TenantFilterDisableBaseline.sites(entries)).containsExactly("a.B#c");
    }

    @Test
    @DisplayName("given a line without a reason, should reject it")
    void given_lineWithoutReason_should_rejectIt() {
      // Act
      List<String> problems =
          TenantFilterDisableBaseline.malformed(
              TenantFilterDisableBaseline.parse(List.of("a.B#c")));

      // Assert
      assertThat(problems).singleElement().asString().contains("no reason");
    }

    @Test
    @DisplayName("given a bare table name as the reason, should reject it")
    void given_bareTableNameAsReason_should_rejectIt() {
      // Act
      List<String> problems =
          TenantFilterDisableBaseline.malformed(
              TenantFilterDisableBaseline.parse(List.of("a.B#c  # injects")));

      // Assert
      assertThat(problems).singleElement().asString().contains("must begin with one of");
    }

    @Test
    @DisplayName("given a classification with no explanation, should reject it")
    void given_classificationWithNoExplanation_should_rejectIt() {
      // Act
      List<String> problems =
          TenantFilterDisableBaseline.malformed(
              TenantFilterDisableBaseline.parse(List.of("a.B#c  # defensive: injects")));

      // Assert
      assertThat(problems).singleElement().asString().contains("must be followed by why");
    }

    @Test
    @DisplayName("given a class without a method, or a duplicated site, should reject both")
    void given_classWithoutMethodOrDuplicate_should_rejectBoth() {
      // Act
      List<String> problems =
          TenantFilterDisableBaseline.malformed(
              TenantFilterDisableBaseline.parse(
                  List.of(
                      "a.B  # " + GOOD_REASON,
                      "a.B#c  # " + GOOD_REASON,
                      "a.B#c  # " + GOOD_REASON)));

      // Assert
      assertThat(problems).hasSize(2);
      assertThat(problems.get(0)).contains("fully.qualified.Class#method");
      assertThat(problems.get(1)).contains("duplicates line 2");
    }
  }
}
