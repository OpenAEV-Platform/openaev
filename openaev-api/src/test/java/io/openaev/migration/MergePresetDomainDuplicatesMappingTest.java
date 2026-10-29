package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.migration.V6_20261012120000000__Merge_preset_domain_duplicates.DomainRow;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Merge preset domain duplicates - duplicate mapping")
class MergePresetDomainDuplicatesMappingTest {

  private static Map<String, String> map(DomainRow... domains) {
    return V6_20261012120000000__Merge_preset_domain_duplicates.findPresetIdByDuplicateId(
        List.of(domains));
  }

  @Test
  @DisplayName("given_variantOfAPresetInItsTenant_should_mapItToThatPreset")
  void given_variantOfAPresetInItsTenant_should_mapItToThatPreset() {
    // Act
    Map<String, String> merges =
        map(
            new DomainRow("preset-a", "Tabletop", "tenant-a"),
            new DomainRow("variant-a", "Table-Top", "tenant-a"),
            new DomainRow("other-variant-a", "table top", "tenant-a"));

    // Assert
    assertThat(merges)
        .containsOnly(Map.entry("variant-a", "preset-a"), Map.entry("other-variant-a", "preset-a"));
  }

  @Test
  @DisplayName("given_variantInATenantWithoutThePreset_should_neverMapItToAnotherTenantPreset")
  void given_variantInATenantWithoutThePreset_should_neverMapItToAnotherTenantPreset() {
    // Act
    Map<String, String> merges =
        map(
            new DomainRow("preset-a", "Tabletop", "tenant-a"),
            new DomainRow("variant-b", "Table-Top", "tenant-b"));

    // Assert
    assertThat(merges).isEmpty();
  }

  @Test
  @DisplayName("given_presetsAndCustomDomainsOnly_should_mapNothing")
  void given_presetsAndCustomDomainsOnly_should_mapNothing() {
    // Act
    Map<String, String> merges =
        map(
            new DomainRow("preset-a", "Tabletop", "tenant-a"),
            new DomainRow("cloud-a", "Cloud", "tenant-a"),
            new DomainRow("custom-a", "Satellite Ops", "tenant-a"),
            new DomainRow("blank-a", "---", "tenant-a"));

    // Assert
    assertThat(merges).isEmpty();
  }
}
