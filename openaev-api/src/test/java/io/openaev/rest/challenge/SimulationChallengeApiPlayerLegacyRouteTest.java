package io.openaev.rest.challenge;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static io.openaev.utils.fixtures.UrlAccessTokenFixture.DEFAULT_RAW_TOKEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins the anonymous player challenges page on the LEGACY, non-tenant-prefixed route once {@code
 * challenges} is v2-active (#6416).
 *
 * <p>{@code /api/player/simulations/{simulationId}/challenges} has no {@code {tenantId}} path
 * variable of its own: it is the route a bookmarked player link, or any non-SPA caller, still
 * reaches directly (the frontend's own {@code buildTenantApiPath} always prefixes its own calls
 * with a tenant segment, so it never exercises this exact mapping). For such an anonymous caller,
 * {@code TxCtxArgumentResolver#anonymousScope} resolves {@code TxCtx.missing()} (deny-all), same as
 * the caller-authorized path with no selector.
 *
 * <p>{@code ChallengeService#playerChallenges} used to call a setter on the {@code Challenge}
 * association returned by {@code ChallengeInjectExpectation#getChallenge()}, which the backing
 * query did not fetch-join: a plain LAZY proxy, force-initialized outside any tenant-scoped read
 * shape. Under a real (single) tenant this silently worked because the platform-wide {@code
 * TenantContext} default resolves to it; under a genuinely missing scope it fails closed and used
 * to surface as a hard error instead of an empty result.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=challenges")
@DisplayName(
    "SimulationChallengeApi#playerChallenges on the legacy anonymous route, challenges v2-active")
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
      "given an anonymous caller with a valid URL access token on the legacy route, the response is 200 with no visible challenge, not a 500")
  void given_anonymousCallerOnLegacyRoute_should_returnEmptyInsteadOfCrashing() throws Exception {
    // -- ARRANGE --
    // No explicit tenant is set: TenantBaseListener stamps the platform default tenant, mirroring
    // the common single-tenant / Community Edition deployment this route must keep serving.
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
    // the challenges join fails closed. Before the fix this forced a lazy load on the unfetched
    // Challenge proxy and threw; the query is now scoped up front, so the reader just comes back
    // with no visible challenge instead of crashing.
    mvc.perform(get(url).cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exercise_challenges").isEmpty());
  }
}
