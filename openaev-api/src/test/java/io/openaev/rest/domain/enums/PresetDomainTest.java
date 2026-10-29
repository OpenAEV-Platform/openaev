package io.openaev.rest.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Domain;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Preset domains")
class PresetDomainTest {

  @Nested
  @DisplayName("Matching key")
  class MatchingKey {

    @ParameterizedTest
    @ValueSource(strings = {"Tabletop", "Table-Top", "table top", " TABLE_TOP ", "Table.Top!"})
    @DisplayName("given_spellingVariantOfTabletop_should_shareTheTabletopKey")
    void given_spellingVariantOfTabletop_should_shareTheTabletopKey(String name) {
      // Act
      String key = PresetDomain.toMatchingKey(name);

      // Assert
      assertThat(key).isEqualTo("tabletop");
    }

    @Test
    @DisplayName("given_presetWithPunctuation_should_keepLettersAndDigitsOnly")
    void given_presetWithPunctuation_should_keepLettersAndDigitsOnly() {
      // Act & Assert
      assertThat(PresetDomain.toMatchingKey("E-mail Infiltration")).isEqualTo("emailinfiltration");
      assertThat(PresetDomain.toMatchingKey("Web App 2")).isEqualTo("webapp2");
    }

    @Test
    @DisplayName("given_nonAsciiLetters_should_keepThem")
    void given_nonAsciiLetters_should_keepThem() {
      // Act & Assert
      assertThat(PresetDomain.toMatchingKey("R\u00e9seau \u00c9quipe"))
          .isEqualTo("r\u00e9seau\u00e9quipe");
    }

    @Test
    @DisplayName("given_nullOrNoLetterOrDigit_should_returnEmptyKey")
    void given_nullOrNoLetterOrDigit_should_returnEmptyKey() {
      // Act & Assert
      assertThat(PresetDomain.toMatchingKey(null)).isEmpty();
      assertThat(PresetDomain.toMatchingKey(" - _ ! ")).isEmpty();
    }
  }

  @Nested
  @DisplayName("Preset names")
  class PresetNames {

    @Test
    @DisplayName("given_presets_should_haveDistinctMatchingKeys")
    void given_presets_should_haveDistinctMatchingKeys() {
      // Arrange
      List<String> names = PresetDomain.getNames();

      // Act
      List<String> keys = names.stream().map(PresetDomain::toMatchingKey).toList();

      // Assert
      assertThat(keys).doesNotHaveDuplicates().doesNotContain("");
    }

    @Test
    @DisplayName("given_exactPresetName_should_beRecognised_and_variant_should_not")
    void given_exactPresetName_should_beRecognised_and_variant_should_not() {
      // Act & Assert
      assertThat(PresetDomain.isPresetName(PresetDomain.getTabletop().getName())).isTrue();
      assertThat(PresetDomain.isPresetName("Table-Top")).isFalse();
    }

    @Test
    @DisplayName("given_tenantProvisioning_should_useEveryPresetName")
    void given_tenantProvisioning_should_useEveryPresetName() {
      // Act & Assert
      assertThat(PresetDomain.getDomainsForTenant(null))
          .extracting(Domain::getName)
          .containsExactlyElementsOf(PresetDomain.getNames());
    }
  }
}
