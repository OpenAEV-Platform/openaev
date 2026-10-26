package io.openaev.database.model;

import static io.openaev.utils.SensitiveValueMaskingUtils.MASK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.utils.fixtures.FindingFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Guards the displayed value of a finding (#7893, ADR-017): the masked form that display, search,
 * filters, sorts, grouping and triggers read instead of the cleartext. It must always match what
 * the masking rule produces, and nothing may hand the cleartext out in its place.
 */
@DisplayName("Finding displayed value")
class FindingValueDisplayTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  @Nested
  @DisplayName("When the value is set")
  class WhenTheValueIsSet {

    @Test
    @DisplayName("Should hold the masked form of a sensitive value")
    void given_aSensitiveValue_should_holdItsMaskedForm() {
      // Arrange & Act
      Finding finding = FindingFixture.createCredentialsFinding("admin:motdepasse");

      // Assert
      assertThat(finding.getValueDisplay()).isEqualTo("admin:mo" + MASK);
    }

    @Test
    @DisplayName("Should follow the value when it changes")
    void given_aNewValue_should_recomputeTheMaskedForm() {
      // Arrange
      Finding finding = FindingFixture.createCredentialsFinding("admin:motdepasse");

      // Act
      finding.setValue("root:toor");

      // Assert
      assertThat(finding.getValueDisplay()).isEqualTo("root:" + MASK);
    }

    @Test
    @DisplayName("Should keep a non sensitive value as it is")
    void given_aNonSensitiveValue_should_holdTheValueAsItIs() {
      // Arrange & Act
      Finding finding = FindingFixture.createDefaultTextFinding();

      // Assert
      assertThat(finding.getValueDisplay()).isEqualTo("text_value");
    }
  }

  @Nested
  @DisplayName("When the stored masked value is missing")
  class WhenTheStoredMaskedValueIsMissing {

    @Test
    @DisplayName("Should compute the mask from the value, never hand out the cleartext")
    void given_noStoredMaskedValue_should_computeTheMask() {
      // Arrange
      // Like a row written by a pod of the previous version during a rolling deploy.
      Finding finding = FindingFixture.createCredentialsFinding("root:toor");
      finding.setValueDisplay(null);

      // Act
      String displayed = finding.getValueDisplay();

      // Assert
      assertThat(displayed).isEqualTo("root:" + MASK).doesNotContain("toor");
    }
  }

  @Nested
  @DisplayName("When the finding is saved")
  class WhenTheFindingIsSaved {

    @Test
    @DisplayName("Should store the masked form in the column the SQL queries read")
    void given_aSensitiveFinding_should_storeItsMaskedForm() {
      // Arrange
      Finding finding = FindingFixture.createCredentialsFinding("admin:motdepasse");

      // Act
      finding.refreshValueDisplay();

      // Assert
      assertThat(ReflectionTestUtils.getField(finding, "valueDisplay"))
          .isEqualTo("admin:mo" + MASK);
    }
  }

  @Nested
  @DisplayName("When the finding is serialized")
  class WhenTheFindingIsSerialized {

    @Test
    @DisplayName("Should write the masked value under finding_value and never the cleartext")
    void given_aSensitiveFinding_should_serializeOnlyTheMaskedValue() throws Exception {
      // Arrange
      Finding finding = FindingFixture.createCredentialsFinding("admin:motdepasse");

      // Act
      String json = MAPPER.writeValueAsString(finding);

      // Assert
      assertThat(MAPPER.readTree(json).get("finding_value").asText()).isEqualTo("admin:mo" + MASK);
      assertThat(json).doesNotContain("motdepasse");
    }
  }

  @Nested
  @DisplayName("When the type is not set yet")
  class WhenTheTypeIsNotSetYet {

    @Test
    @DisplayName("Should mask the whole value until the type is known")
    void given_aValueWithoutType_should_maskItWhole() {
      // Arrange & Act
      Finding finding = new Finding();
      finding.setValue("admin:motdepasse");

      // Assert
      assertThat(finding.getValueDisplay()).isEqualTo("ad" + MASK + ":mo" + MASK);
    }
  }
}
