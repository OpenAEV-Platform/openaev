package io.openaev.api.payload;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.settings.PreviewFeature.INJECT_CHAINING;

import io.openaev.database.model.ChainingTypeRegistry;
import io.openaev.database.model.PrimitiveType;
import io.openaev.service.PreviewFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
      summary = "Get all primitive chaining types",
      description = "Returns primitive types available for payload arguments.")
  @Transactional
  @GetMapping("/types")
  public ResponseEntity<List<PrimitiveTypeOutput>> getArgumentTypes() {
    List<PrimitiveTypeOutput> types =
        resolveAvailableTypes().stream().map(PrimitiveTypeMapper::toOutput).toList();

    return ResponseEntity.ok(types);
  }

  private List<PrimitiveType> resolveAvailableTypes() {
    if (!previewFeatureService.isFeatureEnabled(INJECT_CHAINING)) {
      return List.of(PrimitiveType.Text, PrimitiveType.Document);
    }
    return ChainingTypeRegistry.getPrimitiveTypes();
  }
}
