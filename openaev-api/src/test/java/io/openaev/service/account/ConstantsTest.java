package io.openaev.service.account;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Capability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Service Account Constants")
class ConstantsTest {

  @Test
  @DisplayName("Service role capabilities should contain AGENT_RUNTIME_ACCESS and ACCESS_DOCUMENTS")
  void given_serviceRoleCapabilities_should_containExpectedCapabilities() {
    // -- ASSERT --
    assertThat(Constants.SERVICE_ROLE_CAPABILITIES)
        .containsExactlyInAnyOrder(Capability.AGENT_RUNTIME_ACCESS, Capability.ACCESS_DOCUMENTS);
  }

  @Test
  @DisplayName("Service role name and description should be defined")
  void given_serviceRoleConstants_should_haveNameAndDescription() {
    // -- ASSERT --
    assertThat(Constants.SERVICE_ROLE_NAME).isEqualTo("Service integration");
    assertThat(Constants.SERVICE_ROLE_DESCRIPTION).isNotBlank();
  }

  @Test
  @DisplayName("Service group name and description should be defined")
  void given_serviceGroupConstants_should_haveNameAndDescription() {
    // -- ASSERT --
    assertThat(Constants.SERVICE_GROUP_NAME).isEqualTo("Service integration");
    assertThat(Constants.SERVICE_GROUP_DESCRIPTION).isNotBlank();
  }
}
