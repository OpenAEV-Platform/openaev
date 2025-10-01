package io.openaev.export;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.rest.exercise.exports.ExerciseFileExport;
import io.openaev.rest.exercise.exports.ExportOptions;
import io.openaev.rest.exercise.exports.VariableMixin;
import io.openaev.rest.exercise.exports.VariableWithValueMixin;
import io.openaev.rest.inject.exports.InjectsFileExport;
import io.openaev.service.ArticleService;
import io.openaev.service.ChallengeService;
import lombok.Getter;

@Getter
public class FileExportBase {
  @JsonProperty("export_version")
  private int version = 1;

  @JsonIgnore protected int exportOptionsMask = ExportOptions.mask(false, false, false);

  @JsonIgnore public final ObjectMapper objectMapper;
  @JsonIgnore protected final ChallengeService challengeService;
  @JsonIgnore protected final ArticleService articleService;

  protected FileExportBase(
      ObjectMapper objectMapper, ChallengeService challengeService, ArticleService articleService) {
    this.objectMapper = objectMapper;
    this.challengeService = challengeService;
    this.articleService = articleService;

    this.objectMapper.addMixIn(Base.class, Mixins.Base.class);
    this.objectMapper.addMixIn(Exercise.class, Mixins.Exercise.class);
    this.objectMapper.addMixIn(Document.class, Mixins.Document.class);
    this.objectMapper.addMixIn(Objective.class, Mixins.Objective.class);
    this.objectMapper.addMixIn(LessonsCategory.class, Mixins.LessonsCategory.class);
    this.objectMapper.addMixIn(LessonsQuestion.class, Mixins.LessonsQuestion.class);
    this.objectMapper.addMixIn(User.class, Mixins.User.class);
    this.objectMapper.addMixIn(Organization.class, Mixins.Organization.class);
    this.objectMapper.addMixIn(Inject.class, Mixins.Inject.class);
    this.objectMapper.addMixIn(Article.class, Mixins.Article.class);
    this.objectMapper.addMixIn(Channel.class, Mixins.Channel.class);
    this.objectMapper.addMixIn(Challenge.class, Mixins.Challenge.class);
    this.objectMapper.addMixIn(Tag.class, Mixins.Tag.class);
    this.objectMapper.addMixIn(InjectorContract.class, Mixins.InjectorContract.class);
    this.objectMapper.addMixIn(AttackPattern.class, Mixins.AttackPattern.class);
    this.objectMapper.addMixIn(Payload.class, Mixins.Payload.class);
    this.objectMapper.addMixIn(KillChainPhase.class, Mixins.KillChainPhase.class);

    // default options
    // variables with no value
    this.objectMapper.addMixIn(Variable.class, VariableMixin.class);
    // empty teams
    this.objectMapper.addMixIn(Team.class, Mixins.EmptyTeam.class);
  }

  public FileExportBase withOptions(int exportOptionsMask) {
    this.exportOptionsMask = exportOptionsMask;

    // disable users if not requested; note negation
    if (!ExportOptions.has(ExportOptions.WITH_PLAYERS, this.exportOptionsMask)) {
      this.objectMapper.addMixIn(ExerciseFileExport.class, Mixins.ExerciseFileExport.class);
      this.objectMapper.addMixIn(InjectsFileExport.class, Mixins.InjectsFileExport.class);
    }

    if (ExportOptions.has(ExportOptions.WITH_TEAMS, this.exportOptionsMask)) {
      this.objectMapper.addMixIn(
          Team.class,
          ExportOptions.has(ExportOptions.WITH_PLAYERS, this.exportOptionsMask)
              ? Mixins.Team.class
              : Mixins.EmptyTeam.class);
    }
    if (ExportOptions.has(ExportOptions.WITH_VARIABLE_VALUES, this.exportOptionsMask)) {
      this.objectMapper.addMixIn(Variable.class, VariableWithValueMixin.class);
    } else {
      this.objectMapper.addMixIn(Variable.class, VariableMixin.class);
    }
    return this;
  }
}
