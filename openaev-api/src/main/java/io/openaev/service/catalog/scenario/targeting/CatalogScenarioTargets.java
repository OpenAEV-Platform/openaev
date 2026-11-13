package io.openaev.service.catalog.scenario.targeting;

import static io.openaev.utils.InjectContentUtils.TARGET_SELECTOR_CONTENT_KEY;

import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import io.openaev.service.TeamService;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Adds the targets chosen at install to the injects of a catalogue scenario. */
@Component
@RequiredArgsConstructor
public class CatalogScenarioTargets {

  private final InjectorContractContentUtils contractContent;
  private final AssetService assetService;
  private final AssetGroupService assetGroupService;
  private final TeamService teamService;

  /**
   * Each inject gets the chosen targets of every kind its contract takes; an inject with a target
   * selector takes one kind, the one it selects when it was chosen, else the first one chosen.
   */
  public void addTo(Collection<Inject> injects, TargetChoice choice) {
    ChosenTargets chosen = resolve(choice);
    for (Inject inject : injects) {
      InjectorContract contract = inject.getInjectorContract().orElse(null);
      if (contractContent.hasField(contract, TARGET_SELECTOR_CONTENT_KEY)) {
        selectedKind(inject, chosen)
            .ifPresent(
                kind -> {
                  kind.selectOn(inject);
                  kind.addTo(inject, chosen);
                });
      } else {
        TargetKind.FIELDS.stream()
            .filter(kind -> contractContent.hasField(contract, kind.field()))
            .forEach(kind -> kind.addTo(inject, chosen));
      }
    }
  }

  private ChosenTargets resolve(TargetChoice choice) {
    return new ChosenTargets(
        choice.assetIds().isEmpty() ? List.of() : assetService.assets(choice.assetIds()),
        choice.assetGroupIds().isEmpty()
            ? List.of()
            : assetGroupService.assetGroups(choice.assetGroupIds()),
        choice.teamIds().isEmpty() ? List.of() : teamService.getTeamsByIds(choice.teamIds()),
        choice.hosts().stream().map(String::trim).filter(host -> !host.isEmpty()).toList());
  }

  private static Optional<TargetKind> selectedKind(Inject inject, ChosenTargets chosen) {
    Optional<TargetKind> selected =
        TargetKind.selectedOn(inject).filter(kind -> kind.isChosenIn(chosen));
    return selected.isPresent()
        ? selected
        : TargetKind.SELECTABLE.stream().filter(kind -> kind.isChosenIn(chosen)).findFirst();
  }
}
