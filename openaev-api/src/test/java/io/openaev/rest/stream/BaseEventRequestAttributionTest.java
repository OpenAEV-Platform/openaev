package io.openaev.rest.stream;

import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import io.openaev.helper.ObjectMapperHelper;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.UserFixture;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@DisplayName("Tenant of the events a request publishes")
class BaseEventRequestAttributionTest {

  private static final String WRITE_TENANT = "tenant-write";
  private static final String AMBIENT_TENANT = "tenant-ambient";

  private final ObjectMapper mapper = ObjectMapperHelper.openAEVJsonMapper();
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @BeforeEach
  void inARequestWhoseAmbientTenantIsNotTheWriteTenant() {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    TenantContext.setCurrentTenant(AMBIENT_TENANT);
  }

  @AfterEach
  void clear() {
    RequestContextHolder.resetRequestAttributes();
    TenantContext.clearCurrentTenant();
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
    @DisplayName("given_noAttribution_should_stampAnEventWithTheAmbientTenant")
    void given_noAttribution_should_stampAnEventWithTheAmbientTenant() {
      // Arrange
      User user = savedUser();

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, user, mapper);

      // Assert
      assertThat(event.getTenantId()).isEqualTo(AMBIENT_TENANT);
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
