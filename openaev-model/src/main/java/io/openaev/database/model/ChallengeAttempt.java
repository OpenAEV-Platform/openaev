package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import lombok.Getter;

@Data
@Entity
@Table(name = "challenge_attempts")
public class ChallengeAttempt {
  @EmbeddedId @JsonIgnore private ChallengeAttemptId compositeId = new ChallengeAttemptId();

  @Column(name = "challenge_attempt")
  @NotNull
  private int attempt;

  // -- AUDIT --

  @Getter
  @Column(name = "attempt_created_at")
  @JsonProperty("attempt_created_at")
  private Instant createdAt = now();

  @Getter
  @Column(name = "attempt_updated_at")
  @JsonProperty("attempt_updated_at")
  private Instant updatedAt = now();
}
