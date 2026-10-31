package io.openaev.api.news_feed;

import static io.openaev.api.news_feed.NewsFeedItemApi.TENANT_NEWS_FEED_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.NewsFeedItem;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.model.User;
import io.openaev.database.repository.NewsFeedItemRepository;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.UserService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.NewsFeedItemFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SortField;
import io.openaev.xtmhub.XtmHubRegistrationStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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
@DisplayName("News feed API tests")
class NewsFeedItemApiTest extends IntegrationTest {

  private static final String SEARCH_URI = TENANT_NEWS_FEED_URI + "/me/search";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private NewsFeedItemRepository newsFeedItemRepository;
  @Autowired private TenantXtmHubRegistrationRepository registrationRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserService userService;

  @MockitoBean private PreviewFeatureService previewFeatureService;

  private String tenantId;

  @BeforeEach
  void setUp() throws Exception {
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED)).thenReturn(true);
    tenantId = tenantHelper.createTenantWithCurrentUser("news-feed-tenant").getId();
  }

  private void registerTenant(XtmHubRegistrationStatus status) {
    TenantXtmHubRegistration registration = new TenantXtmHubRegistration();
    registration.setToken("token");
    registration.setRegistrationDate(LocalDateTime.now());
    registration.setRegistrationStatus(status);
    registration.setTenant(new Tenant(tenantId));
    registrationRepository.save(registration);
  }

  private NewsFeedItem saveItem(User user, String hubItemId, String title, Instant creationDate) {
    NewsFeedItem item = NewsFeedItemFixture.createNewsFeedItem(user, hubItemId);
    item.setTitle(title);
    item.setCreationDate(creationDate);
    return newsFeedItemRepository.save(item);
  }

  private String search(String textSearch, String sortField) throws Exception {
    var input = PaginationFixture.getDefault().textSearch(textSearch);
    if (sortField != null) {
      input.sorts(List.of(SortField.builder().property(sortField).direction("desc").build()));
    }
    return mvc.perform(
            post(SEARCH_URI, tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input.build()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Nested
  @DisplayName("Search my news feed items")
  class SearchMyItems {

    @Test
    @DisplayName("Given a registered tenant, should return only the items of the current user")
    void given_registeredTenant_should_returnOnlyMyItems() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.REGISTERED);
      User me = userService.currentUser();
      User other = userRepository.save(UserFixture.getUserWithDefaultEmail());
      saveItem(me, "hub-mine", "Mine", Instant.parse("2026-10-20T10:00:00Z"));
      saveItem(other, "hub-other", "Other", Instant.parse("2026-10-20T10:00:00Z"));

      // Act
      String response = search("", null);

      // Assert
      assertThatJson(response).node("totalElements").isEqualTo(1);
      assertThatJson(response).node("content[0].news_feed_item_title").isEqualTo("Mine");
      assertThatJson(response)
          .node("content[0].news_feed_item_type")
          .isEqualTo("RESOURCE_SCENARIO");
      assertThatJson(response)
          .node("content[0].news_feed_item_tags")
          .isEqualTo("[\"phishing\", \"ransomware\"]");
      assertThatJson(response)
          .node("content[0].news_feed_item_url_path")
          .isEqualTo("/scenarios/hub-mine");
      assertThatJson(response)
          .node("content[0]")
          .isObject()
          .doesNotContainKey("news_feed_item_user");
    }

    @Test
    @DisplayName("Given a title search, should return the matching items only")
    void given_titleSearch_should_returnMatchingItems() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.REGISTERED);
      User me = userService.currentUser();
      saveItem(me, "hub-1", "Ransomware playbook", Instant.parse("2026-10-20T10:00:00Z"));
      saveItem(me, "hub-2", "Phishing campaign", Instant.parse("2026-10-21T10:00:00Z"));

      // Act
      String response = search("phishing", null);

      // Assert
      assertThatJson(response).node("totalElements").isEqualTo(1);
      assertThatJson(response)
          .node("content[0].news_feed_item_title")
          .isEqualTo("Phishing campaign");
    }

    @Test
    @DisplayName("Given a sort on the creation date, should return the newest item first")
    void given_creationDateSort_should_returnNewestFirst() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.REGISTERED);
      User me = userService.currentUser();
      saveItem(me, "hub-old", "Old", Instant.parse("2026-10-01T10:00:00Z"));
      saveItem(me, "hub-new", "New", Instant.parse("2026-10-25T10:00:00Z"));

      // Act
      String response = search("", "news_feed_item_creation_date");

      // Assert
      assertThatJson(response).node("content[0].news_feed_item_title").isEqualTo("New");
      assertThatJson(response).node("content[1].news_feed_item_title").isEqualTo("Old");
    }

    @Test
    @DisplayName("Given an unregistered tenant, should return nothing")
    void given_unregisteredTenant_should_returnNothing() throws Exception {
      // Arrange
      User me = userService.currentUser();
      saveItem(me, "hub-mine", "Mine", Instant.parse("2026-10-20T10:00:00Z"));

      // Act
      String response = search("", null);

      // Assert
      assertThatJson(response).node("totalElements").isEqualTo(0);
      assertThatJson(response).node("content").isArray().isEmpty();
    }

    @Test
    @DisplayName("Given a tenant that lost its connectivity, should still return its items")
    void given_lostConnectivityTenant_should_stillReturnItems() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.LOST_CONNECTIVITY);
      saveItem(
          userService.currentUser(), "hub-mine", "Mine", Instant.parse("2026-10-20T10:00:00Z"));

      // Act
      String response = search("", null);

      // Assert
      assertThatJson(response).node("totalElements").isEqualTo(1);
      assertThatJson(response).node("content[0].news_feed_item_title").isEqualTo("Mine");
    }

    @Test
    @DisplayName("Given the feature disabled, should not find the endpoint")
    void given_featureDisabled_should_returnNotFound() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.REGISTERED);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED))
          .thenReturn(false);

      // Act & Assert
      mvc.perform(
              post(SEARCH_URI, tenantId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(PaginationFixture.getDefault().build()))
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }
  }
}
