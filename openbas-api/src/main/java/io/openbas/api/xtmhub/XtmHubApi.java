package io.openbas.api.xtmhub;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.ResourceType;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.rest.settings.response.PlatformSettings;
import io.openbas.xtmhub.XtmHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@Tag(name = "XTM HUB API", description = "Operations related to XTM Hub")
public class XtmHubApi extends RestBehavior {

  public static final String XTMHUB_URI = "/api/xtmhub";

  private final XtmHubService xtmHubService;

  @PutMapping(
      value = XTMHUB_URI + "/register",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Register OpenAEV into XTM Hub",
      description = "Save registration data into settings from XTM Hub registration")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Successful registration")})
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public PlatformSettings register(@Valid @RequestBody XtmHubRegisterInput input) {
    return this.xtmHubService.register(input.getToken());
  }

  @PutMapping(
      value = XTMHUB_URI + "/unregister",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Unregister OpenAEV from XTM Hub",
      description = "Delete XTM Hub registration data from Settings.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Successful unregistration")})
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public PlatformSettings unregister() {
    return this.xtmHubService.unregister();
  }

  @PostMapping(
      value = XTMHUB_URI + "/refresh-connectivity",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Refresh connectivity with XTM Hub",
      description = "Refresh status in settings and version in XTM Hub")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Successful refresh")})
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public PlatformSettings refreshConnectivity() {
    return this.xtmHubService.refreshConnectivity();
  }
}
