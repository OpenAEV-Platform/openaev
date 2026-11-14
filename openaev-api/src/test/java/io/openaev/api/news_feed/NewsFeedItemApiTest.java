package io.openaev.api.news_feed;

import static io.openaev.api.news_feed.NewsFeedItemApi.TENANT_NEWS_FEED_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
  private static final String UNREAD_COUNT_URI = TENANT_NEWS_FEED_URI + "/me/unread-count";
  private static final String READ_ALL_URI = TENANT_NEWS_FEED_URI + "/me/read-all";

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
    registerTenant(tenantId, status);
  }

  private void registerTenant(String id, XtmHubRegistrationStatus status) {
    TenantXtmHubRegistration registration = new TenantXtmHubRegistration();
    registration.setToken("token");
    registration.setRegistrationDate(LocalDateTime.now());
    registration.setRegistrationStatus(status);
    registration.setTenant(new Tenant(id));
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

  private NewsFeedItem saveItem(User user, String hubItemId, boolean read) {
    NewsFeedItem item = NewsFeedItemFixture.createNewsFeedItem(user, hubItemId);
    item.setRead(read);
    return newsFeedItemRepository.save(item);
  }

  private String unreadCount() throws Exception {
    return mvc.perform(get(UNREAD_COUNT_URI, tenantId))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void markAllRead() throws Exception {
    mvc.perform(put(READ_ALL_URI, tenantId).with(csrf())).andExpect(status().isNoContent());
    entityManager.flush();
    entityManager.clear();
  }

  private boolean isRead(NewsFeedItem item) {
    return newsFeedItemRepository.findById(item.getId()).orElseThrow().isRead();
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

  @Nested
  @DisplayName("Count my unread news feed items")
  class CountMyUnreadItems {

    @Test
    @DisplayName("Given a registered tenant, should count only my unread items")
    void given_registeredTenant_should_countOnlyMyUnreadItems() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.REGISTERED);
      User me = userService.currentUser();
      User other = userRepository.save(UserFixture.getUserWithDefaultEmail());
      saveItem(me, "hub-1", false);
      saveItem(me, "hub-2", false);
      saveItem(me, "hub-3", true);
      saveItem(other, "hub-1", false);
      saveItem(other, "hub-2", false);
      saveItem(other, "hub-3", false);

      // Act
      String response = unreadCount();

      // Assert
      assertThat(response).isEqualTo("2");
    }

    @Test
    @DisplayName("Given no item, should count none")
    void given_noItem_should_countNone() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.REGISTERED);

      // Act
      String response = unreadCount();

      // Assert
      assertThat(response).isEqualTo("0");
    }

    @Test
    @DisplayName("Given an unregistered tenant, should count none")
    void given_unregisteredTenant_should_countNone() throws Exception {
      // Arrange
      saveItem(userService.currentUser(), "hub-1", false);

      // Act
      String response = unreadCount();

      // Assert
      assertThat(response).isEqualTo("0");
    }

    @Test
    @DisplayName(
        "Given another tenant registered but not the one of the request, should count none")
    void given_otherTenantRegistered_should_countNone() throws Exception {
      // Arrange
      registerTenant(
          tenantHelper.createTenant("other-news-feed-tenant").getId(),
          XtmHubRegistrationStatus.REGISTERED);
      saveItem(userService.currentUser(), "hub-1", false);

      // Act
      String response = unreadCount();

      // Assert
      assertThat(response).isEqualTo("0");
    }

    @Test
    @DisplayName("Given a tenant that lost its connectivity, should still count the items")
    void given_lostConnectivityTenant_should_stillCountItems() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.LOST_CONNECTIVITY);
      saveItem(userService.currentUser(), "hub-1", false);

      // Act
      String response = unreadCount();

      // Assert
      assertThat(response).isEqualTo("1");
    }

    @Test
    @DisplayName("Given the feature disabled, should not find the endpoint")
    void given_featureDisabled_should_returnNotFound() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.REGISTERED);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED))
          .thenReturn(false);

      // Act & Assert
      mvc.perform(get(UNREAD_COUNT_URI, tenantId)).andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("Mark all my news feed items as read")
  class MarkAllMyItemsRead {

    @Test
    @DisplayName("Given unread items, should read mine only and leave the ones of other users")
    void given_unreadItems_should_readMineOnly() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.REGISTERED);
      User me = userService.currentUser();
      User other = userRepository.save(UserFixture.getUserWithDefaultEmail());
      NewsFeedItem mine1 = saveItem(me, "hub-1", false);
      NewsFeedItem mine2 = saveItem(me, "hub-2", false);
      NewsFeedItem theirs = saveItem(other, "hub-1", false);

      // Act
      markAllRead();

      // Assert
      assertThat(isRead(mine1)).isTrue();
      assertThat(isRead(mine2)).isTrue();
      assertThat(isRead(theirs)).isFalse();
      assertThat(unreadCount()).isEqualTo("0");
    }

    @Test
    @DisplayName("Given a hundred and fifty unread items, should read them all")
    void given_manyUnreadItems_should_readThemAll() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.REGISTERED);
      User me = userService.currentUser();
      String myId = me.getId();
      for (int i = 0; i < 150; i++) {
        saveItem(me, "hub-" + i, false);
      }
      assertThat(unreadCount()).isEqualTo("150");

      // Act
      markAllRead();

      // Assert
      assertThat(newsFeedItemRepository.countByUserIdAndReadFalse(myId)).isZero();
      assertThat(unreadCount()).isEqualTo("0");
    }

    @Test
    @DisplayName("Given the items already read, should stay read when called again")
    void given_itemsAlreadyRead_should_beIdempotent() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.REGISTERED);
      NewsFeedItem item = saveItem(userService.currentUser(), "hub-1", false);
      markAllRead();

      // Act
      markAllRead();

      // Assert
      assertThat(isRead(item)).isTrue();
    }

    @Test
    @DisplayName("Given an unregistered tenant, should leave the items unread")
    void given_unregisteredTenant_should_leaveItemsUnread() throws Exception {
      // Arrange
      NewsFeedItem item = saveItem(userService.currentUser(), "hub-1", false);

      // Act
      markAllRead();

      // Assert
      assertThat(isRead(item)).isFalse();
    }

    @Test
    @DisplayName(
        "Given another tenant registered but not the one of the request, should leave unread")
    void given_otherTenantRegistered_should_leaveItemsUnread() throws Exception {
      // Arrange
      registerTenant(
          tenantHelper.createTenant("other-news-feed-tenant").getId(),
          XtmHubRegistrationStatus.REGISTERED);
      NewsFeedItem item = saveItem(userService.currentUser(), "hub-1", false);

      // Act
      markAllRead();

      // Assert
      assertThat(isRead(item)).isFalse();
    }

    @Test
    @DisplayName("Given a tenant that lost its connectivity, should still read the items")
    void given_lostConnectivityTenant_should_stillReadItems() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.LOST_CONNECTIVITY);
      NewsFeedItem item = saveItem(userService.currentUser(), "hub-1", false);

      // Act
      markAllRead();

      // Assert
      assertThat(isRead(item)).isTrue();
    }

    @Test
    @WithMockUser(isAdmin = false)
    @DisplayName("Given a user without any capability, should still read their items")
    void given_userWithoutCapability_should_readTheirItems() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.REGISTERED);
      NewsFeedItem item = saveItem(userService.currentUser(), "hub-1", false);

      // Act
      markAllRead();

      // Assert
      assertThat(isRead(item)).isTrue();
    }

    @Test
    @DisplayName("Given the feature disabled, should not find the endpoint")
    void given_featureDisabled_should_returnNotFound() throws Exception {
      // Arrange
      registerTenant(XtmHubRegistrationStatus.REGISTERED);
      NewsFeedItem item = saveItem(userService.currentUser(), "hub-1", false);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED))
          .thenReturn(false);

      // Act & Assert
      mvc.perform(put(READ_ALL_URI, tenantId).with(csrf())).andExpect(status().isNotFound());
      assertThat(isRead(item)).isFalse();
    }
  }
}
