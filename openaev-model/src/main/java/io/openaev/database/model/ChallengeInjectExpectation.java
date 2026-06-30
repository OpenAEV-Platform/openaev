package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@DiscriminatorValue(BaseInjectExpectation.EXPECTATION_TYPE.CHALLENGE_VALUE)
public class ChallengeInjectExpectation extends TableTopInjectExpectation {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "challenge_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_challenge")
  @Schema(implementation = String.class)
  private Challenge challenge;
}
