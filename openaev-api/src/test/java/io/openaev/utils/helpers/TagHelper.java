package io.openaev.utils.helpers;

import io.openaev.database.model.*;
import io.openaev.service.ChallengeService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class TagHelper {
  public static List<Tag> crawlAllExerciseTags(
      Exercise exercise, ChallengeService challengeService) {
    List<Tag> tags = new ArrayList<>(exercise.getTags());
    tags.addAll(exercise.getTeams().stream().flatMap(team -> team.getTags().stream()).toList());
    tags.addAll(
        exercise.getTeams().stream()
            .flatMap(team -> team.getUsers().stream().flatMap(user -> user.getTags().stream()))
            .toList());
    tags.addAll(
        exercise.getTeams().stream()
            .flatMap(
                team ->
                    team.getUsers().stream()
                        .flatMap(
                            user ->
                                user.getOrganization() == null
                                    ? null
                                    : user.getOrganization().getTags().stream()
                                        .filter(Objects::nonNull)))
            .toList());
    tags.addAll(
        exercise.getDocuments().stream().flatMap(document -> document.getTags().stream()).toList());
    tags.addAll(
        exercise.getInjects().stream().flatMap(inject -> inject.getTags().stream()).toList());
    List<Challenge> challenges = new ArrayList<>();
    for (Challenge challenge : challengeService.getExerciseChallenges(exercise.getId())) {
      challenges.add(challenge);
    }
    tags.addAll(challenges.stream().flatMap(challenge -> challenge.getTags().stream()).toList());
    tags.addAll(
        exercise.getInjects().stream()
            .flatMap(
                inject ->
                    inject.getInjectorContract().isPresent()
                        ? inject.getInjectorContract().get().getPayload() != null
                            ? inject.getInjectorContract().get().getPayload().getTags().stream()
                            : Stream.of()
                        : Stream.of())
            .toList());
    return tags;
  }

  public static List<Tag> crawlAllScenarioTags(
      Scenario scenario, ChallengeService challengeService) {
    List<Tag> tags = new ArrayList<>(scenario.getTags());
    tags.addAll(scenario.getTeams().stream().flatMap(team -> team.getTags().stream()).toList());
    tags.addAll(
        scenario.getTeams().stream()
            .flatMap(team -> team.getUsers().stream().flatMap(user -> user.getTags().stream()))
            .toList());
    tags.addAll(
        scenario.getTeams().stream()
            .flatMap(
                team ->
                    team.getUsers().stream()
                        .flatMap(
                            user ->
                                user.getOrganization() == null
                                    ? null
                                    : user.getOrganization().getTags().stream()
                                        .filter(Objects::nonNull)))
            .toList());
    tags.addAll(
        scenario.getDocuments().stream().flatMap(document -> document.getTags().stream()).toList());
    tags.addAll(
        scenario.getInjects().stream().flatMap(inject -> inject.getTags().stream()).toList());
    List<Challenge> challenges = new ArrayList<>();
    for (Challenge challenge : challengeService.getScenarioChallenges(scenario)) {
      challenges.add(challenge);
    }
    tags.addAll(challenges.stream().flatMap(challenge -> challenge.getTags().stream()).toList());
    tags.addAll(
        scenario.getInjects().stream()
            .flatMap(
                inject ->
                    inject.getInjectorContract().isPresent()
                        ? inject.getInjectorContract().get().getPayload() != null
                            ? inject.getInjectorContract().get().getPayload().getTags().stream()
                            : Stream.of()
                        : Stream.of())
            .toList());
    return tags;
  }

  public static List<Tag> crawlAllInjectsTags(
      List<Inject> injects, ChallengeService challengeService) {
    List<Tag> tags =
        new ArrayList<>(injects.stream().flatMap(inject -> inject.getTags().stream()).toList());
    tags.addAll(
        injects.stream()
            .flatMap(inject -> inject.getTeams().stream())
            .flatMap(team -> team.getTags().stream())
            .toList());
    tags.addAll(
        injects.stream()
            .flatMap(inject -> inject.getTeams().stream())
            .flatMap(team -> team.getUsers().stream().flatMap(user -> user.getTags().stream()))
            .toList());
    tags.addAll(
        injects.stream()
            .flatMap(inject -> inject.getTeams().stream())
            .flatMap(
                team ->
                    team.getUsers().stream()
                        .flatMap(
                            user ->
                                user.getOrganization() == null
                                    ? null
                                    : user.getOrganization().getTags().stream()
                                        .filter(Objects::nonNull)))
            .toList());
    tags.addAll(
        injects.stream()
            .flatMap(inject -> inject.getDocuments().stream())
            .map(InjectDocument::getDocument)
            .flatMap(document -> document.getTags().stream())
            .toList());
    tags.addAll(injects.stream().flatMap(inject -> inject.getTags().stream()).toList());
    List<Challenge> challenges = new ArrayList<>();
    for (Challenge challenge : challengeService.getInjectsChallenges(injects)) {
      challenges.add(challenge);
    }
    tags.addAll(challenges.stream().flatMap(challenge -> challenge.getTags().stream()).toList());
    tags.addAll(
        injects.stream()
            .flatMap(
                inject ->
                    inject.getInjectorContract().isPresent()
                        ? inject.getInjectorContract().get().getPayload() != null
                            ? inject.getInjectorContract().get().getPayload().getTags().stream()
                            : Stream.of()
                        : Stream.of())
            .toList());
    return tags;
  }
}
