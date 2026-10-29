package io.openaev.notification.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Finding;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.ResourceType;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * A LIVE trigger filtered on the value of a finding re-checks its filter with the same query engine
 * as the search endpoints. If that filter could run on the cleartext, a trigger such as "value
 * contains motdepasse" would fire only on the right guess and confirm a secret the platform masks
 * on screen (#7893). The filter must only ever see the displayed value.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A notification trigger only matches what the platform displays of a finding value")
class NotificationFindingValueMatchingTest extends IntegrationTest {

  @Autowired private NotificationMatchingService matchingService;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    findingComposer.reset();
    injectComposer.reset();
  }

  private Finding persistCredentials(String value) {
    Finding finding =
        findingComposer
            .forFinding(FindingFixture.createCredentialsFinding(value))
            .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
            .persist()
            .get();
    entityManager.flush();
    entityManager.clear();
    return finding;
  }

  private ResolvedNotificationTrigger triggerOnValueContaining(String text) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey("finding_value");
    filter.setMode(Filters.FilterMode.and);
    filter.setOperator(Filters.FilterOperator.contains);
    filter.setValues(List.of(text));
    Filters.FilterGroup filters = new Filters.FilterGroup();
    filters.setMode(Filters.FilterMode.and);
    filters.setFilters(List.of(filter));
    return new ResolvedNotificationTrigger(
        UUID.randomUUID().toString(),
        "finding value trigger",
        NotificationTriggerType.LIVE,
        ResourceType.FINDING,
        Set.of(NotificationTriggerEventType.CREATE),
        filters,
        null,
        null,
        null,
        List.of(),
        null,
        List.of(),
        List.of());
  }

  @Test
  @DisplayName("Should not match a trigger filtered on the hidden part of the value")
  void given_aTriggerOnTheHiddenSecret_should_notMatch() {
    // Arrange
    Finding finding = persistCredentials("admin:motdepasse");

    // Act
    boolean matches =
        matchingService.matches(
            triggerOnValueContaining("motdepasse"),
            NotificationResourceCatalog.FINDING,
            finding.getId());

    // Assert
    assertThat(matches).isFalse();
  }

  @Test
  @DisplayName("Should match a trigger filtered on a displayed part of the value")
  void given_aTriggerOnADisplayedPart_should_match() {
    // Arrange
    Finding finding = persistCredentials("admin:motdepasse");

    // Act
    boolean matches =
        matchingService.matches(
            triggerOnValueContaining("admin"),
            NotificationResourceCatalog.FINDING,
            finding.getId());

    // Assert
    assertThat(matches).isTrue();
  }
}
