package io.openaev.utils.mapper;

import io.openaev.database.model.*;
import io.openaev.database.raw.RawScenario;
import io.openaev.rest.document.form.RelatedEntityOutput;
import io.openaev.rest.kill_chain_phase.response.KillChainPhaseOutput;
import io.openaev.rest.scenario.form.ScenarioSimple;
import io.openaev.rest.scenario.response.ScenarioOutput;
import io.openaev.rest.scenario.response.ScenarioTeamUserOutput;
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

  public ScenarioOutput toScenarioOutput(
      RawScenario rawScenario,
      Set<KillChainPhaseOutput> killChainPhases,
      Set<ScenarioTeamUserOutput> scenarioTeamUsers) {
    return ScenarioOutput.builder()
        .id(rawScenario.getScenario_id())
        .name(rawScenario.getScenario_name())
        .category(rawScenario.getScenario_category())
        .createdAt(rawScenario.getScenario_created_at())
        .updatedAt(rawScenario.getScenario_updated_at())
        .customDashboard(rawScenario.getScenario_custom_dashboard())
        .description(rawScenario.getScenario_description())
        .externalUrl(rawScenario.getScenario_external_url())
        .lessonsAnonymized(rawScenario.getScenario_lessons_anonymized())
        .from(rawScenario.getScenario_mail_from())
        .mainFocus(rawScenario.getScenario_main_focus())
        .footer(rawScenario.getScenario_message_footer())
        .header(rawScenario.getScenario_message_header())
        .recurrence(rawScenario.getScenario_recurrence())
        .recurrenceStart(rawScenario.getScenario_recurrence_start())
        .recurrenceEnd(rawScenario.getScenario_recurrence_end())
        .subtitle(rawScenario.getScenario_subtitle())
        .dependencies(rawScenario.getScenario_dependencies())
        .severity(rawScenario.getScenario_severity())
        .exercises(rawScenario.getScenario_exercises())
        .killChainPhases(killChainPhases)
        .platforms(rawScenario.getScenario_platforms())
        .tags(rawScenario.getScenario_tags())
        .teamUsers(scenarioTeamUsers)
        .scenarioUsersNumber(rawScenario.getScenario_users_number())
        .scenarioAllUsersNumber(rawScenario.getScenario_all_users_number())
        .build();
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
