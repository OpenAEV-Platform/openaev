package io.openaev.api.xtmhub;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.xtmhub.XtmHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@Tag(name = "XTM HUB API", description = "Operations related to XTM Hub")
public class XtmHubApi extends RestBehavior {

  public static final String XTMHUB_URI = "/api/xtmhub";
  public static final String TENANT_XTMHUB_URI = TENANT_PREFIX + "/xtmhub";

  private final XtmHubService xtmHubService;
  private final XtmHubRegistrationMapper xtmHubRegistrationMapper;

  @GetMapping(
      value = {XTMHUB_URI + "/registration", TENANT_XTMHUB_URI + "/registration"},
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get XTM Hub registration",
      description = "Returns the current tenant's XTM Hub registration, or 204 if not registered")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Registration found"),
    @ApiResponse(responseCode = "204", description = "Not registered")
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.XTM_HUB_REGISTRATION)
  @Transactional(readOnly = true)
  public ResponseEntity<XtmHubRegistrationOutput> getRegistration() {
    return this.xtmHubService
        .getRegistration()
        .map(xtmHubRegistrationMapper::toXtmHubRegistrationOutput)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.noContent().build());
  }

  @PutMapping(
      value = {XTMHUB_URI + "/register", TENANT_XTMHUB_URI + "/register"},
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Register OpenAEV into XTM Hub",
      description = "Save registration data into the XTM Hub registration entity")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Successful registration")})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.XTM_HUB_REGISTRATION)
  @Transactional(rollbackFor = Exception.class)
  public XtmHubRegistrationOutput register(@Valid @RequestBody XtmHubRegisterInput input) {
    return xtmHubRegistrationMapper.toXtmHubRegistrationOutput(
        this.xtmHubService.register(input.getToken()));
  }

  @PutMapping(
      value = {XTMHUB_URI + "/unregister", TENANT_XTMHUB_URI + "/unregister"},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Unregister OpenAEV from XTM Hub",
      description = "Delete XTM Hub registration data from the tenant registration entity.")
  @ApiResponses({@ApiResponse(responseCode = "204", description = "Successful unregistration")})
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.XTM_HUB_REGISTRATION)
  @Transactional(rollbackFor = Exception.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unregister() {
    this.xtmHubService.unregister();
  }

  @PostMapping(
      value = {XTMHUB_URI + "/refresh-connectivity", TENANT_XTMHUB_URI + "/refresh-connectivity"},
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Refresh connectivity with XTM Hub",
      description = "Refresh status in settings and version in XTM Hub")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Successful refresh"),
    @ApiResponse(
        responseCode = "204",
        description = "No registration found or platform not found on XTM Hub")
  })
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.XTM_HUB_REGISTRATION)
  @Transactional(rollbackFor = Exception.class)
  public ResponseEntity<XtmHubRegistrationOutput> refreshConnectivity() {
    return Optional.ofNullable(this.xtmHubService.refreshConnectivity())
        .map(xtmHubRegistrationMapper::toXtmHubRegistrationOutput)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.noContent().build());
  }

  @PutMapping(value = XTMHUB_URI + "/auto-register", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Autoregister OpenAEV into XTM Hub",
      description =
          "Register platform on xtmhub and Save registration data into settings from XTM Hub registration")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Successful registration"),
    @ApiResponse(responseCode = "502", description = "Registration failed on XTM Hub call"),
    @ApiResponse(responseCode = "500", description = "Internal error")
  })
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @Transactional(rollbackFor = Exception.class)
  public void autoRegister(@Valid @RequestBody XtmHubRegisterInput input) {
    this.xtmHubService.autoRegister(input.getToken());
  }

  @PostMapping(
      value = XTMHUB_URI + "/contact-us",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Contact Sales", description = "Contact the sales team throught XTM Hub")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Successful contact")})
  @AccessControl(skipRBAC = true)
  @Transactional(rollbackFor = Exception.class)
  public Boolean contactUs(@Valid @RequestBody XtmHubContactUsInput request) {
    return this.xtmHubService.contactUs(request.getMessage());
  }
}
