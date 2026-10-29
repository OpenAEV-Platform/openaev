package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.rest.settings.PreviewFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("BulkSnapshotExportCondition")
class BulkSnapshotExportConditionTest {

  @Nested
  @DisplayName("Flag resolution")
  class FlagResolution {

    @Test
    @DisplayName("given no property should be disabled")
    void given_noProperty_should_beDisabled() {
      MockEnvironment env = new MockEnvironment();

      assertThat(BulkSnapshotExportCondition.isBulkSnapshotExportEnabled(env)).isFalse();
    }

    @Test
    @DisplayName("given empty property should be disabled")
    void given_emptyProperty_should_beDisabled() {
      MockEnvironment env = new MockEnvironment().withProperty("openaev.enabled-dev-features", "");

      assertThat(BulkSnapshotExportCondition.isBulkSnapshotExportEnabled(env)).isFalse();
    }

    @Test
    @DisplayName("given unrelated features should be disabled")
    void given_unrelatedFeatures_should_beDisabled() {
      MockEnvironment env =
          new MockEnvironment()
              .withProperty(
                  "openaev.enabled-dev-features", "CREDENTIAL_ASSET,SIGNATURE_OUTPUT_PROCESSOR");

      assertThat(BulkSnapshotExportCondition.isBulkSnapshotExportEnabled(env)).isFalse();
    }

    @Test
    @DisplayName("given feature in list should be enabled")
    void given_featureInList_should_beEnabled() {
      MockEnvironment env =
          new MockEnvironment()
              .withProperty(
                  "openaev.enabled-dev-features", "CREDENTIAL_ASSET, BULK_SNAPSHOT_EXPORT");

      assertThat(BulkSnapshotExportCondition.isBulkSnapshotExportEnabled(env)).isTrue();
    }

    @Test
    @DisplayName("given feature in lowercase should be enabled")
    void given_featureInLowercase_should_beEnabled() {
      MockEnvironment env =
          new MockEnvironment()
              .withProperty("openaev.enabled-dev-features", "bulk_snapshot_export");

      assertThat(BulkSnapshotExportCondition.isBulkSnapshotExportEnabled(env)).isTrue();
    }

    @Test
    @DisplayName("given wildcard should be enabled")
    void given_wildcard_should_beEnabled() {
      MockEnvironment env = new MockEnvironment().withProperty("openaev.enabled-dev-features", "*");

      assertThat(BulkSnapshotExportCondition.isBulkSnapshotExportEnabled(env)).isTrue();
    }

    @Test
    @DisplayName("given legacy openbas key should be enabled")
    void given_legacyOpenbasKey_should_beEnabled() {
      MockEnvironment env =
          new MockEnvironment()
              .withProperty("openbas.enabled-dev-features", "BULK_SNAPSHOT_EXPORT")
              .withProperty("openaev.enabled-dev-features", "");

      assertThat(BulkSnapshotExportCondition.isBulkSnapshotExportEnabled(env)).isTrue();
    }

    @Test
    @DisplayName("given both keys should prefer the legacy one")
    void given_bothKeys_should_preferTheLegacyOne() {
      MockEnvironment env =
          new MockEnvironment()
              .withProperty("openbas.enabled-dev-features", "BULK_SNAPSHOT_EXPORT")
              .withProperty("openaev.enabled-dev-features", "CREDENTIAL_ASSET");

      assertThat(BulkSnapshotExportCondition.isBulkSnapshotExportEnabled(env)).isTrue();
    }

    @Test
    @DisplayName("given the modern key only should be shadowed by the legacy one")
    void given_theModernKeyOnly_should_beShadowedByTheLegacyOne() {
      MockEnvironment env =
          new MockEnvironment()
              .withProperty("openbas.enabled-dev-features", "CREDENTIAL_ASSET")
              .withProperty("openaev.enabled-dev-features", "BULK_SNAPSHOT_EXPORT");

      assertThat(BulkSnapshotExportCondition.isBulkSnapshotExportEnabled(env)).isFalse();
    }

    @Test
    @DisplayName("given the condition invoked through matches should delegate to the environment")
    void given_conditionMatches_should_delegateToTheEnvironment() {
      MockEnvironment env = new MockEnvironment().withProperty("openaev.enabled-dev-features", "*");
      ConditionContext context = mock(ConditionContext.class);
      when(context.getEnvironment()).thenReturn(env);

      assertThat(new BulkSnapshotExportCondition().matches(context, null)).isTrue();
    }
  }

  @Nested
  @DisplayName("Anti-drift with PreviewFeature")
  class AntiDrift {

    @Test
    @DisplayName("given the enum entry should match the condition constant")
    void given_theEnumEntry_should_matchTheConditionConstant() {
      assertThat(PreviewFeature.BULK_SNAPSHOT_EXPORT.getValue())
          .isEqualTo(BulkSnapshotExportCondition.FEATURE_NAME);
    }

    @Test
    @DisplayName("given the condition constant should resolve to the enum entry")
    void given_theConditionConstant_should_resolveToTheEnumEntry() {
      assertThat(PreviewFeature.fromStringIgnoreCase(BulkSnapshotExportCondition.FEATURE_NAME))
          .isEqualTo(PreviewFeature.BULK_SNAPSHOT_EXPORT);
    }
  }
}
