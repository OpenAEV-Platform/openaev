package io.openaev.injectors.lade;

import io.openaev.integrations.InjectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LadeInjector {

  private static final String LADE_INJECTOR_NAME = "Airbus CyberRange (Lade)";
  private static final String LADE_INJECTOR_ID = "0097265b-0515-48a5-9bff-71d0f375fcc4";

  @Autowired
  public LadeInjector(InjectorService injectorService, LadeContract contract) {
    try {
      injectorService.register(
          LADE_INJECTOR_ID, LADE_INJECTOR_NAME, contract, false, "cyber-range", null, null, false);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
