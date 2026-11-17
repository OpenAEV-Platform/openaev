package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.NewsFeedType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NewsFeedUtils tests")
class NewsFeedUtilsTest {

  @Nested
  @DisplayName("fromHubValue")
  class FromHubValue {

    @Test
    @DisplayName("Given a known Hub value, should return the matching type")
    void given_knownHubValue_should_returnType() {
      // Act
      var type = NewsFeedUtils.fromHubValue("RESOURCE_SCENARIO");

      // Assert
      assertThat(type).contains(NewsFeedType.RESOURCE_SCENARIO);
    }

    @Test
    @DisplayName("Given an unknown Hub value, should return empty")
    void given_unknownHubValue_should_returnEmpty() {
      // Act
      var type = NewsFeedUtils.fromHubValue("RESOURCE_PLAYBOOK");

      // Assert
      assertThat(type).isEmpty();
    }

    @Test
    @DisplayName("Given a null value, should return empty")
    void given_nullValue_should_returnEmpty() {
      // Act
      var type = NewsFeedUtils.fromHubValue(null);

      // Assert
      assertThat(type).isEmpty();
    }
  }

  @Nested
  @DisplayName("isUnsubscribed")
  class IsUnsubscribed {

    @Test
    @DisplayName("Given no unsubscribed type, should not be unsubscribed")
    void given_noUnsubscribedType_should_notBeUnsubscribed() {
      // Act & Assert
      assertThat(NewsFeedUtils.isUnsubscribed(NewsFeedType.RESOURCE_SCENARIO, new String[0]))
          .isFalse();
    }

    @Test
    @DisplayName("Given the type in the unsubscribed list, should be unsubscribed")
    void given_typeInUnsubscribedList_should_beUnsubscribed() {
      // Act & Assert
      assertThat(
              NewsFeedUtils.isUnsubscribed(
                  NewsFeedType.RESOURCE_SCENARIO, new String[] {"RESOURCE_SCENARIO"}))
          .isTrue();
    }

    @Test
    @DisplayName("Given only other types unsubscribed, should not be unsubscribed")
    void given_otherTypesUnsubscribed_should_notBeUnsubscribed() {
      // Act & Assert
      assertThat(
              NewsFeedUtils.isUnsubscribed(
                  NewsFeedType.RESOURCE_SCENARIO, new String[] {"RESOURCE_PLAYBOOK"}))
          .isFalse();
    }

    @Test
    @DisplayName("Given a null unsubscribed list, should not be unsubscribed")
    void given_nullUnsubscribedList_should_notBeUnsubscribed() {
      // Act & Assert
      assertThat(NewsFeedUtils.isUnsubscribed(NewsFeedType.RESOURCE_SCENARIO, null)).isFalse();
    }

    @Test
    @DisplayName("Given the all-types marker, should be unsubscribed")
    void given_allTypesMarker_should_beUnsubscribed() {
      // Act & Assert
      assertThat(
              NewsFeedUtils.isUnsubscribed(
                  NewsFeedType.RESOURCE_SCENARIO, new String[] {NewsFeedUtils.ALL_TYPES}))
          .isTrue();
    }
  }

  @Nested
  @DisplayName("subscribedTypes")
  class SubscribedTypes {

    @Test
    @DisplayName("Given no unsubscribed type, should subscribe to every known type")
    void given_noUnsubscribedType_should_subscribeToEveryType() {
      // Act & Assert
      assertThat(NewsFeedUtils.subscribedTypes(new String[0]))
          .containsExactlyInAnyOrder(NewsFeedType.values());
    }

    @Test
    @DisplayName("Given the type unsubscribed, should not subscribe to it")
    void given_typeUnsubscribed_should_notSubscribeToIt() {
      // Act & Assert
      assertThat(NewsFeedUtils.subscribedTypes(new String[] {"RESOURCE_SCENARIO"})).isEmpty();
    }

    @Test
    @DisplayName("Given the all-types marker, should subscribe to nothing")
    void given_allTypesMarker_should_subscribeToNothing() {
      // Act & Assert
      assertThat(NewsFeedUtils.subscribedTypes(new String[] {NewsFeedUtils.ALL_TYPES})).isEmpty();
    }
  }

  @Nested
  @DisplayName("toUnsubscribedTypes")
  class ToUnsubscribedTypes {

    @Test
    @DisplayName("Given every type subscribed, should unsubscribe from nothing")
    void given_everyTypeSubscribed_should_unsubscribeFromNothing() {
      // Act & Assert
      assertThat(NewsFeedUtils.toUnsubscribedTypes(List.of(NewsFeedType.values()))).isEmpty();
    }

    @Test
    @DisplayName("Given no type subscribed, should store the all-types marker")
    void given_noTypeSubscribed_should_storeAllTypesMarker() {
      // Act & Assert
      assertThat(NewsFeedUtils.toUnsubscribedTypes(List.of()))
          .containsExactly(NewsFeedUtils.ALL_TYPES);
    }

    @Test
    @DisplayName("Given a stored value, should round-trip through subscribedTypes")
    void given_storedValue_should_roundTrip() {
      // Arrange
      List<NewsFeedType> subscribed = List.of(NewsFeedType.RESOURCE_SCENARIO);

      // Act
      String[] stored = NewsFeedUtils.toUnsubscribedTypes(subscribed);

      // Assert
      assertThat(NewsFeedUtils.subscribedTypes(stored)).isEqualTo(subscribed);
    }
  }
}
