package io.openaev.rest.challenge;

import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Inject;
import io.openaev.injectors.challenge.model.ChallengeContent;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Stream;

public class ChallengeHelper {

  private ChallengeHelper() {}

  public static List<String> resolveChallengeIds(
      @NotNull final List<Inject> injects, ObjectMapper mapper) {
    return injects.stream()
        .filter(
            inject ->
                inject
                    .getInjectorContract()
                    .map(contract -> contract.getId().equals(CHALLENGE_PUBLISH))
                    .orElse(false))
        .filter(inject -> inject.getContent() != null)
        .flatMap(
            inject -> {
              try {
                ChallengeContent content =
                    mapper.treeToValue(inject.getContent(), ChallengeContent.class);
                return content.getChallenges().stream();
              } catch (JsonProcessingException e) {
                return Stream.empty();
              }
            })
        .distinct()
        .toList();
  }
}
