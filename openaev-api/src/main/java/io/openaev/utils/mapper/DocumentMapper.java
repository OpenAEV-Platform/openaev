package io.openaev.utils.mapper;

import static io.openaev.utils.mapper.ChallengeMapper.toRelatedEntityOutputs;
import static io.openaev.utils.mapper.ChannelMapper.toRelatedEntityOutputs;
import static io.openaev.utils.mapper.ExerciseMapper.toRelatedEntityOutputs;
import static io.openaev.utils.mapper.ExerciseMapper.toSimulationArticles;
import static io.openaev.utils.mapper.ExerciseMapper.toSimulationInjects;
import static io.openaev.utils.mapper.InjectMapper.toRelatedEntityOutputs;
import static io.openaev.utils.mapper.PayloadMapper.toRelatedEntityOutputs;
import static io.openaev.utils.mapper.ScenarioMapper.toScenarioArticles;
import static io.openaev.utils.mapper.ScenarioMapper.toScenarioInjects;
import static io.openaev.utils.mapper.SecurityPlatformMapper.toRelatedEntityOutputs;

import io.openaev.database.model.*;
import io.openaev.rest.document.form.DocumentRelationsOutput;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class DocumentMapper {

  public static DocumentRelationsOutput toDocumentRelationsOutput(Document document) {
    Set<Article> articles = document.getArticles();
    Set<Inject> injects =
        document.getInjectDocuments().stream()
            .map(InjectDocument::getInject)
            .collect(Collectors.toSet());

    Set<Article> scenarioArticles =
        articles.stream()
            .filter(article -> article.getScenario() != null)
            .collect(Collectors.toSet());

    Set<Article> simulationArticles =
        articles.stream()
            .filter(article -> article.getExercise() != null)
            .collect(Collectors.toSet());

    Set<Inject> atomics =
        injects.stream()
            .filter(inject -> inject.getScenario() == null && inject.getExercise() == null)
            .collect(Collectors.toSet());

    Set<Inject> scenarioInjects =
        injects.stream().filter(inject -> inject.getScenario() != null).collect(Collectors.toSet());

    Set<Inject> simulationInjects =
        injects.stream().filter(inject -> inject.getExercise() != null).collect(Collectors.toSet());

    Set<Exercise> simulations =
        Stream.concat(
                document.getSimulationsByLogoDark().stream(),
                document.getSimulationsByLogoLight().stream())
            .collect(Collectors.toSet());

    Set<SecurityPlatform> securityPlatforms =
        Stream.concat(
                document.getSecurityPlatformsByLogoDark().stream(),
                document.getSecurityPlatformsByLogoLight().stream())
            .collect(Collectors.toSet());

    Set<Channel> channels =
        Stream.concat(
                document.getChannelsByLogoDark().stream(),
                document.getChannelsByLogoLight().stream())
            .collect(Collectors.toSet());

    Set<Payload> payloads =
        Stream.concat(
                document.getPayloadsByFileDrop().stream(),
                document.getPayloadsByExecutableFile().stream())
            .collect(Collectors.toSet());

    return DocumentRelationsOutput.builder()
        .simulations(toRelatedEntityOutputs(simulations))
        .securityPlatforms(toRelatedEntityOutputs(securityPlatforms))
        .channels(toRelatedEntityOutputs(channels))
        .payloads(toRelatedEntityOutputs(payloads))
        .scenarioArticles(toScenarioArticles(scenarioArticles))
        .simulationArticles(toSimulationArticles(simulationArticles))
        .atomicTestings(toRelatedEntityOutputs(atomics))
        .scenarioInjects(toScenarioInjects(scenarioInjects))
        .simulationInjects(toSimulationInjects(simulationInjects))
        .challenges(toRelatedEntityOutputs(document.getChallenges()))
        .build();
  }
}
