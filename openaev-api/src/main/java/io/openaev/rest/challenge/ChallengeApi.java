package io.openaev.rest.challenge;

import static io.openaev.database.specification.ChallengeSpecification.fromIds;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.database.model.ChallengeFlag.FLAG_TYPE;
import io.openaev.database.raw.RawDocument;
import io.openaev.database.repository.*;
import io.openaev.rest.challenge.form.ChallengeInput;
import io.openaev.rest.challenge.form.ChallengeTryInput;
import io.openaev.rest.challenge.response.ChallengeResult;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.InputValidationException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.ChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ChallengeApi extends RestBehavior {

  private final ChallengeRepository challengeRepository;
  private final ChallengeFlagRepository challengeFlagRepository;
  private final TagRepository tagRepository;
  private final DocumentRepository documentRepository;
  private final ChallengeService challengeService;
  private final DocumentService documentService;

  @GetMapping("/api/challenges")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.CHALLENGE)
  public Iterable<Challenge> challenges() {
    return fromIterable(challengeRepository.findAll()).stream()
        .map(challengeService::enrichChallengeWithExercisesOrScenarios)
        .toList();
  }

  @LogExecutionTime
  @PostMapping("/api/challenges/find")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.CHALLENGE)
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public List<Challenge> findEndpoints(
      @RequestBody @Valid @NotNull final List<String> challengeIds) {
    return this.challengeRepository.findAll(fromIds(challengeIds));
  }

  @PutMapping("/api/challenges/{challengeId}")
  @RBAC(
      resourceId = "#challengeId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.CHALLENGE)
  @Transactional(rollbackOn = Exception.class)
  public Challenge updateChallenge(
      @PathVariable String challengeId, @Valid @RequestBody ChallengeInput input) {
    Challenge challenge =
        challengeRepository.findById(challengeId).orElseThrow(ElementNotFoundException::new);
    challenge.setTags(iterableToSet(tagRepository.findAllById(input.tagIds())));
    challenge.setDocuments(fromIterable(documentRepository.findAllById(input.documentIds())));
    challenge.setUpdateAttributes(input);
    challenge.setUpdatedAt(Instant.now());
    // Clear all flags
    List<ChallengeFlag> challengeFlags = challenge.getFlags();
    challengeFlagRepository.deleteAll(challengeFlags);
    challengeFlags.clear();
    // Add new ones
    input
        .flags()
        .forEach(
            flagInput -> {
              ChallengeFlag challengeFlag = new ChallengeFlag();
              challengeFlag.setType(FLAG_TYPE.valueOf(flagInput.getType()));
              challengeFlag.setValue(flagInput.getValue());
              challengeFlag.setChallenge(challenge);
              challengeFlags.add(challengeFlag);
            });
    Challenge saveChallenge = challengeRepository.save(challenge);
    return challengeService.enrichChallengeWithExercisesOrScenarios(saveChallenge);
  }

  @PostMapping("/api/challenges")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.CHALLENGE)
  @Transactional(rollbackOn = Exception.class)
  public Challenge createChallenge(@Valid @RequestBody ChallengeInput input) {
    Challenge challenge = new Challenge();
    challenge.setUpdateAttributes(input);
    challenge.setTags(iterableToSet(tagRepository.findAllById(input.tagIds())));
    challenge.setDocuments(fromIterable(documentRepository.findAllById(input.documentIds())));
    List<ChallengeFlag> challengeFlags =
        input.flags().stream()
            .map(
                flagInput -> {
                  ChallengeFlag challengeFlag = new ChallengeFlag();
                  challengeFlag.setType(FLAG_TYPE.valueOf(flagInput.getType()));
                  challengeFlag.setValue(flagInput.getValue());
                  challengeFlag.setChallenge(challenge);
                  return challengeFlag;
                })
            .toList();
    challenge.setFlags(challengeFlags);
    return challengeRepository.save(challenge);
  }

  @DeleteMapping("/api/challenges/{challengeId}")
  @RBAC(
      resourceId = "#challengeId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.CHALLENGE)
  @Transactional(rollbackOn = Exception.class)
  public void deleteChallenge(@PathVariable String challengeId) {
    challengeRepository.deleteById(challengeId);
  }

  @PostMapping("/api/challenges/{challengeId}/try")
  @RBAC(
      resourceId = "#challengeId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.CHALLENGE)
  public ChallengeResult tryChallenge(
      @PathVariable String challengeId, @Valid @RequestBody ChallengeTryInput input)
      throws InputValidationException {
    validateUUID(challengeId);
    return challengeService.tryChallenge(challengeId, input);
  }

  @GetMapping("/api/challenges/{challengeId}/documents")
  @RBAC(
      resourceId = "#challengeId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.CHALLENGE)
  @Operation(summary = "Get the Documents used in a challenge")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of Documents used in the Challenge")
      })
  public List<RawDocument> documentsFromChallenge(@PathVariable String challengeId) {
    return documentService.documentsForChallenge(challengeId);
  }
}
