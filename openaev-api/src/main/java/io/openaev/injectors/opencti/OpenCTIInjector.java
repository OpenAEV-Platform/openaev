package io.openaev.injectors.opencti;

import io.openaev.integrations.InjectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenCTIInjector {

  private static final String OPENCTI_INJECTOR_NAME = "OpenCTI";
  private static final String OPENCTI_INJECTOR_ID = "2cbc77af-67f2-46af-bfd2-755d06a46da0";

  @Autowired
  public OpenCTIInjector(InjectorService injectorService, OpenCTIContract contract) {
    try {
      injectorService.register(
          OPENCTI_INJECTOR_ID,
          OPENCTI_INJECTOR_NAME,
          contract,
          true,
          "incident-response",
          null,
          null,
          false);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
