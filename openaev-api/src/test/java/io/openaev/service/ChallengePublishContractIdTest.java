package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.openaev.database.repository.InjectRepository;
import io.openaev.injectors.challenge.ChallengeContract;
import io.openaev.integration.impl.injectors.challenge.ChallengeInjectorIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the lookup feeding challenge_exercises and challenge_scenarios (#8060): the injects
 * publishing a challenge are found by their contract id, which inject_injector_contract holds.
 */
class ChallengePublishContractIdTest {

  @Test
  @DisplayName("Challenge inject lookup filters on the challenge publication contract id")
  void lookupUsesThePublicationContractId() {
    assertEquals(
        ChallengeContract.CHALLENGE_PUBLISH, InjectRepository.CHALLENGE_PUBLISH_CONTRACT_ID);
  }

  @Test
  @DisplayName("Challenge inject lookup never filters on the challenge injector id")
  void lookupDoesNotUseTheInjectorId() {
    assertNotEquals(
        ChallengeInjectorIntegration.CHALLENGE_INJECTOR_ID,
        InjectRepository.CHALLENGE_PUBLISH_CONTRACT_ID);
  }
}
