package io.openaev.injectors.prowler;

import static io.openaev.helper.SupportedLanguage.en;
import static io.openaev.helper.SupportedLanguage.fr;

import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.Contractor;
import io.openaev.injector_contract.ContractorIcon;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Placeholder contractor for the Prowler injector (misconfiguration scanner).
 *
 * <p>Prowler is not integrated as a real connector yet: this catalog entry only exists so the
 * Findings page "Misconfiguration" tab has a stable {@code finding_source} value to filter on in
 * advance. It exposes no executable contracts (findings from a real Prowler connector will be
 * attached asynchronously, the same way Nuclei findings are, not via manually triggered injects).
 */
@Component
public class ProwlerContract extends Contractor {
  public static final String TYPE = "openaev_prowler";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return new ContractConfig(
        TYPE, Map.of(en, "Prowler", fr, "Prowler"), "#f97316", "#f97316", null);
  }

  @Override
  public List<Contract> contracts() {
    // No executable contracts: this injector is a catalog placeholder only.
    return List.of();
  }

  @Override
  public ContractorIcon getIcon() {
    return null;
  }
}
