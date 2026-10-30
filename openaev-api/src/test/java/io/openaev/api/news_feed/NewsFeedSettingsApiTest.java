package io.openaev.api.news_feed;

import static io.openaev.api.news_feed.NewsFeedSettingsApi.TENANT_NEWS_FEED_SETTINGS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.UserService;
import io.openaev.utils.NewsFeedUtils;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.xtmhub.XtmHubRegistrationStatus;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("News feed settings API tests")
class NewsFeedSettingsApiTest extends IntegrationTest {

  private static final String SETTINGS_URI = TENANT_NEWS_FEED_SETTINGS_URI + "/me";
  private static final String SUBSCRIBED_TYPES_FIELD = "news_feed_settings_subscribed_types";
  private static final String HUB_REGISTERED_FIELD = "news_feed_settings_hub_registered";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantXtmHubRegistrationRepository registrationRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserService userService;

  @MockitoBean private PreviewFeatureService previewFeatureService;

  private String tenantId;
  private String userId;

  @BeforeEach
  void setUp() throws Exception {
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED)).thenReturn(true);
    tenantId = tenantHelper.createTenantWithCurrentUser("news-feed-settings-tenant").getId();
    // read before any request: the security context does not outlive a MockMvc call
    userId = userService.currentUser().getId();
  }

  private void registerTenant(String id, XtmHubRegistrationStatus status) {
    TenantXtmHubRegistration registration = new TenantXtmHubRegistration();
    registration.setToken("token");
    registration.setRegistrationDate(LocalDateTime.now());
    registration.setRegistrationStatus(status);
    registration.setTenant(new Tenant(id));
    registrationRepository.save(registration);
  }

  private String getSettings() throws Exception {
    return mvc.perform(get(SETTINGS_URI, tenantId))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private org.springframework.test.web.servlet.ResultActions putSettings(Object... types)
      throws Exception {
    return mvc.perform(
        put(SETTINGS_URI, tenantId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(Map.of(SUBSCRIBED_TYPES_FIELD, types)))
            .with(csrf()));
  }

  @Nested
  @DisplayName("Get my news feed settings")
  class GetMySettings {

    @Test
    @DisplayName(
        "Given a user who never changed their settings, should be subscribed to everything")
    void given_defaultUser_should_beSubscribedToEveryType() throws Exception {
      // Arrange
      registerTenant(tenantId, XtmHubRegistrationStatus.REGISTERED);

      // Act
      String response = getSettings();

      // Assert
      assertThatJson(response).node(SUBSCRIBED_TYPES_FIELD).isEqualTo("[\"RESOURCE_SCENARIO\"]");
      assertThatJson(response).node(HUB_REGISTERED_FIELD).isEqualTo(true);
    }

    @Test
    @DisplayName("Given an unregistered tenant, should still answer and flag it")
    void given_unregisteredTenant_should_flagItAsNotRegistered() throws Exception {
      // Act
      String response = getSettings();

      // Assert
      assertThatJson(response).node(HUB_REGISTERED_FIELD).isEqualTo(false);
    }

    @Test
    @DisplayName("Given a tenant that lost its connectivity, should still be registered")
    void given_lostConnectivityTenant_should_stillBeRegistered() throws Exception {
      // Arrange
      registerTenant(tenantId, XtmHubRegistrationStatus.LOST_CONNECTIVITY);

      // Act
      String response = getSettings();

      // Assert
      assertThatJson(response).node(HUB_REGISTERED_FIELD).isEqualTo(true);
    }

    @Test
    @DisplayName("Given the feature disabled, should not find the endpoint")
    void given_featureDisabled_should_returnNotFound() throws Exception {
      // Arrange
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED))
          .thenReturn(false);

      // Act & Assert
      mvc.perform(get(SETTINGS_URI, tenantId)).andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("Update my news feed settings")
  class UpdateMySettings {

    @Test
    @DisplayName("Given the type unticked, should unsubscribe from everything")
    void given_noSubscribedType_should_unsubscribeFromEverything() throws Exception {
      // Arrange
      registerTenant(tenantId, XtmHubRegistrationStatus.REGISTERED);

      // Act
      String response =
          putSettings().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

      // Assert
      assertThatJson(response).node(SUBSCRIBED_TYPES_FIELD).isArray().isEmpty();
      assertThat(userRepository.findById(userId).orElseThrow())
          .extracting(User::getUnsubscribedNewsFeedTypes)
          .isEqualTo(new String[] {NewsFeedUtils.ALL_TYPES});
      assertThatJson(getSettings()).node(SUBSCRIBED_TYPES_FIELD).isArray().isEmpty();
    }

    @Test
    @DisplayName("Given a type ticked again after unsubscribing from everything, should restore it")
    void given_typeTickedAgain_should_restoreIt() throws Exception {
      // Arrange
      registerTenant(tenantId, XtmHubRegistrationStatus.REGISTERED);
      putSettings().andExpect(status().isOk());

      // Act
      putSettings("RESOURCE_SCENARIO").andExpect(status().isOk());

      // Assert
      assertThatJson(getSettings())
          .node(SUBSCRIBED_TYPES_FIELD)
          .isEqualTo("[\"RESOURCE_SCENARIO\"]");
      assertThat(userRepository.findById(userId).orElseThrow())
          .extracting(User::getUnsubscribedNewsFeedTypes)
          .isEqualTo(new String[0]);
    }

    @Test
    @DisplayName("Given a change of mine, should not change the settings of another user")
    void given_myChange_should_notChangeOtherUsers() throws Exception {
      // Arrange
      registerTenant(tenantId, XtmHubRegistrationStatus.REGISTERED);
      User other = userRepository.save(UserFixture.getUserWithDefaultEmail());

      // Act
      putSettings().andExpect(status().isOk());

      // Assert
      assertThat(
              userRepository.findById(other.getId()).orElseThrow().getUnsubscribedNewsFeedTypes())
          .isEmpty();
    }

    @Test
    @DisplayName("Given an unregistered tenant, should forbid the update")
    void given_unregisteredTenant_should_returnForbidden() throws Exception {
      // Act & Assert
      putSettings().andExpect(status().isForbidden());
      assertThat(userRepository.findById(userId).orElseThrow())
          .extracting(User::getUnsubscribedNewsFeedTypes)
          .isEqualTo(new String[0]);
    }

    @Test
    @DisplayName("Given another tenant registered but not the one of the request, should forbid")
    void given_otherTenantRegistered_should_returnForbidden() throws Exception {
      // Arrange
      String otherTenantId = tenantHelper.createTenant("other-news-feed-tenant").getId();
      registerTenant(otherTenantId, XtmHubRegistrationStatus.REGISTERED);

      // Act & Assert
      putSettings().andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Given an unknown type, should reject the payload")
    void given_unknownType_should_returnBadRequest() throws Exception {
      // Arrange
      registerTenant(tenantId, XtmHubRegistrationStatus.REGISTERED);

      // Act & Assert
      putSettings("RESOURCE_PLAYBOOK").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Given no list, should reject the payload")
    void given_missingList_should_returnBadRequest() throws Exception {
      // Arrange
      registerTenant(tenantId, XtmHubRegistrationStatus.REGISTERED);

      // Act & Assert
      mvc.perform(
              put(SETTINGS_URI, tenantId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}")
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Given the feature disabled, should not find the endpoint")
    void given_featureDisabled_should_returnNotFound() throws Exception {
      // Arrange
      registerTenant(tenantId, XtmHubRegistrationStatus.REGISTERED);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED))
          .thenReturn(false);

      // Act & Assert
      putSettings().andExpect(status().isNotFound());
    }
  }
}
