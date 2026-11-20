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

/** Adds chosen targets to injects: of a scenario at install, of a simulation at launch. */
@Component
@RequiredArgsConstructor
public class CatalogScenarioTargets {

  private final InjectorContractContentUtils contractContent;
  private final AssetService assetService;
  private final AssetGroupService assetGroupService;
  private final TeamService teamService;

  /**
   * Each inject gets the chosen targets of every kind its contract takes; an inject with a target
   * selector takes one kind, the one it selects when it was chosen, else the first one chosen. The
   * chosen ids resolve in the tenant of the injects: an id of another tenant is unknown.
   */
  public void addTo(String tenantId, Collection<Inject> injects, TargetChoice choice) {
    ChosenTargets chosen = resolve(tenantId, choice);
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

  /**
   * The kinds of target an inject takes, none when it takes no target, and whether it has one: an
   * inject with a target selector has a target of the kind it selects.
   */
  public InjectTargets targetsOf(Inject inject) {
    InjectorContract contract = inject.getInjectorContract().orElse(null);
    if (contractContent.hasField(contract, TARGET_SELECTOR_CONTENT_KEY)) {
      return new InjectTargets(
          inject,
          TargetKind.SELECTABLE,
          TargetKind.selectedOn(inject).filter(kind -> kind.isSetOn(inject)).isPresent());
    }
    List<TargetKind> kinds =
        TargetKind.FIELDS.stream()
            .filter(kind -> contractContent.hasField(contract, kind.field()))
            .toList();
    return new InjectTargets(inject, kinds, kinds.stream().anyMatch(kind -> kind.isSetOn(inject)));
  }

  /**
   * The chosen ids the current reader resolves in the tenant: one they are not cleared for is
   * dropped, like an unknown one.
   */
  public TargetChoice visible(String tenantId, TargetChoice choice) {
    return resolve(tenantId, choice).choice();
  }

  private ChosenTargets resolve(String tenantId, TargetChoice choice) {
    return new ChosenTargets(
        choice.assetIds().isEmpty() ? List.of() : assetService.assets(choice.assetIds(), tenantId),
        choice.assetGroupIds().isEmpty()
            ? List.of()
            : assetGroupService.assetGroups(choice.assetGroupIds(), tenantId),
        choice.teamIds().isEmpty() ? List.of() : teamService.teams(tenantId, choice.teamIds()),
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
