package io.openaev.injectors.manual;

import io.openaev.integrations.InjectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ManualInjector {

  private static final String MANUAL_INJECTOR_NAME = "Manual";
  private static final String MANUAL_INJECTOR_ID = "6981a39d-e219-4016-a235-cf7747994abc";

  @Autowired
  public ManualInjector(InjectorService injectorService, ManualContract contract) {
    try {
      injectorService.register(
          MANUAL_INJECTOR_ID, MANUAL_INJECTOR_NAME, contract, true, "generic", null, null, false);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
