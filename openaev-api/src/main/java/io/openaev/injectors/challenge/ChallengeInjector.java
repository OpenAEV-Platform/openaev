package io.openaev.injectors.challenge;

import io.openaev.integrations.InjectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChallengeInjector {

  private static final String CHALLENGE_INJECTOR_NAME = "Challenges";
  private static final String CHALLENGE_INJECTOR_ID = "49229430-b5b5-431f-ba5b-f36f599b0233";

  @Autowired
  public ChallengeInjector(InjectorService injectorService, ChallengeContract contract) {
    try {
      injectorService.register(
          CHALLENGE_INJECTOR_ID,
          CHALLENGE_INJECTOR_NAME,
          contract,
          false,
          "capture-the-flag",
          null,
          null,
          false);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
