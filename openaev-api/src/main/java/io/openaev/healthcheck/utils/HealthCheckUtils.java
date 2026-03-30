package io.openaev.healthcheck.utils;

import static io.openaev.database.model.InjectorContract.*;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static java.util.stream.StreamSupport.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.executors.utils.ExecutorUtils;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.helper.InjectModelHelper;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HealthCheckUtils {

  private final ExecutorUtils executorUtils;

  /**
   * Run all mail service checks for one inject
   *
   * @param inject to test
   * @param service to verify
   * @param isServiceAvailable status
   * @param type of healthcheck
   * @param status of healthcheck
   * @return found healthchecks
   */
  public List<HealthCheck> runMailServiceChecks(
      Inject inject,
      ExternalServiceDependency service,
      boolean isServiceAvailable,
      HealthCheck.Type type,
      HealthCheck.Status status) {
    List<HealthCheck> result = new ArrayList<>();
    InjectorContract injectorContract = inject.getInjectorContract().orElse(null);
    Injector injector = injectorContract != null ? injectorContract.getInjector() : null;

    if (injector != null
        && ArrayUtils.contains(injector.getDependencies(), service)
        && !isServiceAvailable) {
      result.add(new HealthCheck(type, HealthCheck.Detail.SERVICE_UNAVAILABLE, status, now()));
    }

    return result;
  }

  /**
   * Run all Executors checks for one inject
   *
   * @param inject to test
   * @param agentsAndAssetsAgentless data to verify if there is at least one agent up
   * @return all found executors healthchecks issues
   */
  public List<HealthCheck> runExecutorChecks(
      Inject inject, AgentsAndAssetsAgentless agentsAndAssetsAgentless) {
    List<HealthCheck> result = new ArrayList<>();
    InjectorContract injectorContract = inject.getInjectorContract().orElse(null);
    Set<Agent> agents = agentsAndAssetsAgentless.agents();
    agents = executorUtils.removeInactiveAgentsFromAgents(agents);
    agents = executorUtils.removeAgentsWithoutExecutorFromAgents(agents);

    if (injectorContract != null && injectorContract.getNeedsExecutor() && agents.isEmpty()) {
      result.add(
          new HealthCheck(
              HealthCheck.Type.AGENT_OR_EXECUTOR,
              HealthCheck.Detail.EMPTY,
              HealthCheck.Status.ERROR,
              now()));
    }

    return result;
  }

  /**
   * Run all Collectors checks for one inject
   *
   * @param inject to test
   * @param collectors all available collectors
   * @return all found collectors healthchecks issues
   */
  public List<HealthCheck> runCollectorChecks(Inject inject, List<Collector> collectors) {
    List<HealthCheck> result = new ArrayList<>();
    boolean isDetectionOrPrenvention =
        InjectModelHelper.isDetectionOrPrevention(inject.getContent());

    if (isDetectionOrPrenvention && collectors.isEmpty()) {
      result.add(
          new HealthCheck(
              HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR,
              HealthCheck.Detail.EMPTY,
              HealthCheck.Status.ERROR,
              now()));
    }

    return result;
  }

  /**
   * Launch all the injector checks on one inject
   *
   * @param inject to test
   * @param injectors all available injectors
   * @return list of the healthcheck result
   */
  public List<HealthCheck> runAllInjectorChecks(
      @NotNull final Inject inject, @NotNull final List<Injector> injectors) {

    List<HealthCheck> results = new ArrayList<>();
    results.addAll(
        runInjectorCheck(inject, injectors, ExternalServiceDependency.NMAP, HealthCheck.Type.NMAP));
    results.addAll(
        runInjectorCheck(
            inject, injectors, ExternalServiceDependency.NUCLEI, HealthCheck.Type.NUCLEI));
    return results;
  }

  /**
   * Verify whether an injector contract depends on an injector and whether that injector is
   * registered; if not, add an error to the health check.
   *
   * @param inject the inject to verify
   * @param injectors the list of registered injectors
   * @param externalServiceDependency the external service dependency to check against
   * @param type the type of health check being performed
   * @return a list of health check errors, empty if the injector is properly registered
   */
  public List<HealthCheck> runInjectorCheck(
      @NotNull final Inject inject,
      @NotNull final List<Injector> injectors,
      @NotNull final ExternalServiceDependency externalServiceDependency,
      @NotNull final HealthCheck.Type type) {
    List<HealthCheck> result = new ArrayList<>();
    InjectorContract contract = inject.getInjectorContract().orElse(null);
    if (contract != null
        && contract.getInjector() != null
        && contract.getInjector().getDependencies() != null
        && Arrays.asList(contract.getInjector().getDependencies())
            .contains(externalServiceDependency)) {
      boolean isInjectorRegistered =
          injectors.stream()
              .anyMatch(
                  injector ->
                      Objects.equals(injector.getType(), externalServiceDependency.getValue()));

      // if the injector is not registered we add an error in the health check
      if (!isInjectorRegistered) {
        result.add(
            new HealthCheck(
                type, HealthCheck.Detail.SERVICE_UNAVAILABLE, HealthCheck.Status.ERROR, now()));
      }
    }
    return result;
  }

  /**
   * Verify if into the provided list of healthchecks, at least one specific exist
   *
   * @param type to search
   * @param detail to search
   * @param status to search
   * @param injectsHealthChecks to filter
   * @return list of found healthcheck
   */
  public List<HealthCheck> runInjectsChecksFor(
      HealthCheck.Type type,
      HealthCheck.Detail detail,
      HealthCheck.Status status,
      List<HealthCheck> injectsHealthChecks) {
    List<HealthCheck> result = new ArrayList<>();

    if (injectsHealthChecks.stream()
        .anyMatch(
            healthCheck ->
                Objects.equals(type, healthCheck.getType())
                    && Objects.equals(detail, healthCheck.getDetail())
                    && Objects.equals(status, healthCheck.getStatus()))) {
      result.add(new HealthCheck(type, detail, status, now()));
    }

    return result;
  }

  /**
   * Run all missing content checks for one scenario
   *
   * @param scenario to test
   * @return all found missing content issues
   */
  public List<HealthCheck> runMissingContentChecks(@NotNull final Scenario scenario) {
    List<HealthCheck> result = new ArrayList<>();
    boolean atLeastOneInjectIsNotReady =
        scenario.getInjects().stream().anyMatch(inject -> !runContentChecks(inject).isEmpty());

    if (atLeastOneInjectIsNotReady) {
      result.add(
          new HealthCheck(
              HealthCheck.Type.INJECT,
              HealthCheck.Detail.NOT_READY,
              HealthCheck.Status.WARNING,
              now()));
    }

    return result;
  }

  /**
   * Run all teams checks for one scenario
   *
   * @param scenario to test
   * @return all found teams issues
   */
  public List<HealthCheck> runTeamsChecks(@NotNull final Scenario scenario) {
    List<HealthCheck> result = new ArrayList<>();
    boolean isMailSender =
        scenario.getInjects().stream()
            .filter(
                inject ->
                    inject.getInjectorContract() != null
                        && inject.getInjectorContract().isPresent()
                        && inject.getInjectorContract().get().getInjector() != null
                        && inject.getInjectorContract().get().getInjector().getDependencies()
                            != null)
            .flatMap(
                inject ->
                    Arrays.stream(
                        inject.getInjectorContract().get().getInjector().getDependencies()))
            .anyMatch(
                dependency ->
                    ExternalServiceDependency.SMTP.equals(dependency)
                        || ExternalServiceDependency.IMAP.equals(dependency));

    if (isMailSender) {
      boolean isMissingTeamsOrEnabledPlayers =
          scenario.getTeams().isEmpty()
              || scenario.getTeams().stream().allMatch(team -> team.getUsers().isEmpty())
              || scenario.getTeamUsers().isEmpty();

      if (isMissingTeamsOrEnabledPlayers) {
        result.add(
            new HealthCheck(
                HealthCheck.Type.TEAMS,
                HealthCheck.Detail.EMPTY,
                HealthCheck.Status.WARNING,
                now()));
      }
    }

    return result;
  }

  /**
   * Run content checks by inject
   *
   * @param inject to verify
   * @return found healthchecks
   */
  public List<HealthCheck> runContentChecks(Inject inject) {
    return runContentChecks(
        inject.getInjectorContract().orElse(null),
        inject.getContent(),
        inject.isAllTeams(),
        ofNullable(inject.getTeams())
            .map(teams -> teams.stream().map(Team::getId).toList())
            .orElse(new ArrayList<>()),
        ofNullable(inject.getAssets())
            .map(assets -> assets.stream().map(Asset::getId).toList())
            .orElse(new ArrayList<>()),
        ofNullable(inject.getAssetGroups())
            .map(assetGroups -> assetGroups.stream().map(AssetGroup::getId).toList())
            .orElse(new ArrayList<>()));
  }

  /**
   * Run content check by injector contract
   *
   * @param injectorContract to validate
   * @param content to validate
   * @param allTeams to control
   * @param teams to control
   * @param assets to control
   * @param assetGroups to control
   * @return found list of healthchecks
   */
  public List<HealthCheck> runContentChecks(
      InjectorContract injectorContract,
      ObjectNode content,
      boolean allTeams,
      @NotNull final List<String> teams,
      @NotNull final List<String> assets,
      @NotNull final List<String> assetGroups) {
    List<HealthCheck> result = new ArrayList<>();

    if (injectorContract == null) {
      result.add(
          new HealthCheck(
              HealthCheck.Type.INJECTOR_CONTRACT,
              HealthCheck.Detail.MANDATORY_CONTENT,
              HealthCheck.Status.ERROR,
              now()));
      return result;
    }

    ObjectMapper mapper = new ObjectMapper();
    ArrayNode injectContractFields;

    try {
      injectContractFields =
          (ArrayNode)
              mapper
                  .readValue(injectorContract.getContent(), ObjectNode.class)
                  .get(CONTRACT_CONTENT_FIELDS);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error parsing injector contract content", e);
    }

    ObjectNode contractContent = injectorContract.getConvertedContent();
    if (contractContent == null) {
      result.add(
          new HealthCheck(
              HealthCheck.Type.INJECTOR_CONTRACT,
              HealthCheck.Detail.MANDATORY_CONTENT,
              HealthCheck.Status.ERROR,
              now()));
      return result;
    }
    List<JsonNode> contractFields =
        stream(contractContent.get(CONTRACT_CONTENT_FIELDS).spliterator(), false).toList();

    for (JsonNode jsonField : contractFields) {

      // If field is mandatory
      if (jsonField.get(CONTRACT_ELEMENT_CONTENT_MANDATORY).asBoolean()
          && !InjectModelHelper.isFieldSet(
              allTeams, teams, assets, assetGroups, jsonField, content, injectContractFields)) {
        result.add(
            new HealthCheck(
                HealthCheck.Type.fromValue(jsonField.get(CONTRACT_ELEMENT_CONTENT_KEY).asText()),
                HealthCheck.Detail.MANDATORY_CONTENT,
                HealthCheck.Status.ERROR,
                now()));
      }

      // If field is mandatory group
      if (jsonField.hasNonNull(CONTRACT_ELEMENT_CONTENT_MANDATORY_GROUPS)) {
        ArrayNode mandatoryGroups =
            (ArrayNode) jsonField.get(CONTRACT_ELEMENT_CONTENT_MANDATORY_GROUPS);
        if (!mandatoryGroups.isEmpty()) {
          boolean atLeastOneSet = false;
          for (JsonNode mandatoryFieldKey : mandatoryGroups) {
            Optional<JsonNode> groupField =
                contractFields.stream()
                    .filter(
                        jsonNode ->
                            mandatoryFieldKey
                                .asText()
                                .equals(jsonNode.get(CONTRACT_ELEMENT_CONTENT_KEY).asText()))
                    .findFirst();
            if (groupField.isPresent()
                && InjectModelHelper.isFieldSet(
                    allTeams,
                    teams,
                    assets,
                    assetGroups,
                    groupField.get(),
                    content,
                    injectContractFields)) {
              atLeastOneSet = true;
              break;
            }
          }
          if (!atLeastOneSet) {
            for (JsonNode mandatoryFieldKey : mandatoryGroups) {
              result.add(
                  new HealthCheck(
                      HealthCheck.Type.fromValue(mandatoryFieldKey.asText()),
                      HealthCheck.Detail.MANDATORY_CONTENT,
                      HealthCheck.Status.ERROR,
                      now()));
            }
          }
        }
      }

      // If field is mandatory conditional, if the conditional field is set, check if the current
      // field is set
      if (jsonField.hasNonNull(CONTRACT_ELEMENT_CONTENT_MANDATORY_CONDITIONAL_FIELDS)) {
        JsonNode fields = jsonField.get(CONTRACT_ELEMENT_CONTENT_MANDATORY_CONDITIONAL_FIELDS);

        if (fields.isArray()) {
          for (JsonNode node : fields) {
            if (!node.isNull()) {
              String fieldKey = node.asText();

              Optional<JsonNode> conditionalFieldOpt =
                  contractFields.stream()
                      .filter(
                          jsonNode ->
                              fieldKey.equals(jsonNode.get(CONTRACT_ELEMENT_CONTENT_KEY).asText()))
                      .findFirst();

              // If field not exists -> skip
              if (conditionalFieldOpt.isEmpty()) {
                continue;
              }
              if (jsonField.hasNonNull(CONTRACT_ELEMENT_CONTENT_MANDATORY_CONDITIONAL_VALUES)) {
                JsonNode conditionalValuesNode =
                    jsonField.get(CONTRACT_ELEMENT_CONTENT_MANDATORY_CONDITIONAL_VALUES);

                if (conditionalValuesNode.has(fieldKey)) {
                  List<String> specificValuesNode =
                      conditionalValuesNode.get(fieldKey).isArray()
                          ? stream(conditionalValuesNode.get(fieldKey).spliterator(), false)
                              .map(JsonNode::asText)
                              .toList()
                          : List.of(conditionalValuesNode.get(fieldKey).asText());

                  List<String> actualValues =
                      InjectModelHelper.getFieldValue(
                          teams, assets, assetGroups, conditionalFieldOpt.get(), content);
                  boolean conditionMet =
                      actualValues.stream().anyMatch(specificValuesNode::contains);

                  if (!conditionMet) {
                    continue; // condition not met → skip
                  }
                }
              }
              Optional<JsonNode> fieldOpt =
                  contractFields.stream()
                      .filter(
                          jsonNode ->
                              jsonField
                                  .get(CONTRACT_ELEMENT_CONTENT_KEY)
                                  .asText()
                                  .equals(jsonNode.get(CONTRACT_ELEMENT_CONTENT_KEY).asText()))
                      .findFirst();
              // If field not exists -> skip
              if (fieldOpt.isEmpty()) {
                continue;
              }
              if (!InjectModelHelper.isFieldSet(
                  allTeams,
                  teams,
                  assets,
                  assetGroups,
                  fieldOpt.get(),
                  content,
                  injectContractFields)) {
                result.add(
                    new HealthCheck(
                        HealthCheck.Type.fromValue(
                            fieldOpt.get().get(CONTRACT_ELEMENT_CONTENT_KEY).asText()),
                        HealthCheck.Detail.MANDATORY_CONTENT,
                        HealthCheck.Status.ERROR,
                        now()));
              }
            }
          }
        }
      }
    }

    return removeDuplicates(result);
  }

  /**
   * Remove all duplicates healthchecks
   *
   * @param healthChecks to filter
   * @return filtered healthchecks
   */
  public List<HealthCheck> removeDuplicates(List<HealthCheck> healthChecks) {
    if (healthChecks == null || healthChecks.isEmpty()) {
      return Collections.emptyList();
    }

    return healthChecks.stream()
        .collect(
            Collectors.toMap(
                this::createHealthCheckKey, Function.identity(), this::keepErrorStatusPriority))
        .values()
        .stream()
        .toList();
  }

  private String createHealthCheckKey(HealthCheck healthCheck) {
    return healthCheck.getType() + "_" + healthCheck.getDetail();
  }

  private HealthCheck keepErrorStatusPriority(HealthCheck first, HealthCheck second) {
    if (HealthCheck.Status.ERROR.equals(first.getStatus())) {
      return first;
    } else if (HealthCheck.Status.ERROR.equals(second.getStatus())) {
      return second;
    }
    return first;
  }
}
