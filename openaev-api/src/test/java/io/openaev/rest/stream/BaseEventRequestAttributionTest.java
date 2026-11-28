package io.openaev.rest.stream;

import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TenantContext;
import io.openaev.context.TransactionTenantScope;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import io.openaev.helper.ObjectMapperHelper;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.UserFixture;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@DisplayName("Tenant of the events a request publishes")
class BaseEventRequestAttributionTest {

  private static final String WRITE_TENANT = "tenant-write";
  private static final String AMBIENT_TENANT = "tenant-ambient";
  private static final String OTHER_TENANT = "tenant-other";

  private final ObjectMapper mapper = ObjectMapperHelper.openAEVJsonMapper();
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @BeforeEach
  void inARequestWhoseAmbientTenantIsNotTheWriteTenant() {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    TenantContext.setCurrentTenant(AMBIENT_TENANT);
  }

  @AfterEach
  void clear() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
      TransactionSynchronizationManager.clear();
    }
    RequestContextHolder.resetRequestAttributes();
    TenantContext.clearCurrentTenant();
  }

  /**
   * Opens a transaction on the thread and writes {@code scope} into it, as the transaction aspect
   * does for the {@code TxCtx} of a request.
   */
  private static void inATransactionScopedTo(String scope) {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionTenantScope.write(mock(EntityManager.class, RETURNS_DEEP_STUBS), scope);
  }

  private static User savedUser() {
    User user = UserFixture.getUserWithDefaultEmail();
    user.setId(UUID.randomUUID().toString());
    return user;
  }

  @Nested
  @DisplayName("After a request attributed the events of users to a tenant")
  class AttributedToUsers {

    @Test
    @DisplayName("given_attributionOfUsers_should_stampAUserEventWithTheWriteTenant")
    void given_attributionOfUsers_should_stampAUserEventWithTheWriteTenant() {
      // Arrange
      BaseEvent.attributeRequestEventsTo(WRITE_TENANT, User.class);
      User user = savedUser();

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, user, mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(WRITE_TENANT);
    }

    @Test
    @DisplayName("given_attributionOfUsersAndAScopeOfTwoTenants_should_stampAnAttributedEvent")
    void given_attributionOfUsersAndAScopeOfTwoTenants_should_stampAnAttributedEvent() {
      // Arrange
      inATransactionScopedTo(WRITE_TENANT + "," + OTHER_TENANT);
      BaseEvent.attributeRequestEventsTo(WRITE_TENANT, User.class);

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, savedUser(), mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(WRITE_TENANT);
      assertThat(event.isTenantAttributed()).isTrue();
    }

    @Test
    @DisplayName("given_attributionOfUsersAndAScopeOfNoTenant_should_stampAnAttributedEvent")
    void given_attributionOfUsersAndAScopeOfNoTenant_should_stampAnAttributedEvent() {
      // Arrange: a public route with no tenant in its path, which declares the tenant of the row
      inATransactionScopedTo("");
      BaseEvent.attributeRequestEventsTo(WRITE_TENANT, User.class);

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, savedUser(), mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(WRITE_TENANT);
      assertThat(event.isTenantAttributed()).isTrue();
    }

    @Test
    @DisplayName(
        "given_attributionOfUsersAndAScopePinningAnotherTenant_should_stampTheDeclaredTenant")
    void given_attributionOfUsersAndAScopePinningAnotherTenant_should_stampTheDeclaredTenant() {
      // Arrange
      inATransactionScopedTo(OTHER_TENANT);
      BaseEvent.attributeRequestEventsTo(WRITE_TENANT, User.class);

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, savedUser(), mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(WRITE_TENANT);
    }

    @Test
    @DisplayName("given_attributionOfUsers_should_leaveTheEventOfAnotherEntityOnItsOwnTenant")
    void given_attributionOfUsers_should_leaveTheEventOfAnotherEntityOnItsOwnTenant() {
      // Arrange
      BaseEvent.attributeRequestEventsTo(WRITE_TENANT, User.class);
      Tag tag = TagFixture.getTagWithText("later-entity");

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, tag, mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(AMBIENT_TENANT);
    }
  }

  @Nested
  @DisplayName("After a request declared the scope its roles were read in")
  class DeclaredScope {

    @Test
    @DisplayName("given_aScopeOfNoTenant_should_stampAUserEventWithNoTenant")
    void given_aScopeOfNoTenant_should_stampAUserEventWithNoTenant() {
      // Arrange
      BaseEvent.attributeRequestEventsToOneOf(List.of(), User.class);

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, savedUser(), mapper);

      // Assert
      assertThat(event.getTenantId()).isNull();
    }

    @Test
    @DisplayName("given_aScopeHoldingTheAmbientTenant_should_stampAUserEventWithTheAmbientTenant")
    void given_aScopeHoldingTheAmbientTenant_should_stampAUserEventWithTheAmbientTenant() {
      // Arrange
      BaseEvent.attributeRequestEventsToOneOf(List.of(WRITE_TENANT, AMBIENT_TENANT), User.class);

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, savedUser(), mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(AMBIENT_TENANT);
    }

    @Test
    @DisplayName("given_aScopeHoldingThePinnedTenant_should_stampAUserEventWithThePinnedTenant")
    void given_aScopeHoldingThePinnedTenant_should_stampAUserEventWithThePinnedTenant() {
      // Arrange: the first of the declared tenants in the order of their ids is not the pinned one
      inATransactionScopedTo(WRITE_TENANT);
      BaseEvent.attributeRequestEventsToOneOf(List.of(WRITE_TENANT, OTHER_TENANT), User.class);

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, savedUser(), mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(WRITE_TENANT);
    }

    @Test
    @DisplayName("given_noTenantDeclared_should_stampAUserEventWithTheAmbientTenant")
    void given_noTenantDeclared_should_stampAUserEventWithTheAmbientTenant() {
      // Arrange
      BaseEvent.attributeRequestEventsTo(null, User.class);

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, savedUser(), mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(AMBIENT_TENANT);
    }
  }

  @Nested
  @DisplayName("Without an attribution")
  class NotAttributed {

    @Test
    @DisplayName("given_aScopePinningOneTenant_should_stampAnEventWithThatTenant")
    void given_aScopePinningOneTenant_should_stampAnEventWithThatTenant() {
      // Arrange: the scope of the header route, the ambient tenant left at another one
      inATransactionScopedTo(WRITE_TENANT);
      Tag tag = TagFixture.getTagWithText("header-route");

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, tag, mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(WRITE_TENANT);
      assertThat(event.isTenantAttributed()).isTrue();
    }

    @Test
    @DisplayName(
        "given_aScopeOfTwoTenants_should_stampAnEventWithTheAmbientTenantAndMarkItUnattributed")
    void given_aScopeOfTwoTenants_should_stampAnEventWithTheAmbientTenantAndMarkItUnattributed() {
      // Arrange: the short route with no header, for a caller in two tenants
      inATransactionScopedTo(WRITE_TENANT + "," + OTHER_TENANT);
      Tag tag = TagFixture.getTagWithText("two-tenants");

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, tag, mapper);

      // Assert: the tenant the other listeners read is the ambient one, and the event says the
      // request named no tenant for its write
      assertThat(event.getTenantId()).isEqualTo(AMBIENT_TENANT);
      assertThat(event.isTenantAttributed()).isFalse();
    }

    @Test
    @DisplayName(
        "given_aScopeOfTwoTenantsOffARequest_should_stampAnEventWithTheAmbientTenantAndMarkItUnattributed")
    void given_aScopeOfTwoTenantsOffARequest_should_stampTheAmbientAndMarkItUnattributed() {
      // Arrange: a background transaction of several tenants
      RequestContextHolder.resetRequestAttributes();
      inATransactionScopedTo(WRITE_TENANT + "," + OTHER_TENANT);
      Tag tag = TagFixture.getTagWithText("two-tenants-off-request");

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, tag, mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(AMBIENT_TENANT);
      assertThat(event.isTenantAttributed()).isFalse();
    }

    @Test
    @DisplayName("given_aScopeOfOneTenantOffARequest_should_stampAnAttributedEventWithThatTenant")
    void given_aScopeOfOneTenantOffARequest_should_stampAnAttributedEventWithThatTenant() {
      // Arrange: a background transaction of one tenant
      RequestContextHolder.resetRequestAttributes();
      inATransactionScopedTo(WRITE_TENANT);
      Tag tag = TagFixture.getTagWithText("one-tenant-off-request");

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, tag, mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(WRITE_TENANT);
      assertThat(event.isTenantAttributed()).isTrue();
    }

    @Test
    @DisplayName(
        "given_aScopeOfNoTenant_should_stampAnEventWithTheAmbientTenantAndMarkItUnattributed")
    void given_aScopeOfNoTenant_should_stampTheAmbientTenantAndMarkItUnattributed() {
      // Arrange: a public route with no tenant in its path, for an anonymous caller
      inATransactionScopedTo("");
      Tag tag = TagFixture.getTagWithText("no-tenant");

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, tag, mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(AMBIENT_TENANT);
      assertThat(event.isTenantAttributed()).isFalse();
    }

    @Test
    @DisplayName(
        "given_aScopeOfNoTenantOffARequest_should_stampAnEventWithTheAmbientTenantAndMarkItUnattributed")
    void given_aScopeOfNoTenantOffARequest_should_stampTheAmbientAndMarkItUnattributed() {
      // Arrange: a background entry with no tenant, processed with no scope
      RequestContextHolder.resetRequestAttributes();
      inATransactionScopedTo("");
      Tag tag = TagFixture.getTagWithText("no-tenant-off-request");

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, tag, mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(AMBIENT_TENANT);
      assertThat(event.isTenantAttributed()).isFalse();
    }

    @Test
    @DisplayName("given_aScopeOfNoTenantThenOne_should_stampAnAttributedEventWithThatTenant")
    void given_aScopeOfNoTenantThenOne_should_stampAnAttributedEventWithThatTenant() {
      // Arrange: the scope set to one tenant on a transaction that held none
      inATransactionScopedTo("");
      TransactionTenantScope.write(mock(EntityManager.class, RETURNS_DEEP_STUBS), WRITE_TENANT);
      Tag tag = TagFixture.getTagWithText("none-then-one");

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, tag, mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(WRITE_TENANT);
      assertThat(event.isTenantAttributed()).isTrue();
    }

    @Test
    @DisplayName("given_aScopeOfOneTenantAfterTwo_should_stampAnAttributedEventWithThatTenant")
    void given_aScopeOfOneTenantAfterTwo_should_stampAnAttributedEventWithThatTenant() {
      // Arrange: the scope narrowed to one tenant on the same transaction
      inATransactionScopedTo(WRITE_TENANT + "," + OTHER_TENANT);
      TransactionTenantScope.write(mock(EntityManager.class, RETURNS_DEEP_STUBS), WRITE_TENANT);
      Tag tag = TagFixture.getTagWithText("narrowed");

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, tag, mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(WRITE_TENANT);
      assertThat(event.isTenantAttributed()).isTrue();
    }

    @Test
    @DisplayName("given_noScope_should_stampAnEventWithTheAmbientTenant")
    void given_noScope_should_stampAnEventWithTheAmbientTenant() {
      // Arrange
      User user = savedUser();

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, user, mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(AMBIENT_TENANT);
      assertThat(event.isTenantAttributed()).isTrue();
    }

    @Test
    @DisplayName("given_requestWithoutSession_should_notCreateOne")
    void given_requestWithoutSession_should_notCreateOne() {
      // Act
      new BaseEvent(DATA_UPDATE, savedUser(), mapper);

      // Assert
      assertThat(request.getSession(false)).isNull();
    }
  }
}
