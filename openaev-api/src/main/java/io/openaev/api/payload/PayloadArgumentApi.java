package io.openaev.api.payload;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.settings.PreviewFeature.INJECT_CHAINING;

import io.openaev.database.model.ArgumentType;
import io.openaev.service.PreviewFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PayloadArgumentApi.TENANT_PAYLOAD_ARGUMENTS_URI)
@RequiredArgsConstructor
public class PayloadArgumentApi {

  public static final String TENANT_PAYLOAD_ARGUMENTS_URI = TENANT_PREFIX + "/payload-arguments";

  private final PreviewFeatureService previewFeatureService;

  // -- READ --

  @Operation(
      summary = "Get all argument types with their sub-types",
      description = "Returns the argument types availables for payload arguments.")
  @GetMapping("/types")
  public ResponseEntity<List<ArgumentTypeOutput>> getArgumentTypes() {
    List<ArgumentTypeOutput> types =
        resolveAvailableTypes().stream().map(ArgumentTypeMapper::toOutput).toList();

    return ResponseEntity.ok(types);
  }

  private List<ArgumentType> resolveAvailableTypes() {
    if (!previewFeatureService.isFeatureEnabled(INJECT_CHAINING)) {
      return List.of(ArgumentType.Text, ArgumentType.Document);
    }
    return Arrays.stream(ArgumentType.values()).toList();
  }
}
