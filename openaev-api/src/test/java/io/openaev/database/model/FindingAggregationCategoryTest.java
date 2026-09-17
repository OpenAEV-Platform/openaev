package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Finding aggregation category")
class FindingAggregationCategoryTest {

  @Test
  @DisplayName("Maps every supported contract output type")
  void given_supported_contract_output_types_should_map_each_type_to_a_category() {
    // Arrange
    EnumSet<ContractOutputType> supportedTypes = EnumSet.allOf(ContractOutputType.class);

    // Act
    var categories =
        supportedTypes.stream()
            .map(FindingAggregationCategory::from)
            .collect(java.util.stream.Collectors.toSet());

    // Assert
    assertThat(categories).containsExactlyInAnyOrder(FindingAggregationCategory.values());
  }

  @Test
  @DisplayName("Maps contract output types to their analyst workflow")
  void given_contract_output_types_should_apply_the_actionable_grouping() {
    // Arrange / Act / Assert
    assertThat(FindingAggregationCategory.from(ContractOutputType.Port))
        .isEqualTo(FindingAggregationCategory.SURFACE_REACHABILITY);
    assertThat(FindingAggregationCategory.from(ContractOutputType.Username))
        .isEqualTo(FindingAggregationCategory.IDENTITIES);
    assertThat(FindingAggregationCategory.from(ContractOutputType.Credentials))
        .isEqualTo(FindingAggregationCategory.CREDENTIAL_ACCESS);
    assertThat(FindingAggregationCategory.from(ContractOutputType.Delegation))
        .isEqualTo(FindingAggregationCategory.PRIVILEGE_TRUST_STRUCTURE);
    assertThat(FindingAggregationCategory.from(ContractOutputType.CVE))
        .isEqualTo(FindingAggregationCategory.EXPLOITABLE_WEAKNESSES);
    assertThat(FindingAggregationCategory.from(ContractOutputType.File))
        .isEqualTo(FindingAggregationCategory.RESOURCES);
    assertThat(FindingAggregationCategory.from(ContractOutputType.PasswordPolicy))
        .isEqualTo(FindingAggregationCategory.CONFIGURATION_POSTURE);
    assertThat(FindingAggregationCategory.from(ContractOutputType.Text))
        .isEqualTo(FindingAggregationCategory.INFORMATIVE);
  }
}
