package io.openaev.rest.scenario;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UrlAccessControl;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.challenge.response.ChallengeInformation;
import io.openaev.rest.challenge.response.ScenarioChallengesReader;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.security.error.AuthenticationError;
import io.openaev.service.ChallengeService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScenarioChallengesApi extends RestBehavior {

  private final ScenarioRepository scenarioRepository;
  private final UserRepository userRepository;

  private final DocumentService documentService;
  private final ChallengeService challengeService;

  public List<Document> getScenarioPlayerDocuments(Scenario scenario) {
    List<Article> articles = scenario.getArticles();
    List<Inject> injects = scenario.getInjects();
    return documentService.getPlayerDocuments(articles, injects);
  }

  @GetMapping({
    "/api/player/scenarios/{scenarioId}/documents",
    TENANT_PREFIX + "/player/scenarios/{scenarioId}/documents"
  })
  @Transactional
  @AccessControl(skipRBAC = true)
  @UrlAccessControl(userId = "#userId")
  public List<Document> playerDocuments(
      @PathVariable String scenarioId, @RequestParam Optional<String> userId)
      throws AuthenticationError {
    Optional<Scenario> scenarioOpt =
        this.scenarioRepository.findByIdAndTenantId(scenarioId, TenantContext.getCurrentTenant());
    final User user = impersonateUser(userRepository, userId);
    if (scenarioOpt.isPresent()) {
      if (!scenarioOpt.get().isUserHasAccess(user)
          && !scenarioOpt.get().getUsers().contains(user)) {
        throw new AuthenticationError("The given player is not in this exercise");
      }
      return getScenarioPlayerDocuments(scenarioOpt.get());
    } else {
      throw new IllegalArgumentException("Scenario ID not found");
    }
  }

  @GetMapping({
    "/api/observer/scenarios/{scenarioId}/challenges",
    TENANT_PREFIX + "/observer/scenarios/{scenarioId}/challenges"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ScenarioChallengesReader observerChallenges(@PathVariable String scenarioId) {
    Scenario scenario =
        scenarioRepository
            .findByIdAndTenantId(scenarioId, TenantContext.getCurrentTenant())
            .orElseThrow(ElementNotFoundException::new);
    ScenarioChallengesReader scenarioChallengesReader = new ScenarioChallengesReader(scenario);
    Iterable<Challenge> challenges = challengeService.getScenarioChallenges(scenario);
    scenarioChallengesReader.setScenarioChallenges(
        fromIterable(challenges).stream()
            .map(challenge -> new ChallengeInformation(challenge, null, 0))
            .toList());
    return scenarioChallengesReader;
  }
}
