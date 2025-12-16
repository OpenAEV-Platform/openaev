package io.openaev.utils.mapper;

import io.openaev.database.model.*;
import io.openaev.database.raw.RawScenarioQuery;
import io.openaev.dto.KillChainPhaseDTO;
import io.openaev.dto.ScenarioDTO;
import io.openaev.dto.ScenarioTeamUserDTO;
import io.openaev.rest.document.form.RelatedEntityOutput;
import io.openaev.rest.scenario.form.ScenarioSimple;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ScenarioMapper {

  public ScenarioSimple toScenarioSimple(@NotNull final Scenario scenario) {
    ScenarioSimple simple = new ScenarioSimple();
    BeanUtils.copyProperties(scenario, simple);
    return simple;
  }

  public static Set<RelatedEntityOutput> toScenarioArticles(Set<Article> articles) {
    return articles.stream().map(article -> toScenarioArticle(article)).collect(Collectors.toSet());
  }

  public ScenarioDTO toScenarioDTO(
      RawScenarioQuery rawScenarioQuery,
      Set<KillChainPhaseDTO> killChainPhases,
      Set<ScenarioTeamUserDTO> scenarioTeamUsers) {
    ScenarioDTO scenario = new ScenarioDTO();
    scenario.setId(rawScenarioQuery.getScenario_id());
    scenario.setName(rawScenarioQuery.getScenario_name());
    scenario.setCategory(rawScenarioQuery.getScenario_category());
    scenario.setCreatedAt(rawScenarioQuery.getScenario_created_at());
    scenario.setUpdatedAt(rawScenarioQuery.getScenario_updated_at());
    scenario.setCustomDashboard(rawScenarioQuery.getScenario_custom_dashboard());
    scenario.setDescription(rawScenarioQuery.getScenario_description());
    scenario.setExternalUrl(rawScenarioQuery.getScenario_external_url());
    scenario.setLessonsAnonymized(rawScenarioQuery.getScenario_lessons_anonymized());
    scenario.setFrom(rawScenarioQuery.getScenario_mail_from());
    scenario.setMainFocus(rawScenarioQuery.getScenario_main_focus());
    scenario.setFooter(rawScenarioQuery.getScenario_message_footer());
    scenario.setHeader(rawScenarioQuery.getScenario_message_header());
    scenario.setRecurrence(rawScenarioQuery.getScenario_recurrence());
    scenario.setRecurrenceStart(rawScenarioQuery.getScenario_recurrence_start());
    scenario.setRecurrenceEnd(rawScenarioQuery.getScenario_recurrence_end());
    scenario.setSubtitle(rawScenarioQuery.getScenario_subtitle());
    scenario.setDependencies(rawScenarioQuery.getScenario_dependencies());
    scenario.setSeverity(rawScenarioQuery.getScenario_severity());
    scenario.setExercises(rawScenarioQuery.getScenario_exercises());
    scenario.setKillChainPhases(killChainPhases);
    scenario.setPlatforms(rawScenarioQuery.getScenario_platforms());
    scenario.setTags(rawScenarioQuery.getScenario_tags());
    scenario.setTeamUsers(scenarioTeamUsers);
    scenario.setScenarioUsersNumber(rawScenarioQuery.getScenario_users_number());
    scenario.setScenarioAllUsersNumber(rawScenarioQuery.getScenario_all_users_number());
    return scenario;
  }

  private static RelatedEntityOutput toScenarioArticle(Article article) {
    return RelatedEntityOutput.builder()
        .id(article.getId())
        .name(article.getName())
        .context(article.getScenario().getId())
        .build();
  }

  public static Set<RelatedEntityOutput> toScenarioInjects(Set<Inject> injects) {
    return injects.stream().map(inject -> toScenarioInject(inject)).collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toScenarioInject(Inject inject) {
    return RelatedEntityOutput.builder()
        .id(inject.getId())
        .name(inject.getTitle())
        .context(inject.getScenario().getId())
        .build();
  }
}
