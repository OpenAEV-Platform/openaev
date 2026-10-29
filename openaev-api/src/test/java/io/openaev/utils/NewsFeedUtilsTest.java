package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.NewsFeedType;
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
}
