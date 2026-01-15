package io.openaev.injectors.openaev;

import static io.openaev.helper.SupportedLanguage.en;
import static io.openaev.helper.SupportedLanguage.fr;

import io.openaev.helper.SupportedLanguage;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.Contractor;
import io.openaev.injector_contract.ContractorIcon;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenAEVImplantContract extends Contractor {

  public static final String TYPE = "openaev_implant";

  @Override
  public String getType() {
    return TYPE;
  }

  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-openaev.png");
    return new ContractorIcon(iconStream);
  }

  @Override
  public ContractConfig getConfig() {
    Map<SupportedLanguage, String> labels = Map.of(en, "OpenAEV Implant", fr, "OpenAEV Implant");
    return new ContractConfig(TYPE, labels, "#8b0000", "#8b0000", "/img/icon-openaev.png");
  }

  @Override
  public List<Contract> contracts() throws Exception {
    return List.of();
  }
}
