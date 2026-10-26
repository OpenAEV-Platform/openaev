package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NewsFeedType tests")
class NewsFeedTypeTest {

  @Nested
  @DisplayName("fromHubValue")
  class FromHubValue {

    @Test
    @DisplayName("Given a known Hub value, should return the matching type")
    void given_knownHubValue_should_returnType() {
      // Act
      var type = NewsFeedType.fromHubValue("RESOURCE_SCENARIO");

      // Assert
      assertThat(type).contains(NewsFeedType.RESOURCE_SCENARIO);
    }

    @Test
    @DisplayName("Given an unknown Hub value, should return empty")
    void given_unknownHubValue_should_returnEmpty() {
      // Act
      var type = NewsFeedType.fromHubValue("RESOURCE_PLAYBOOK");

      // Assert
      assertThat(type).isEmpty();
    }

    @Test
    @DisplayName("Given a null value, should return empty")
    void given_nullValue_should_returnEmpty() {
      // Act
      var type = NewsFeedType.fromHubValue(null);

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
      assertThat(NewsFeedType.RESOURCE_SCENARIO.isUnsubscribed(new String[0])).isFalse();
    }

    @Test
    @DisplayName("Given the type in the unsubscribed list, should be unsubscribed")
    void given_typeInUnsubscribedList_should_beUnsubscribed() {
      // Act & Assert
      assertThat(NewsFeedType.RESOURCE_SCENARIO.isUnsubscribed(new String[] {"RESOURCE_SCENARIO"}))
          .isTrue();
    }

    @Test
    @DisplayName("Given only other types unsubscribed, should not be unsubscribed")
    void given_otherTypesUnsubscribed_should_notBeUnsubscribed() {
      // Act & Assert
      assertThat(NewsFeedType.RESOURCE_SCENARIO.isUnsubscribed(new String[] {"RESOURCE_PLAYBOOK"}))
          .isFalse();
    }

    @Test
    @DisplayName("Given the all-types marker, should be unsubscribed")
    void given_allTypesMarker_should_beUnsubscribed() {
      // Act & Assert
      assertThat(
              NewsFeedType.RESOURCE_SCENARIO.isUnsubscribed(new String[] {NewsFeedType.ALL_TYPES}))
          .isTrue();
    }
  }
}
