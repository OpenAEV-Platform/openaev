package io.openaev.rest.finding;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.injector_contract.InjectorContractContentUtils.getContractOutputs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import io.openaev.rest.inject.service.InjectService;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FindingService {

  private final InjectService injectService;

  private final FindingRepository findingRepository;
  private final AssetRepository assetRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;

  @Resource private ObjectMapper mapper;

  // -- CRUD --

  public List<Finding> findings() {
    return fromIterable(this.findingRepository.findAll());
  }

  public Finding finding(@NotNull final String id) {
    return this.findingRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Finding not found with id: " + id));
  }

  public Finding createFinding(@NotNull final Finding finding, @NotBlank final String injectId) {
    Inject inject = this.injectService.inject(injectId);
    finding.setInject(inject);
    return this.findingRepository.save(finding);
  }

  public Iterable<Finding> createFindings(
      @NotNull final List<Finding> findings, @NotBlank final String injectId) {
    Inject inject = this.injectService.inject(injectId);
    findings.forEach((finding) -> finding.setInject(inject));
    return this.findingRepository.saveAll(findings);
  }

  public Finding updateFinding(@NotNull final Finding finding, @NotNull final String injectId) {
    if (!finding.getInject().getId().equals(injectId)) {
      throw new IllegalArgumentException("Inject id cannot be changed: " + injectId);
    }
    return this.findingRepository.save(finding);
  }

  public void deleteFinding(@NotNull final String id) {
    if (!this.findingRepository.existsById(id)) {
      throw new EntityNotFoundException("Finding not found with id: " + id);
    }
    this.findingRepository.deleteById(id);
  }

  /**
   * Builds a Finding based on the provided parameters. If a Finding with the same inject ID, value,
   * type, and key already exists, it will update the assets associated with it.
   *
   * @param inject the Inject object associated with the Finding
   * @param asset the Asset to be linked to the Finding
   * @param contractOutputElement the ContractOutputElement defining the type and key of the Finding
   * @param finalValue the value of the Finding to be stored
   */
  public void buildFinding(
      Inject inject, Asset asset, ContractOutputElement contractOutputElement, String finalValue) {
    try {
      Optional<Finding> optionalFinding =
          findingRepository.findByInjectIdAndValueAndTypeAndKey(
              inject.getId(),
              finalValue,
              contractOutputElement.getType(),
              contractOutputElement.getKey());

      Finding finding =
          optionalFinding.orElseGet(
              () -> {
                Finding newFinding = new Finding();
                newFinding.setInject(inject);
                newFinding.setField(contractOutputElement.getKey());
                newFinding.setType(contractOutputElement.getType());
                newFinding.setValue(finalValue);
                newFinding.setName(contractOutputElement.getName());
                newFinding.setTags(new HashSet<>(contractOutputElement.getTags()));
                return newFinding;
              });

      boolean isNewAsset =
          finding.getAssets().stream().noneMatch(a -> a.getId().equals(asset.getId()));

      if (isNewAsset) {
        finding.getAssets().add(asset);
      }

      if (optionalFinding.isEmpty() || isNewAsset) {
        findingRepository.save(finding);
      }

    } catch (DataIntegrityViolationException ex) {
      log.info(
          String.format(
              "Race condition: finding already exists. Retrying ... %s ", ex.getMessage()),
          ex);
      // Re-fetch and try to add the asset
      handleRaceCondition(inject, asset, contractOutputElement, finalValue);
    }
  }

  private void handleRaceCondition(
      Inject inject, Asset asset, ContractOutputElement contractOutputElement, String finalValue) {
    Optional<Finding> retryFinding =
        findingRepository.findByInjectIdAndValueAndTypeAndKey(
            inject.getId(),
            finalValue,
            contractOutputElement.getType(),
            contractOutputElement.getKey());

    if (retryFinding.isPresent()) {
      Finding existingFinding = retryFinding.get();
      boolean isNewAsset =
          existingFinding.getAssets().stream().noneMatch(a -> a.getId().equals(asset.getId()));
      if (isNewAsset) {
        existingFinding.getAssets().add(asset);
        findingRepository.save(existingFinding);
      }
    } else {
      log.warn("Retry failed: Finding still not found after race condition.");
    }
  }

  // -- Extract findings from strctured output : Here we compute the findings from structured output
  // from ExecutionInjectInput sent by injectors
  // This structured output is generated based on injectorcontract where we can find the node
  // Outputs and with that the injector generate this structure output--

  public void extractFindingsFromInjectorContract(Inject inject, ObjectNode structuredOutput) {
    // Get the contract
    InjectorContract injectorContract = inject.getInjectorContract().orElseThrow();
    List<InjectorContractContentOutputElement> contractOutputs =
        getContractOutputs(injectorContract.getConvertedContent(), mapper);

    if (contractOutputs.isEmpty()) {
      log.warn("No contract outputs found for inject: " + inject.getId());
      return;
    }

    List<Finding> findings = new ArrayList<>();
    contractOutputs.forEach(
        contractOutput -> {
          if (!contractOutput.isFindingCompatible()) {
            return;
          }
          if (contractOutput.isMultiple()) {
            JsonNode jsonNodes = structuredOutput.get(contractOutput.getField());
            if (jsonNodes != null && jsonNodes.isArray()) {
              for (JsonNode jsonNode : jsonNodes) {
                if (!contractOutput.getType().validate.apply(jsonNode)) {
                  throw new IllegalArgumentException("Finding not correctly formatted");
                }
                Finding finding = FindingUtils.createFinding(contractOutput);
                finding.setValue(contractOutput.getType().toFindingValue.apply(jsonNode));
                Finding linkedFinding = linkFindings(contractOutput, jsonNode, finding);
                findings.add(linkedFinding);
              }
            }
          } else {
            JsonNode jsonNode = structuredOutput.get(contractOutput.getField());
            if (!contractOutput.getType().validate.apply(jsonNode)) {
              throw new IllegalArgumentException("Finding not correctly formatted");
            }
            Finding finding = FindingUtils.createFinding(contractOutput);
            finding.setValue(contractOutput.getType().toFindingValue.apply(jsonNode));
            Finding linkedFinding = linkFindings(contractOutput, jsonNode, finding);
            findings.add(linkedFinding);
          }
        });
    this.createFindings(findings, inject.getId());
  }

  private Finding linkFindings(
      InjectorContractContentOutputElement contractOutput, JsonNode jsonNode, Finding finding) {
    // Create links with assets
    if (contractOutput.getType().toFindingAssets != null) {
      List<String> assetsIds = contractOutput.getType().toFindingAssets.apply(jsonNode);
      List<Optional<Asset>> assets =
          assetsIds.stream().map(this.assetRepository::findById).toList();
      if (!assets.isEmpty()) {
        finding.setAssets(assets.stream().filter(Optional::isPresent).map(Optional::get).toList());
      }
    }
    // Create links with teams
    if (contractOutput.getType().toFindingTeams != null) {
      List<String> teamsIds = contractOutput.getType().toFindingTeams.apply(jsonNode);
      List<Optional<Team>> teams = teamsIds.stream().map(this.teamRepository::findById).toList();
      if (!teams.isEmpty()) {
        finding.setTeams(teams.stream().filter(Optional::isPresent).map(Optional::get).toList());
      }
    }
    // Create links with users
    if (contractOutput.getType().toFindingUsers != null) {
      List<String> usersIds = contractOutput.getType().toFindingUsers.apply(jsonNode);
      List<Optional<User>> users = usersIds.stream().map(this.userRepository::findById).toList();
      if (!users.isEmpty()) {
        finding.setUsers(users.stream().filter(Optional::isPresent).map(Optional::get).toList());
      }
    }
    return finding;
  }

  /**
   * Function used to get Finding Contract Output Element from OutputParser
   *
   * @param outputParsers OutputParser
   * @return list of contractOutputElement of OutputParser
   */
  private List<ContractOutputElement> getAllIsFindingContractOutputElementsOfOutputParser(
      Set<OutputParser> outputParsers) {
    return outputParsers.stream()
        .flatMap(outputParser -> outputParser.getContractOutputElements().stream())
        .filter(io.openaev.database.model.ContractOutputElement::isFinding)
        .toList();
  }

  /**
   * Function used to get the asset associated with a given structured output.
   *
   * @param struturedOutput The structured output to analyze.
   * @param valueTargetedAssetsMap a map where the key is the value of the targeted asset (e.g.,
   *     hostname, seen_ip) and the value is the Endpoint object representing the targeted asset.
   * @param sourceAgent The agent where the execution occurred.
   * @return The linked Asset.
   */
  private Asset getAssetLinkedToStructuredOutput(
      JsonNode struturedOutput, Map<String, Endpoint> valueTargetedAssetsMap, Agent sourceAgent) {
    if (valueTargetedAssetsMap.isEmpty() || !struturedOutput.has("host")) {
      return sourceAgent.getAsset();
    }

    String host = struturedOutput.get("host").asText();
    return valueTargetedAssetsMap.keySet().stream()
        .filter(host::contains)
        .findFirst()
        .map(valueTargetedAssetsMap::get)
        .orElse(null);
  }

  /** Extracts findings from structured output that was generated using output parsers. */
  public void extractFindingsFromOutputParsers(
      Inject inject, Agent agent, Set<OutputParser> outputParsers, JsonNode structuredOutput) {

    List<ContractOutputElement> contractOutputElements =
        this.getAllIsFindingContractOutputElementsOfOutputParser(outputParsers);

    Map<String, Endpoint> valueTargetedAssetsMap = injectService.getValueTargetedAssetMap(inject);

    contractOutputElements.forEach(
        contractOutputElement -> {
          JsonNode jsonNodes = structuredOutput.get(contractOutputElement.getKey());
          if (jsonNodes == null || !jsonNodes.isArray()) {
            return;
          }

          for (JsonNode jsonNode : jsonNodes) {
            // Validate finding format
            if (!contractOutputElement.getType().validate.apply(jsonNode)) {
              throw new IllegalArgumentException("Finding not correctly formatted");
            }

            // Build and save the finding
            this.buildFinding(
                inject,
                getAssetLinkedToStructuredOutput(jsonNode, valueTargetedAssetsMap, agent),
                contractOutputElement,
                contractOutputElement.getType().toFindingValue.apply(jsonNode));
          }
        });
  }
}
