package io.openaev.xtmhub.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.NewsFeedItem;
import io.openaev.database.model.NewsFeedType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.model.User;
import io.openaev.database.repository.NewsFeedItemRepository;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.NewsFeedItemService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantExtension;
import io.openaev.xtmhub.NewsFeedFetchResult;
import io.openaev.xtmhub.ProvisionedNewsFeedItem;
import io.openaev.xtmhub.ProvisionedNewsFeedItems;
import io.openaev.xtmhub.XtmHubClient;
import io.openaev.xtmhub.XtmHubRegistrationStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test for {@link XtmHubNewsFeedCollectorService}. It cannot be {@code @Transactional}:
 * the collector opens its own transactions, so data is committed and cleaned up by hand.
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@WithMockUser(isAdmin = true)
@ExtendWith(DefaultTenantExtension.class)
@DisplayName("XtmHubNewsFeedCollectorService integration tests")
class XtmHubNewsFeedCollectorServiceTest extends IntegrationTest {

  private static final String FIRST_TOKEN = "first-token";
  private static final String SECOND_TOKEN = "second-token";
  private static final Instant CREATION_DATE = Instant.parse("2026-10-20T10:15:30Z");

  @Autowired private XtmHubNewsFeedCollectorService collectorService;
  @Autowired private NewsFeedItemRepository newsFeedItemRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantXtmHubRegistrationRepository registrationRepository;
  @Autowired private TenantComposer tenantComposer;

  @MockitoBean private XtmHubClient xtmHubClient;
  @MockitoBean private PreviewFeatureService previewFeatureService;

  private final List<User> createdUsers = new ArrayList<>();
  private final List<String> createdTenantIds = new ArrayList<>();
  private Tenant firstTenant;
  private Tenant secondTenant;

  @BeforeEach
  void setUp() {
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED)).thenReturn(true);
    firstTenant = createTenant("News-Feed-A");
    secondTenant = createTenant("News-Feed-B");
  }

  @AfterEach
  void cleanup() {
    newsFeedItemRepository.deleteAll();
    userRepository.deleteAll(createdUsers);
    createdTenantIds.forEach(registrationRepository::deleteByTenantId);
    createdTenantIds.forEach(tenantRepository::deleteById);
  }

  @Nested
  @DisplayName("Item creation")
  class ItemCreation {

    @Test
    @DisplayName("Given a registered tenant, should create one unread item per user")
    void given_registeredTenant_should_createOneUnreadItemPerUser() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User first = createUser(firstTenant);
      User second = createUser(firstTenant);
      givenHubReturns(FIRST_TOKEN, scenario("hub-1"));

      // Act
      collectorService.run();

      // Assert
      for (User user : List.of(first, second)) {
        assertThat(itemsOf(user))
            .singleElement()
            .satisfies(
                item -> {
                  assertThat(item.getHubItemId()).isEqualTo("hub-1");
                  assertThat(item.getTitle()).isEqualTo("Scenario hub-1");
                  assertThat(item.getType()).isEqualTo(NewsFeedType.RESOURCE_SCENARIO.name());
                  assertThat(item.getTags()).containsExactly("phishing");
                  assertThat(item.getUrlPath()).isEqualTo("/scenarios/hub-1");
                  assertThat(item.getCreationDate()).isEqualTo(CREATION_DATE);
                  assertThat(item.isRead()).isFalse();
                });
      }
    }

    @Test
    @DisplayName("Given the same item twice in a payload, should create it once per user")
    void given_sameItemTwiceInPayload_should_createItOncePerUser() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User user = createUser(firstTenant);
      givenHubReturns(
          FIRST_TOKEN,
          scenario("hub-1"),
          new ProvisionedNewsFeedItem(
              "hub-1", "Updated title", "RESOURCE_SCENARIO", List.of(), null, CREATION_DATE, false),
          scenario("hub-2"));

      // Act
      collectorService.run();

      // Assert
      assertThat(itemsOf(user))
          .extracting(NewsFeedItem::getHubItemId, NewsFeedItem::getTitle)
          .containsExactlyInAnyOrder(
              tuple("hub-1", "Scenario hub-1"), tuple("hub-2", "Scenario hub-2"));
    }

    @Test
    @DisplayName("Given more users than a chunk, should create the items of every user")
    void given_moreUsersThanAChunk_should_createItemsOfEveryUser() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      List<User> users = createUsers(NewsFeedItemService.USER_CHUNK_SIZE + 1, firstTenant);
      givenHubReturns(FIRST_TOKEN, scenario("hub-1"));

      // Act
      collectorService.run();

      // Assert
      Set<String> userIds = users.stream().map(User::getId).collect(Collectors.toSet());
      assertThat(newsFeedItemRepository.findAll())
          .extracting(NewsFeedItem::getOwnerUserId)
          .filteredOn(userIds::contains)
          .hasSize(users.size())
          .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Given the same Hub payload twice, should not create duplicates")
    void given_samePayloadTwice_should_notCreateDuplicates() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User user = createUser(firstTenant);
      givenHubReturns(FIRST_TOKEN, scenario("hub-1"));

      // Act
      collectorService.run();
      collectorService.run();

      // Assert
      assertThat(itemsOf(user)).hasSize(1);
    }

    @Test
    @DisplayName("Given an existing item, should leave it untouched")
    void given_existingItem_should_leaveItUntouched() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User user = createUser(firstTenant);
      givenHubReturns(FIRST_TOKEN, scenario("hub-1"));
      collectorService.run();
      NewsFeedItem item = itemsOf(user).getFirst();
      item.setRead(true);
      newsFeedItemRepository.save(item);

      // Act
      collectorService.run();

      // Assert
      assertThat(itemsOf(user))
          .singleElement()
          .satisfies(kept -> assertThat(kept.isRead()).isTrue());
    }

    @Test
    @DisplayName("Given a user unsubscribed from the type, should create no item for them")
    void given_userUnsubscribedFromType_should_createNoItemForThem() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User unsubscribed = createUser(firstTenant);
      unsubscribed.setUnsubscribedNewsFeedTypes(new String[] {"RESOURCE_SCENARIO"});
      userRepository.save(unsubscribed);
      User subscribed = createUser(firstTenant);
      givenHubReturns(FIRST_TOKEN, scenario("hub-1"));

      // Act
      collectorService.run();

      // Assert
      assertThat(itemsOf(unsubscribed)).isEmpty();
      assertThat(itemsOf(subscribed)).hasSize(1);
    }

    @Test
    @DisplayName("Given a user unsubscribed from every type, should create no item for them")
    void given_userUnsubscribedFromEveryType_should_createNoItemForThem() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User unsubscribed = createUser(firstTenant);
      unsubscribed.setUnsubscribedNewsFeedTypes(new String[] {NewsFeedType.ALL_TYPES});
      userRepository.save(unsubscribed);
      givenHubReturns(FIRST_TOKEN, scenario("hub-1"));

      // Act
      collectorService.run();

      // Assert
      assertThat(itemsOf(unsubscribed)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Recipients")
  class Recipients {

    @Test
    @DisplayName("Given a user of a tenant not registered on the Hub, should create no item")
    void given_userOfUnregisteredTenant_should_createNoItem() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User registeredUser = createUser(firstTenant);
      User unregisteredUser = createUser(secondTenant);
      givenHubReturns(FIRST_TOKEN, scenario("hub-1"));

      // Act
      collectorService.run();

      // Assert
      assertThat(itemsOf(registeredUser)).hasSize(1);
      assertThat(itemsOf(unregisteredUser)).isEmpty();
    }

    @Test
    @DisplayName("Given a user of a registered and an unregistered tenant, should create one item")
    void given_userOfRegisteredAndUnregisteredTenants_should_createOneItem() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User user = createUser(firstTenant, secondTenant);
      givenHubReturns(FIRST_TOKEN, scenario("hub-1"));

      // Act
      collectorService.run();

      // Assert
      assertThat(itemsOf(user)).hasSize(1);
    }

    @Test
    @DisplayName("Given users of several registered tenants, should create items for all of them")
    void given_severalRegisteredTenants_should_createItemsForAllTheirUsers() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      saveRegistration(SECOND_TOKEN, secondTenant, LocalDateTime.now().minusDays(1));
      User first = createUser(firstTenant);
      User second = createUser(secondTenant);
      givenHubReturns(FIRST_TOKEN, scenario("hub-1"));

      // Act
      collectorService.run();

      // Assert
      assertThat(itemsOf(first)).hasSize(1);
      assertThat(itemsOf(second)).hasSize(1);
    }

    @Test
    @DisplayName("Given a tenant that lost connectivity, should create no item for its users")
    void given_tenantLostConnectivity_should_createNoItemForItsUsers() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      saveRegistration(
          SECOND_TOKEN,
          secondTenant,
          LocalDateTime.now().minusDays(1),
          XtmHubRegistrationStatus.LOST_CONNECTIVITY);
      User first = createUser(firstTenant);
      User second = createUser(secondTenant);
      givenHubReturns(FIRST_TOKEN, scenario("hub-1"));

      // Act
      collectorService.run();

      // Assert
      assertThat(itemsOf(first)).hasSize(1);
      assertThat(itemsOf(second)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Skipped items and users")
  class Skipped {

    @Test
    @DisplayName("Given a user who belongs to no tenant, should create no item for them")
    void given_userWithoutTenant_should_createNoItemForThem() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User withoutTenant = createUser();
      givenHubReturns(FIRST_TOKEN, scenario("hub-1"));

      // Act
      collectorService.run();

      // Assert
      assertThat(itemsOf(withoutTenant)).isEmpty();
    }

    @Test
    @DisplayName("Given deleted and unknown-type Hub items, should create only the valid one")
    void given_deletedAndUnknownTypeItems_should_createOnlyTheValidOne() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User user = createUser(firstTenant);
      givenHubReturns(
          FIRST_TOKEN,
          scenario("hub-valid"),
          new ProvisionedNewsFeedItem(
              "hub-deleted", "Deleted", "RESOURCE_SCENARIO", List.of(), null, CREATION_DATE, true),
          new ProvisionedNewsFeedItem(
              "hub-unknown",
              "Unknown",
              "RESOURCE_PLAYBOOK",
              List.of(),
              null,
              CREATION_DATE,
              false));

      // Act
      collectorService.run();

      // Assert
      assertThat(itemsOf(user)).extracting(NewsFeedItem::getHubItemId).containsExactly("hub-valid");
    }
  }

  @Nested
  @DisplayName("Hub calls")
  class HubCalls {

    @Test
    @DisplayName("Given no registered tenant, should not call the Hub")
    void given_noRegisteredTenant_should_notCallHub() {
      // Arrange
      User user = createUser(firstTenant);

      // Act
      collectorService.run();

      // Assert
      verify(xtmHubClient, never()).consumeProvisionedNewsFeedItems(any(), any());
      assertThat(itemsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("Given the feature flag is off, should not call the Hub")
    void given_featureFlagOff_should_notCallHub() {
      // Arrange
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED))
          .thenReturn(false);
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User user = createUser(firstTenant);

      // Act
      collectorService.run();

      // Assert
      verify(xtmHubClient, never()).consumeProvisionedNewsFeedItems(any(), any());
      assertThat(itemsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("Given a rejected first token, should use the next tenant's token")
    void given_rejectedFirstToken_should_useNextTenantToken() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      saveRegistration(SECOND_TOKEN, secondTenant, LocalDateTime.now().minusDays(1));
      User user = createUser(firstTenant);
      when(xtmHubClient.consumeProvisionedNewsFeedItems(eq(FIRST_TOKEN), any()))
          .thenReturn(new NewsFeedFetchResult.TokenRejected());
      givenHubReturns(SECOND_TOKEN, scenario("hub-1"));

      // Act
      collectorService.run();

      // Assert
      assertThat(itemsOf(user)).hasSize(1);
    }

    @Test
    @DisplayName("Given several valid tokens, should call the Hub once with the first tenant's")
    void given_severalValidTokens_should_callHubOnceWithFirstTenantToken() {
      // Arrange
      saveRegistration(SECOND_TOKEN, secondTenant, LocalDateTime.now().minusDays(1));
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User user = createUser(firstTenant);
      givenHubReturns(FIRST_TOKEN, scenario("hub-1"));
      givenHubReturns(SECOND_TOKEN, scenario("hub-1"));

      // Act
      collectorService.run();

      // Assert
      verify(xtmHubClient).consumeProvisionedNewsFeedItems(eq(FIRST_TOKEN), any());
      verify(xtmHubClient, never()).consumeProvisionedNewsFeedItems(eq(SECOND_TOKEN), any());
      assertThat(itemsOf(user)).hasSize(1);
    }

    @Test
    @DisplayName("Given every token rejected, should create nothing and not fail")
    void given_everyTokenRejected_should_createNothingAndNotFail() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      saveRegistration(SECOND_TOKEN, secondTenant, LocalDateTime.now().minusDays(1));
      User user = createUser(firstTenant);
      when(xtmHubClient.consumeProvisionedNewsFeedItems(any(), any()))
          .thenReturn(new NewsFeedFetchResult.TokenRejected());

      // Act
      collectorService.run();

      // Assert
      verify(xtmHubClient).consumeProvisionedNewsFeedItems(eq(FIRST_TOKEN), any());
      verify(xtmHubClient).consumeProvisionedNewsFeedItems(eq(SECOND_TOKEN), any());
      assertThat(itemsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("Given a failing Hub call, should create nothing and not fail")
    void given_failingHubCall_should_createNothingAndNotFail() {
      // Arrange
      saveRegistration(FIRST_TOKEN, firstTenant, LocalDateTime.now().minusDays(2));
      User user = createUser(firstTenant);
      when(xtmHubClient.consumeProvisionedNewsFeedItems(any(), any()))
          .thenReturn(new NewsFeedFetchResult.Failed());

      // Act
      collectorService.run();

      // Assert
      assertThat(itemsOf(user)).isEmpty();
    }
  }

  // -- Helpers --

  private Tenant createTenant(String name) {
    Tenant tenant = TenantFixture.getTenant(name);
    tenantComposer.forTenant(tenant).persist();
    createdTenantIds.add(tenant.getId());
    return tenant;
  }

  private User createUser(Tenant... tenants) {
    return createUsers(1, tenants).getFirst();
  }

  private List<User> createUsers(int count, Tenant... tenants) {
    List<User> users = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      User user = UserFixture.getUserWithDefaultEmail();
      user.setTenants(new ArrayList<>(List.of(tenants)));
      users.add(user);
    }
    List<User> saved = userRepository.saveAll(users);
    createdUsers.addAll(saved);
    return saved;
  }

  private void saveRegistration(String token, Tenant tenant, LocalDateTime registrationDate) {
    saveRegistration(token, tenant, registrationDate, XtmHubRegistrationStatus.REGISTERED);
  }

  private void saveRegistration(
      String token,
      Tenant tenant,
      LocalDateTime registrationDate,
      XtmHubRegistrationStatus status) {
    TenantXtmHubRegistration registration = new TenantXtmHubRegistration();
    registration.setToken(token);
    registration.setRegistrationStatus(status);
    registration.setRegistrationDate(registrationDate);
    registration.setConnectivityEmailEligible(true);
    registration.setTenant(tenant);
    registrationRepository.save(registration);
  }

  private ProvisionedNewsFeedItem scenario(String hubItemId) {
    return new ProvisionedNewsFeedItem(
        hubItemId,
        "Scenario " + hubItemId,
        "RESOURCE_SCENARIO",
        List.of("phishing"),
        "/scenarios/" + hubItemId,
        CREATION_DATE,
        false);
  }

  private void givenHubReturns(String token, ProvisionedNewsFeedItem... items) {
    when(xtmHubClient.consumeProvisionedNewsFeedItems(eq(token), any()))
        .thenReturn(
            new NewsFeedFetchResult.Fetched(
                new ProvisionedNewsFeedItems(List.of(items), List.of("RESOURCE_SCENARIO"))));
  }

  private List<NewsFeedItem> itemsOf(User user) {
    return newsFeedItemRepository.findAll().stream()
        .filter(item -> user.getId().equals(item.getOwnerUserId()))
        .toList();
  }
}
