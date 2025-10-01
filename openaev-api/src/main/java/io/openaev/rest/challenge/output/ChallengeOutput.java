package io.openaev.rest.challenge.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.ChallengeFlag;
import io.openaev.database.model.Document;
import io.openaev.database.model.Tag;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import org.springframework.util.CollectionUtils;

@Data
public class ChallengeOutput {

  @JsonProperty("challenge_id")
  @NotBlank
  private String id;

  @JsonProperty("challenge_name")
  private String name;

  @JsonProperty("challenge_category")
  private String category;

  @JsonProperty("challenge_content")
  private String content;

  @JsonProperty("challenge_score")
  private Double score;

  @JsonProperty("challenge_max_attempts")
  private Integer maxAttempts;

  @JsonProperty("challenge_flags")
  private List<ChallengeFlag> flags = new ArrayList<>();

  @JsonProperty("challenge_tags")
  private Set<String> tags = new HashSet<>();

  @JsonProperty("challenge_documents")
  private List<String> documents = new ArrayList<>();

  @JsonProperty("challenge_exercises")
  private List<String> exerciseIds = new ArrayList<>();

  @JsonProperty("challenge_scenarios")
  private List<String> scenarioIds = new ArrayList<>();

  public static ChallengeOutput from(@org.jetbrains.annotations.NotNull final Challenge challenge) {
    ChallengeOutput challengeOutput = new ChallengeOutput();
    challengeOutput.setId(challenge.getId());
    challengeOutput.setName(challenge.getName());
    challengeOutput.setCategory(challenge.getCategory());
    challengeOutput.setContent(challenge.getContent());
    challengeOutput.setScore(challenge.getScore());
    challengeOutput.setMaxAttempts(challenge.getMaxAttempts());
    if (!CollectionUtils.isEmpty(challenge.getFlags())) {
      challengeOutput.setFlags(challenge.getFlags());
    }
    challengeOutput.setTags(
        challenge.getTags().stream().map(Tag::getId).collect(Collectors.toSet()));
    challengeOutput.setDocuments(challenge.getDocuments().stream().map(Document::getId).toList());
    return challengeOutput;
  }
}
