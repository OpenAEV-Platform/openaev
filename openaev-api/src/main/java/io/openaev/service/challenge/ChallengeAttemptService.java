package io.openaev.service.challenge;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.challenge.ChallengeAttemptUtils.buildChallengeAttemptID;

import io.openaev.database.model.ChallengeAttempt;
import io.openaev.database.model.ChallengeAttemptId;
import io.openaev.database.repository.ChallengeAttemptRepository;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChallengeAttemptService {

  private final ChallengeAttemptRepository challengeAttemptRepository;

  // -- CRUD --

  public Optional<ChallengeAttempt> getChallengeAttempt(@NotNull final ChallengeAttemptId id) {
    return this.challengeAttemptRepository.findById(id);
  }

  public List<ChallengeAttempt> getChallengeAttempts(@NotNull final List<ChallengeAttemptId> ids) {
    return fromIterable(this.challengeAttemptRepository.findAllById(ids));
  }

  public Optional<ChallengeAttempt> getChallengeAttempt(
      @NotBlank final String challengeId,
      @NotBlank final String injectStatusId,
      @NotBlank final String userId) {
    ChallengeAttemptId challengeAttemptId =
        buildChallengeAttemptID(challengeId, injectStatusId, userId);
    return this.challengeAttemptRepository.findById(challengeAttemptId);
  }

  public ChallengeAttempt saveChallengeAttempt(@NotNull final ChallengeAttempt challengeAttempt) {
    return this.challengeAttemptRepository.save(challengeAttempt);
  }

  public Iterable<ChallengeAttempt> saveChallengeAttempts(
      @NotNull final List<ChallengeAttempt> challengeAttempts) {
    return this.challengeAttemptRepository.saveAll(challengeAttempts);
  }

  public void deleteChallengeAttempt(@NotNull final ChallengeAttemptId id) {
    this.challengeAttemptRepository.deleteById(id);
  }
}
