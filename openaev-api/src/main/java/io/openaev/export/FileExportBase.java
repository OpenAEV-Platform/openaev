package io.openaev.export;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.rest.exercise.exports.ExerciseFileExport;
import io.openaev.rest.exercise.exports.ExportOptions;
import io.openaev.rest.exercise.exports.VariableMixin;
import io.openaev.rest.exercise.exports.VariableWithValueMixin;
import io.openaev.rest.inject.exports.InjectsFileExport;
import io.openaev.service.ArticleService;
import io.openaev.service.ChallengeService;
import io.openaev.service.organization.OrganizationService;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

@Getter
public class FileExportBase {
  @JsonProperty("export_version")
  private int version = 1;

  @JsonIgnore protected int exportOptionsMask = ExportOptions.mask(false, false, false);

  @JsonIgnore public final ObjectMapper objectMapper;
  @JsonIgnore protected final ChallengeService challengeService;
  @JsonIgnore protected final ArticleService articleService;
  @JsonIgnore protected final OrganizationService organizationService;

  protected FileExportBase(
      ObjectMapper objectMapper,
      ChallengeService challengeService,
      ArticleService articleService,
      OrganizationService organizationService) {
    this.objectMapper = objectMapper;
    this.challengeService = challengeService;
    this.articleService = articleService;
    this.organizationService = organizationService;

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

    // Workflow (chaining) export mixins — Workflow mixin is set dynamically based on export options
    this.objectMapper.addMixIn(WorkflowScopeRule.class, Mixins.WorkflowScopeRuleExport.class);
    this.objectMapper.addMixIn(ScopeVariable.class, Mixins.ScopeVariableExport.class);
    this.objectMapper.addMixIn(Step.class, Mixins.StepExport.class);
    this.objectMapper.addMixIn(Condition.class, Mixins.ConditionExport.class);

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

  /**
   * Nulls, in a serialized export, every organization reference ({@code user_organization}, {@code
   * team_organization}) that points outside the organizations the export carries ({@code
   * <prefix>_organizations}). A user is platform-level and may belong to another tenant's
   * organization: the export leaves that organization out, and its id must not leak through the
   * reference either. The field is kept (null), since the importer reads it unconditionally.
   *
   * @param export the serialized export
   * @param prefix the export's key prefix ({@code exercise}, {@code scenario}, {@code inject})
   */
  public static void dropForeignOrganizationReferences(ObjectNode export, String prefix) {
    Set<String> exportedIds = new HashSet<>();
    export
        .path(prefix + "_organizations")
        .forEach(organization -> exportedIds.add(organization.path("organization_id").asText()));
    nullReferencesOutside(export.path(prefix + "_users"), "user_organization", exportedIds);
    nullReferencesOutside(export.path(prefix + "_teams"), "team_organization", exportedIds);
  }

  private static void nullReferencesOutside(
      JsonNode items, String referenceField, Set<String> exportedIds) {
    items.forEach(
        item -> {
          JsonNode reference = item.get(referenceField);
          if (reference != null
              && !reference.isNull()
              && !exportedIds.contains(reference.asText())) {
            ((ObjectNode) item).putNull(referenceField);
          }
        });
  }
}
