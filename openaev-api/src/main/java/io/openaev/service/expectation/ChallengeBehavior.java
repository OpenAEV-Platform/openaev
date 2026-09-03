package io.openaev.service.expectation;

import static io.openaev.helper.StreamHelper.fromIterable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.injectors.challenge.model.ChallengeContent;
import io.openaev.service.InjectExpectationUtils;
import io.openaev.utils.challenge.ChallengeExpectationUtils;
import java.util.List;
import org.springframework.stereotype.Component;

/** Behavior implementation for {@link ChallengeInjectExpectation}. */
@Component
public class ChallengeBehavior extends AbstractTableTopBehavior {

  private final ChallengeRepository challengeRepository;
  private final ObjectMapper mapper;

  public ChallengeBehavior(
      InjectExpectationRepository injectExpectationRepository,
      ChallengeRepository challengeRepository,
      ObjectMapper mapper) {
    super(injectExpectationRepository);
    this.challengeRepository = challengeRepository;
    this.mapper = mapper;
  }

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof ChallengeInjectExpectation;
  }

  @Override
  public boolean supportsFormExpectationType(BaseInjectExpectation.EXPECTATION_TYPE type) {
    return type == BaseInjectExpectation.EXPECTATION_TYPE.CHALLENGE;
  }

  // TODO /!\ /!\ : The UI needs to be fixed: when the score and result are initialized to
  //  null, the user can no longer validate the flag.
  @Override
  protected InjectExpectationResult buildDefaultPlayerResult(Double expectedScore) {
    return ChallengeExpectationUtils.buildDefaultChallengeInjectExpectationResult();
  }

  @Override
  public ChallengeInjectExpectation convertFormExpectationToBaseInjectExpectation(
      io.openaev.model.inject.form.Expectation formExpectation, Exercise exercise, Inject inject) {
    ChallengeInjectExpectation challengeExpectation = new ChallengeInjectExpectation();
    InjectExpectationUtils.setCommonFields(
        challengeExpectation, formExpectation, exercise, inject, this.expectationPropertiesConfig);
    return challengeExpectation;
  }

  /**
   * Expands the challenge template into one template per challenge referenced by the inject
   * content, so each challenge gets its own expectation tree.
   */
  @Override
  protected List<TableTopInjectExpectation> expandTemplatesForContext(
      ExecutableInject executableInject, TableTopInjectExpectation template) {
    return resolveChallenges(executableInject).stream()
        .map(
            challenge -> {
              ChallengeInjectExpectation expectation =
                  (ChallengeInjectExpectation) template.clone();
              expectation.setChallenge(challenge);
              expectation.setName(challenge.getName());
              return (TableTopInjectExpectation) expectation;
            })
        .toList();
  }

  private List<Challenge> resolveChallenges(ExecutableInject executableInject) {
    List<Challenge> cached = executableInject.getExpectationContext(Challenge.class);
    if (!cached.isEmpty()) {
      return cached;
    }
    try {
      ChallengeContent content =
          mapper.treeToValue(
              executableInject.getInjection().getInject().getContent(), ChallengeContent.class);
      return fromIterable(challengeRepository.findAllById(content.getChallenges()));
    } catch (Exception e) {
      return List.of();
    }
  }
}
