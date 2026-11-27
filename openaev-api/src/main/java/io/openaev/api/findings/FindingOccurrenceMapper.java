package io.openaev.api.findings;

import io.openaev.api.findings.dto.FindingOccurrenceContext;
import io.openaev.api.findings.dto.FindingOccurrenceOutput;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.model.Inject;
import io.openaev.utils.mapper.InjectMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FindingOccurrenceMapper {

  private final InjectMapper injectMapper;

  public FindingOccurrenceOutput toOutput(FindingOccurrence occurrence) {
    Optional<Exercise> simulation =
        Optional.ofNullable(occurrence.getInject()).map(Inject::getExercise);
    return FindingOccurrenceOutput.builder()
        .id(occurrence.getId())
        .date(occurrence.getDate())
        .field(occurrence.getField())
        .inject(
            Optional.ofNullable(occurrence.getInject())
                .map(injectMapper::toInjectSimple)
                .orElse(null))
        .simulation(
            simulation
                .map(exercise -> new FindingOccurrenceContext(exercise.getId(), exercise.getName()))
                .orElse(null))
        .scenario(
            simulation
                .map(Exercise::getScenario)
                .map(scenario -> new FindingOccurrenceContext(scenario.getId(), scenario.getName()))
                .orElse(null))
        .build();
  }
}
