package io.openaev.injectors.lade;

import static io.openaev.helper.SupportedLanguage.en;

import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.Contractor;
import io.openaev.injector_contract.ContractorIcon;
import io.openaev.injectors.lade.config.LadeConfig;
import io.openaev.injectors.lade.service.LadeService;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LadeContract extends Contractor {

  public static final String TYPE = "openaev_lade";

  public static final String LADE_DEFAULT = "0a6439e4-671d-4a57-9446-9648005b36ef";
  private LadeConfig config;
  private LadeService ladeService;

  @Autowired
  public void setLadeService(LadeService ladeService) {
    this.ladeService = ladeService;
  }

  @Override
  public boolean isExpose() {
    return Optional.ofNullable(config.getEnable()).orElse(false);
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return new ContractConfig(
        TYPE, Map.of(en, "Airbus LADE"), "#673AB7", "#673AB7", "/img/icon-lade.png", isExpose());
  }

  @Autowired
  public void setConfig(LadeConfig config) {
    this.config = config;
  }

  @Override
  public List<Contract> contracts() throws Exception {
    if (Optional.ofNullable(config.getEnable()).orElse(false)) {
      ContractConfig contractConfig = getConfig();
      return ladeService.buildContracts(contractConfig);
    }
    return List.of();
  }

  @Override
  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-lade.png");
    return new ContractorIcon(iconStream);
  }
}
