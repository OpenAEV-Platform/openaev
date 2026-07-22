package io.openaev.injectors.manual;

import static io.openaev.helper.SupportedLanguage.en;
import static io.openaev.helper.SupportedLanguage.fr;
import static io.openaev.injector_contract.Contract.manualContract;
import static io.openaev.injector_contract.ContractCardinality.Multiple;
import static io.openaev.injector_contract.ContractDef.contractBuilder;
import static io.openaev.injector_contract.fields.ContractExpectations.expectationsField;
import static io.openaev.injector_contract.fields.ContractTeam.teamField;

import io.openaev.database.model.Endpoint;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.Contractor;
import io.openaev.injector_contract.ContractorIcon;
import io.openaev.injector_contract.fields.ContractElement;
import io.openaev.injector_contract.fields.ContractExpectations;
import io.openaev.rest.domain.enums.PresetDomain;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManualContract extends Contractor {
  public static final String TYPE = "openaev_manual";

  public static final String MANUAL_DEFAULT = "d02e9132-b9d0-4daa-b3b1-4b9871f8472c";

  private final ExpectationBuilderService expectationBuilderService;

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return new ContractConfig(
        TYPE, Map.of(en, "Manual", fr, "Manuel"), "#009688", "#009688", "/img/manual.png");
  }

  @Override
  public List<Contract> contracts() {
    ContractConfig contractConfig = getConfig();
    ContractElement teams = teamField(Multiple);
    ContractExpectations expectations =
        expectationsField(List.of(this.expectationBuilderService.buildManualExpectation()));

    List<ContractElement> instance =
        contractBuilder().mandatoryOnCondition(teams, expectations).optional(expectations).build();

    return List.of(
        manualContract(
            contractConfig,
            MANUAL_DEFAULT,
            Map.of(en, "Manual", fr, "Manuel"),
            instance,
            List.of(Endpoint.PLATFORM_TYPE.Internal),
            false,
            Set.of(PresetDomain.getEmailInfiltration(), PresetDomain.getTabletop())));
  }

  @Override
  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-manual.png");
    return new ContractorIcon(iconStream);
  }
}
