package io.openbas.injectors.caldera;

import static io.openbas.executors.caldera.service.CalderaExecutorService.toPlatform;
import static io.openbas.helper.SupportedLanguage.en;
import static io.openbas.helper.SupportedLanguage.fr;
import static io.openbas.injector_contract.Contract.executableContract;
import static io.openbas.injector_contract.ContractCardinality.Multiple;
import static io.openbas.injector_contract.ContractDef.contractBuilder;
import static io.openbas.injector_contract.fields.ContractAsset.assetField;
import static io.openbas.injector_contract.fields.ContractAssetGroup.assetGroupField;
import static io.openbas.injector_contract.fields.ContractExpectations.expectationsField;
import static io.openbas.injector_contract.fields.ContractSelect.selectFieldWithDefault;

import io.openbas.database.model.Endpoint.PLATFORM_TYPE;
import io.openbas.expectation.ExpectationBuilderService;
import io.openbas.helper.SupportedLanguage;
import io.openbas.injector_contract.*;
import io.openbas.injector_contract.fields.*;
import io.openbas.injectors.caldera.client.model.Ability;
import io.openbas.injectors.caldera.config.CalderaInjectorConfig;
import io.openbas.injectors.caldera.model.CalderaInjectContent;
import io.openbas.injectors.caldera.model.Obfuscator;
import io.openbas.injectors.caldera.service.CalderaInjectorService;
import jakarta.validation.constraints.NotNull;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CalderaContract extends Contractor {

  public static final String TYPE = "openbas_caldera";

  private final CalderaInjectorConfig config;
  private final CalderaInjectorService injectorCalderaService;
  private final ExpectationBuilderService expectationBuilderService;

  @Override
  public boolean isExpose() {
    return this.config.isEnable();
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    Map<SupportedLanguage, String> labels = Map.of(en, "Caldera", fr, "Caldera");
    return new ContractConfig(
        TYPE, labels, "#8b0000", "#8b0000", "/img/icon-caldera.png", isExpose());
  }

  @Override
  public List<Contract> contracts() {
    if (this.config.isEnable()) {
      ContractConfig contractConfig = getConfig();
      // Add contract based on abilities
      return new ArrayList<>(abilityContracts(contractConfig));
    }
    return List.of();
  }

  // -- PRIVATE --

  private ContractSelect obfuscatorField() {
    List<Obfuscator> obfuscators = this.injectorCalderaService.obfuscators();
    Map<String, String> obfuscatorChoices =
        obfuscators.stream().collect(Collectors.toMap(Obfuscator::getName, Obfuscator::getName));
    return selectFieldWithDefault(
        "obfuscator",
        "Obfuscators",
        obfuscatorChoices,
        CalderaInjectContent.getDefaultObfuscator());
  }

  private ContractExpectations expectations() {
    return expectationsField(
        List.of(
            this.expectationBuilderService.buildPreventionExpectation(),
            this.expectationBuilderService.buildDetectionExpectation()));
  }

  private List<Contract> abilityContracts(@NotNull final ContractConfig contractConfig) {
    // Fields
    ContractAsset assetField = assetField(Multiple);
    ContractAssetGroup assetGroupField = assetGroupField(Multiple);
    ContractSelect obfuscatorField = obfuscatorField();
    ContractExpectations expectationsField = expectations();

    List<Ability> abilities =
        this.injectorCalderaService.abilities().stream()
            .filter(ability -> !ability.getTactic().equals("openbas"))
            .toList();
    // Build contracts
    return abilities.stream()
        .map(
            (ability -> {
              ContractDef builder = contractBuilder();
              builder.mandatoryGroup(assetField, assetGroupField);
              builder.optional(obfuscatorField);
              builder.optional(expectationsField);
              List<PLATFORM_TYPE> platforms = new ArrayList<>();
              ability
                  .getExecutors()
                  .forEach(
                      executor -> {
                        String command = executor.getCommand();
                        if (command != null && !command.isEmpty()) {
                          Matcher matcher = Pattern.compile("#\\{(.*?)\\}").matcher(command);
                          while (matcher.find()) {
                            if (!matcher.group(1).isEmpty()) {
                              builder.mandatory(
                                  ContractText.textField(matcher.group(1), matcher.group(1)));
                            }
                          }
                        }
                        if (!executor.getPlatform().equals("unknown")) {
                          PLATFORM_TYPE platform = toPlatform(executor.getPlatform());
                          if (!platforms.contains(platform)) {
                            platforms.add(platform);
                          }
                        } else {
                          if (executor.getName().equals("psh")) {
                            if (!platforms.contains(PLATFORM_TYPE.Windows)) {
                              platforms.add(PLATFORM_TYPE.Windows);
                            }
                          } else if (executor.getName().equals("sh")) {
                            if (!platforms.contains(PLATFORM_TYPE.Linux)) {
                              platforms.add(PLATFORM_TYPE.Linux);
                            }
                          } else if (executor.getName().equals("cmd")) {
                            if (!platforms.contains(PLATFORM_TYPE.Windows)) {
                              platforms.add(PLATFORM_TYPE.Windows);
                            }
                          }
                        }
                      });
              Contract contract =
                  executableContract(
                      contractConfig,
                      ability.getAbility_id(),
                      Map.of(en, ability.getName(), fr, ability.getName()),
                      builder.build(),
                      platforms,
                      true);
              contract.addAttackPattern(ability.getTechnique_id());
              return contract;
            }))
        .collect(Collectors.toList());
  }

  @Override
  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-caldera.png");
    return new ContractorIcon(iconStream);
  }
}
