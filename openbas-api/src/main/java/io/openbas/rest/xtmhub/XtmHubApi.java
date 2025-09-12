package io.openbas.rest.xtmhub;


import io.openbas.rest.helper.RestBehavior;
import io.openbas.rest.settings.response.PlatformSettings;
import io.openbas.xtmhub.XtmHubService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class XtmHubApi extends RestBehavior {

  public static final String ENDPOINT_URI = "/api/xtmhub";

  private final XtmHubService xtmHubService;

  @PutMapping(ENDPOINT_URI + "/register")
  public PlatformSettings register(@Valid @RequestBody XtmHubRegisterInput input) {
    return this.xtmHubService.register(input.getToken());
  }

  @PutMapping(ENDPOINT_URI + "/unregister")
  public PlatformSettings unregister() { return this.xtmHubService.unregister();}
}
