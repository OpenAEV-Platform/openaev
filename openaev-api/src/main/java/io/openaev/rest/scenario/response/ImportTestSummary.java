package io.openaev.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import io.openaev.rest.inject.output.InjectOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Data;

@Data
public class ImportTestSummary {

  @JsonProperty("import_message")
  private List<ImportMessage> importMessage = new ArrayList<>();

  @JsonProperty("total_injects")
  public int totalNumberOfInjects;

  @JsonIgnore private List<Inject> injects = new ArrayList<>();

  @JsonProperty("injects")
  public List<InjectOutput> getInjectResults() {
    return injects.stream()
        .map(
            inject ->
                new InjectOutput(
                    inject.getId(),
                    inject.getTitle(),
                    inject.isEnabled(),
                    inject.getContent(),
                    inject.isAllTeams(),
                    Optional.ofNullable(inject.getExercise()).map(Exercise::getId).orElse(null),
                    Optional.ofNullable(inject.getScenario()).map(Scenario::getId).orElse(null),
                    inject.getDependsDuration(),
                    inject.getInjectorContract().orElse(null),
                    inject.getTags().stream().map(Tag::getId).toArray(String[]::new),
                    inject.getTeams().stream().map(Team::getId).toArray(String[]::new),
                    inject.getAssets().stream().map(Asset::getId).toArray(String[]::new),
                    inject.getAssetGroups().stream().map(AssetGroup::getId).toArray(String[]::new),
                    inject
                        .getInjectorContract()
                        .map(InjectorContract::getInjector)
                        .map(Injector::getType)
                        .orElse(null),
                    Optional.ofNullable(inject.getDependsOn())
                        .map(List::stream)
                        .flatMap(Stream::findAny)
                        .orElse(null)))
        .toList();
  }
}
