package io.openaev.injectors.phishing;

import static io.openaev.helper.SupportedLanguage.en;
import static io.openaev.helper.SupportedLanguage.fr;

import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.Contractor;
import io.openaev.injector_contract.ContractorIcon;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Contractor for the internal phishing injector. Unlike static injectors, this contractor exposes
 * NO static contracts: each {@code PhishingLandingPage} synthesizes its own {@code
 * InjectorContract} (a Threat Arsenal action) through {@code
 * PhishingLandingPageService.synchroniseInjectorContract}, mirroring how payloads generate their
 * contracts. The synthesized contract id equals the landing page id, so {@code PhishingExecutor}
 * resolves the landing page directly from the inject contract.
 */
@Component
public class PhishingContract extends Contractor {

  public static final String TYPE = "openaev_phishing";

  /** Stable per-tenant injector id (registered by the builtin integration factory). */
  public static final String PHISHING_INJECTOR_ID = "3d1a9f52-6c8e-4c2a-9f77-2b0d5f9a1e64";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return new ContractConfig(
        TYPE, Map.of(en, "Phishing", fr, "Hameconnage"), "#e91e63", "#e91e63", "/img/email.png");
  }

  @Override
  public List<Contract> contracts() {
    // Contracts are synthesized per landing page (see PhishingLandingPageService), so there is no
    // static contract to register here.
    return List.of();
  }

  @Override
  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-email.png");
    return new ContractorIcon(iconStream);
  }
}
