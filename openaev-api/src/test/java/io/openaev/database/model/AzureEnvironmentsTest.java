package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.management.AzureEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Azure environments tests")
class AzureEnvironmentsTest {

  @Test
  @DisplayName("given_knownEnvironments_should_exposeTheirNames")
  void given_knownEnvironments_should_exposeTheirNames() {
    // Act
    var names = AzureEnvironments.names();

    // Assert: an empty list would silently break the credential form select
    assertThat(names).isNotEmpty().contains("AzureCloud");
  }

  @Test
  @DisplayName("given_names_should_allBeResolvable")
  void given_names_should_allBeResolvable() {
    // Act & Assert: names() and fromName() must stay in sync
    assertThat(AzureEnvironments.names())
        .allSatisfy(name -> assertThat(AzureEnvironments.fromName(name)).isNotNull());
  }

  @Test
  @DisplayName("given_names_should_matchKnownEnvironmentsCount")
  void given_names_should_matchKnownEnvironmentsCount() {
    // Act & Assert: names() is filtered on knownEnvironments(), which relies on the SDK instances
    assertThat(AzureEnvironments.names()).hasSize(AzureEnvironment.knownEnvironments().size());
  }

  @Test
  @DisplayName("given_publicCloudName_should_resolveToAzureEnvironment")
  void given_publicCloudName_should_resolveToAzureEnvironment() {
    // Act
    AzureEnvironment environment = AzureEnvironments.fromName("AzureCloud");

    // Assert
    assertThat(environment).isSameAs(AzureEnvironment.AZURE);
  }

  @Test
  @DisplayName("given_governmentCloudName_should_resolveToAzureEnvironment")
  void given_governmentCloudName_should_resolveToAzureEnvironment() {
    // Act
    AzureEnvironment environment = AzureEnvironments.fromName("AzureUSGovernment");

    // Assert
    assertThat(environment).isSameAs(AzureEnvironment.AZURE_US_GOVERNMENT);
  }

  @Test
  @DisplayName("given_nameWithSurroundingSpaces_should_resolveToAzureEnvironment")
  void given_nameWithSurroundingSpaces_should_resolveToAzureEnvironment() {
    // Act & Assert
    assertThat(AzureEnvironments.fromName("  AzureCloud  ")).isSameAs(AzureEnvironment.AZURE);
  }

  @Test
  @DisplayName("given_nullName_should_returnNull")
  void given_nullName_should_returnNull() {
    // Act & Assert
    assertThat(AzureEnvironments.fromName(null)).isNull();
  }

  @Test
  @DisplayName("given_blankName_should_returnNull")
  void given_blankName_should_returnNull() {
    // Act & Assert
    assertThat(AzureEnvironments.fromName("")).isNull();
    assertThat(AzureEnvironments.fromName("   ")).isNull();
  }

  @Test
  @DisplayName("given_unknownName_should_throw")
  void given_unknownName_should_throw() {
    // Act & Assert
    assertThatThrownBy(() -> AzureEnvironments.fromName("NotAnAzureCloud"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported Azure environment: NotAnAzureCloud");
  }

  @Test
  @DisplayName("given_nameWithWrongCase_should_throw")
  void given_nameWithWrongCase_should_throw() {
    // Act & Assert: the stored value is the canonical Azure CLI name, matching is exact
    assertThatThrownBy(() -> AzureEnvironments.fromName("azurecloud"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
