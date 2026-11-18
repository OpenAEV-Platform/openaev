package io.openaev.service.expectation;

import static io.openaev.injectors.phishing.service.PhishingTrackingService.NO_INTERACTION_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.injectors.phishing.PhishingContract;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("PhishingBehavior")
class PhishingBehaviorTest extends IntegrationTest {

  private static final Double EXPECTED_SCORE = 100.0;

  @Autowired private PhishingBehavior phishingBehavior;
  @Autowired private InjectExpectationRepository injectExpectationRepository;

  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;

  @BeforeEach
  void setUp() {
    teamComposer.reset();
    userComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
  }

  @Nested
  @DisplayName("initializeAndSaveInjectExpectationsFromExecutableInject")
  class InitializeAndSaveInjectExpectation {

    @Test
    @DisplayName("given team with players should start players and team as resisted")
    void given_team_with_players_should_start_players_and_team_as_resisted() {
      // Arrange
      Team team =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))
              .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))
              .persist()
              .get();
      Inject inject = persistInjectForTeam(team);

      // Act
      initializeExpectations(inject, team);

      // Assert — the expiration collector only fails expectations without a score
      List<TableTopInjectExpectation> saved = findExpectations(inject);
      List<TableTopInjectExpectation> players =
          saved.stream().filter(e -> e.getUser() != null).toList();
      assertThat(players)
          .hasSize(2)
          .allSatisfy(
              player -> {
                assertThat(player.getScore()).isEqualTo(EXPECTED_SCORE);
                assertThat(player.getResults()).hasSize(1);
                assertThat(player.getResults().getFirst().getResult())
                    .isEqualTo(NO_INTERACTION_MESSAGE);
              });
      TableTopInjectExpectation teamExpectation =
          saved.stream().filter(e -> e.getUser() == null).findFirst().orElseThrow();
      assertThat(teamExpectation.getScore()).isEqualTo(EXPECTED_SCORE);
      assertThat(teamExpectation.getResults()).isEmpty();
    }

    /** The behavior only handles the expectations of a phishing inject */
    private Inject persistInjectForTeam(Team team) {
      Injector phishingInjector =
          InjectorFixture.createInjector(
              UUID.randomUUID().toString(), "Phishing", PhishingContract.TYPE);
      return injectComposer
          .forInject(InjectFixture.getDefaultInject())
          .withInjectorContract(
              injectorContractComposer
                  .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                  .withInjector(phishingInjector))
          .withTeam(teamComposer.forTeam(team))
          .persist()
          .get();
    }

    private void initializeExpectations(Inject inject, Team team) {
      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(team), List.of(), List.of(), List.of(), List.of());
      ManualInjectExpectation template = new ManualInjectExpectation();
      template.setInject(inject);
      template.setExpectedScore(EXPECTED_SCORE);
      template.setExpirationTime(21600L);

      phishingBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
          executableInject, template, null);
      entityManager.flush();
    }

    private List<TableTopInjectExpectation> findExpectations(Inject inject) {
      return injectExpectationRepository.findAllByInjectId(inject.getId()).stream()
          .map(TableTopInjectExpectation.class::cast)
          .toList();
    }
  }
}
