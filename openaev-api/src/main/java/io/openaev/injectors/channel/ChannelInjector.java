package io.openaev.injectors.channel;

import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.integrations.InjectorService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChannelInjector {

  private static final String CHANNEL_INJECTOR_NAME = "Media pressure";
  private static final String CHANNEL_INJECTOR_ID = "8d932e36-353c-48fa-ba6f-86cb7b02ed19";

  @Autowired
  public ChannelInjector(InjectorService injectorService, ChannelContract contract) {
    try {
      injectorService.register(
          CHANNEL_INJECTOR_ID,
          CHANNEL_INJECTOR_NAME,
          contract,
          false,
          "media-pressure",
          null,
          null,
          false,
          List.of(ExternalServiceDependency.SMTP, ExternalServiceDependency.SMTP));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
