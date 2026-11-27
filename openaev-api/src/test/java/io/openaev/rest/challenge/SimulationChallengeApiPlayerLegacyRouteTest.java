package io.openaev.rest.challenge;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static io.openaev.utils.fixtures.UrlAccessTokenFixture.DEFAULT_RAW_TOKEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.ChallengeInjectExpectation;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.User;
import io.openaev.utils.fixtures.ChallengeFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UrlAccessTokenFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.ChallengeComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.fixtures.composers.UrlAccessTokenComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins the anonymous player challenges page on the route that names no tenant, with {@code
 * challenges} and {@code exercises} v2-active (#6416).
 *
 * <p>{@code /api/player/simulations/{simulationId}/challenges} has no {@code {tenantId}} path
 * variable of its own. The platform emits no link that reaches it: every player link carries the
 * simulation's tenant, and the frontend's {@code buildTenantApiPath} prefixes every API call with
 * it. For an anonymous caller on this route, {@code TxCtx#anonymousScope} resolves {@code
 * TxCtx.missing()} (deny-all), so the simulation is not found and the answer is a 404. The
 * tenant-prefixed route is covered by {@code PlayerUrlTokenRoutesTenantPathTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("SimulationChallengeApi#playerChallenges on the route that names no tenant")
class SimulationChallengeApiPlayerLegacyRouteTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ChallengeComposer challengeComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UrlAccessTokenComposer urlAccessTokenComposer;

  @BeforeEach
  void reset() {
    challengeComposer.reset();
    exerciseComposer.reset();
    injectComposer.reset();
    injectStatusComposer.reset();
    injectExpectationComposer.reset();
    userComposer.reset();
    teamComposer.reset();
    urlAccessTokenComposer.reset();
  }

  @Test
  @DisplayName(
      "given an anonymous caller with a valid URL access token on the route that names no tenant,"
          + " the simulation is not found")
  void given_anonymousCallerOnRouteWithNoTenant_should_answerNotFound() throws Exception {
    // -- ARRANGE --
    // No explicit tenant is set: TenantBaseListener stamps the platform default tenant.
    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    challengeComposer.forChallenge(challenge).persist();

    User player = UserFixture.getUser();
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    InjectStatus injectStatus = InjectStatusFixture.createSuccessStatus();
    ChallengeInjectExpectation expectation =
        InjectExpectationFixture.createChallengeInjectExpectation(challenge, player, exercise);

    injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withExercise(exerciseComposer.forExercise(exercise))
        .withInjectStatus(injectStatusComposer.forInjectStatus(injectStatus))
        .withExpectation(
            injectExpectationComposer
                .forExpectation(expectation)
                .withUser(userComposer.forUser(player))
                .withTeam(teamComposer.forTeam(TeamFixture.getTeam(player))))
        .persist();

    String url =
        "/api/player/simulations/" + exercise.getId() + "/challenges?userId=" + player.getId();
    urlAccessTokenComposer
        .forToken(UrlAccessTokenFixture.createValidToken(exercise, player, url))
        .persist();
    entityManager.flush();
    entityManager.clear();

    // -- ACT & ASSERT --
    // Anonymous: no @WithMockUser. TxCtx resolves to missing() on this route (no path tenant), so
    // the simulation is not found in any scope.
    mvc.perform(get(url).cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN)))
        .andExpect(status().isNotFound());
  }
}
