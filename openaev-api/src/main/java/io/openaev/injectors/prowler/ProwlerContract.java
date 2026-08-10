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
 * Findings page has a stable {@code finding_source} value to filter/display on in advance. It
 * exposes no executable contracts (findings from a real Prowler connector will be attached
 * asynchronously through the execution callback, the same way Nuclei findings are, not via manually
 * triggered injects).
 *
 * <p>The Prowler injector is expected to forward Prowler's {@code -M json-ocsf} output completely
 * untouched (no transformation) to that callback, tagged with {@link
 * io.openaev.database.model.ContractOutputType#OCSF}. {@link
 * io.openaev.output_processor.OCSFOutputProcessor} is the component that then parses that native
 * OCSF JSON into {@link io.openaev.database.model.Finding}s (severity, resource/ARN, cloud
 * account/region, remediation, compliance) - it is looked up by output type through {@link
 * io.openaev.output_processor.OutputProcessorFactory}, independently of this placeholder
 * contractor.
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
