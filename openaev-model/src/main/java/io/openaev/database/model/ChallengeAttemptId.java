package io.openaev.database.model;

import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
@Embeddable
public class ChallengeAttemptId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String challengeId;
  private String injectStatusId;
  private String userId;
}
