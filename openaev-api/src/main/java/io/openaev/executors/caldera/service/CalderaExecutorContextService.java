package io.openaev.executors.caldera.service;

import io.openaev.database.model.*;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.caldera.client.CalderaExecutorClient;
import io.openaev.executors.caldera.client.model.Ability;
import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.rest.exception.AgentException;
import io.openaev.service.InjectorService;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CalderaExecutorContextService extends ExecutorContextService {

  private final CalderaExecutorConfig calderaExecutorConfig;
  private final InjectorService injectorService;
  private final CalderaExecutorClient calderaExecutorClient;

  public final Map<String, Ability> injectorExecutorAbilities = new HashMap<>();
  public final Map<String, Ability> injectorExecutorClearAbilities = new HashMap<>();

  public void registerAbilities() {
    // Create the abilities if not exist for all injectors that need it
    List<Ability> abilities = this.abilities();

    Iterable<Injector> injectors = injectorService.getAllConnectors();
    injectors.forEach(
        injector -> {
          if (injector.getExecutorCommands() != null) {
            List<Ability> filteredAbilities =
                abilities.stream()
                    .filter(
                        ability ->
                            ability.getName().equals("caldera-subprocessor-" + injector.getName()))
                    .toList();
            if (!filteredAbilities.isEmpty()) {
              Ability existingAbility = filteredAbilities.getFirst();
              calderaExecutorClient.deleteAbility(existingAbility);
            }
            Ability ability = calderaExecutorClient.createSubprocessorAbility(injector);
            this.injectorExecutorAbilities.put(injector.getId(), ability);
          }
          if (injector.getExecutorClearCommands() != null) {
            List<Ability> filteredAbilities =
                abilities.stream()
                    .filter(
                        ability -> ability.getName().equals("caldera-clear-" + injector.getName()))
                    .toList();
            if (!filteredAbilities.isEmpty()) {
              Ability existingAbility = filteredAbilities.getFirst();
              calderaExecutorClient.deleteAbility(existingAbility);
            }
            Ability ability = calderaExecutorClient.createClearAbility(injector);
            this.injectorExecutorClearAbilities.put(injector.getId(), ability);
          }
        });
  }

  public void launchExecutorSubprocess(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent)
      throws AgentException {

    if (!this.calderaExecutorConfig.isEnable()) {
      throw new AgentException("Fatal error: Caldera executor is not enabled", agent);
    }

    inject
        .getInjectorContract()
        .map(InjectorContract::getInjector)
        .ifPresent(
            injector -> {
              if (this.injectorExecutorAbilities.containsKey(injector.getId())) {
                List<Map<String, String>> additionalFields =
                    List.of(
                        Map.of("trait", "inject", "value", inject.getId()),
                        Map.of("trait", "agent", "value", agent.getId()));
                calderaExecutorClient.exploit(
                    "base64",
                    agent.getExternalReference(),
                    this.injectorExecutorAbilities.get(injector.getId()).getAbility_id(),
                    additionalFields);
              }
            });
  }

  public List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus) {
    return new ArrayList<>();
  }

  public void launchExecutorClear(@NotNull final Injector injector, @NotNull final Agent agent) {
    if (this.injectorExecutorAbilities.containsKey(injector.getId())) {
      calderaExecutorClient.exploit(
          "base64",
          agent.getExternalReference(),
          this.injectorExecutorClearAbilities.get(injector.getId()).getAbility_id(),
          List.of());
    }
  }

  private List<Ability> abilities() {
    return calderaExecutorClient.abilities();
  }
}
