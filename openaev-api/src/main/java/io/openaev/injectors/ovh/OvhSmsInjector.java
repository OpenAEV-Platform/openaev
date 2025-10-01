package io.openaev.injectors.ovh;

import io.openaev.integrations.InjectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OvhSmsInjector {

  private static final String OVH_SMS_INJECTOR_NAME = "OVHCloud SMS Platform";
  private static final String OVH_SMS_INJECTOR_ID = "e5aefbca-cf8f-4a57-9384-0503a8ffc22f";

  @Autowired
  public OvhSmsInjector(InjectorService injectorService, OvhSmsContract contract) {
    try {
      injectorService.register(
          OVH_SMS_INJECTOR_ID,
          OVH_SMS_INJECTOR_NAME,
          contract,
          true,
          "communication",
          null,
          null,
          false);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
