package io.openaev.injectors.mastodon;

import io.openaev.integrations.InjectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MastodonInjector {

  private static final String MASTODON_INJECTOR_NAME = "Mastodon";
  private static final String MASTODON_INJECTOR_ID = "37cd1743-8975-43c0-837c-f99970142e72";

  @Autowired
  public MastodonInjector(InjectorService injectorService, MastodonContract contract) {
    try {
      injectorService.register(
          MASTODON_INJECTOR_ID,
          MASTODON_INJECTOR_NAME,
          contract,
          false,
          "social-media",
          null,
          null,
          false);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
