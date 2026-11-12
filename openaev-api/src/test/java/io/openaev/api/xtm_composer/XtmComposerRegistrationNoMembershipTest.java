package io.openaev.api.xtm_composer;

import static io.openaev.api.xtm_composer.XtmComposerApi.XTMCOMPOSER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.xtm_composer.dto.XtmComposerRegisterInput;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.ProductionActiveTables;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The composer's machine identity belongs to no tenant. The registration it wrote is a platform row
 * of {@code parameters}, so the instance-management endpoints read it under the caller's empty
 * scope instead of answering "invalid identifier".
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("XTM Composer registration read by a caller with no tenant membership")
class XtmComposerRegistrationNoMembershipTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private OpenAEVConfig openAEVConfig;

  @BeforeAll
  void setUp() {
    openAEVConfig.setVersion("test-version");
  }

  private void register(String id) throws Exception {
    XtmComposerRegisterInput input = new XtmComposerRegisterInput();
    input.setName("composer with no tenant");
    input.setId(id);
    input.setPublicKey("public-key");
    mvc.perform(
            post(XTMCOMPOSER_URI + "/register")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("given_aRegisteredComposerAndNoMembership_should_listItsInstances")
  void given_aRegisteredComposerAndNoMembership_should_listItsInstances() throws Exception {
    // Arrange
    register("composer-without-tenant");

    // Act + Assert: the registration is found, the identifier is valid
    mvc.perform(
            get(XTMCOMPOSER_URI + "/composer-without-tenant/connector-instances")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("given_aRegisteredComposerAndNoMembership_should_refuseAnotherIdentifier")
  void given_aRegisteredComposerAndNoMembership_should_refuseAnotherIdentifier() throws Exception {
    // Arrange
    register("composer-without-tenant");

    // Act + Assert
    mvc.perform(
            get(XTMCOMPOSER_URI + "/another-composer/connector-instances")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }
}
