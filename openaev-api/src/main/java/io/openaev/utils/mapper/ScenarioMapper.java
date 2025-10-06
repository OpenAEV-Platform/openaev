package io.openaev.utils.mapper;

import io.openaev.database.model.Article;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.rest.document.form.RelatedEntityOutput;
import io.openaev.rest.scenario.form.ScenarioSimple;
import io.openaev.rest.scenario.response.ScenarioOutput;
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

  public ScenarioOutput toScenarioOutput(Scenario scenario) {
    ScenarioOutput scenarioOutput = new ScenarioOutput();
    scenarioOutput.setId(scenario.getId());
    scenarioOutput.setName(scenario.getName());
    scenarioOutput.setDescription(scenario.getDescription());
    scenarioOutput.setSubtitle(scenario.getSubtitle());
    scenarioOutput.setCategory(scenario.getCategory());
    scenarioOutput.setMainFocus(scenario.getMainFocus());
    scenarioOutput.setSeverity(scenario.getSeverity());
    scenarioOutput.setExternalReference(scenario.getExternalReference());
    scenarioOutput.setExternalUrl(scenario.getExternalUrl());
    scenarioOutput.setRecurrence(scenario.getRecurrence());
    scenarioOutput.setRecurrenceStart(scenario.getRecurrenceStart());
    scenarioOutput.setRecurrenceEnd(scenario.getRecurrenceEnd());
    scenarioOutput.setHeader(scenario.getHeader());
    scenarioOutput.setFooter(scenario.getFooter());
    scenarioOutput.setFrom(scenario.getFrom());
    scenarioOutput.setReplyTos(scenario.getReplyTos());
    scenarioOutput.setCreatedAt(scenario.getCreatedAt());
    scenarioOutput.setUpdatedAt(scenario.getUpdatedAt());
    scenarioOutput.setCustomDashboard(scenario.getCustomDashboard());
    scenarioOutput.setTeams(scenario.getTeams());
    scenarioOutput.setTeamUsers(scenario.getTeamUsers());
    scenarioOutput.setTags(scenario.getTags());
    scenarioOutput.setDocuments(scenario.getDocuments());
    scenarioOutput.setArticles(scenario.getArticles());
    scenarioOutput.setLessonsCategories(scenario.getLessonsCategories());
    scenarioOutput.setExercises(scenario.getExercises());
    scenarioOutput.setLessonsAnonymized(scenario.isLessonsAnonymized());
    scenarioOutput.setPlanners(scenario.getPlanners());
    scenarioOutput.setObservers(scenario.getObservers());
    scenarioOutput.setInjectStatistics(scenario.getInjectStatistics());
    scenarioOutput.setUsersAllNumber(scenario.usersAllNumber());
    scenarioOutput.setUsersNumber(scenario.usersNumber());
    scenarioOutput.setUsers(scenario.getUsers());
    scenarioOutput.setCommunicationsNumber(scenario.getCommunicationsNumber());
    scenarioOutput.setPlatforms(scenario.getPlatforms());
    scenarioOutput.setKillChainPhases(scenario.getKillChainPhases());
    return scenarioOutput;
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
