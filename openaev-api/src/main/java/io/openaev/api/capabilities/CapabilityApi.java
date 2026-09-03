package io.openaev.api.capabilities;

import io.openaev.context.TxCtx;
import io.openaev.database.model.CapabilityScope;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/capabilities")
public class CapabilityApi {

  private final PreviewFeatureService previewFeatureService;

  @Operation(
      summary = "Get the capability tree",
      description =
          "Returns the hierarchical tree of all capabilities. "
              + "Optionally filter by scope (PLATFORM or TENANT).")
  @Transactional
  @GetMapping
  public ResponseEntity<List<CapabilityOutput>> getCapabilities(
      TxCtx ctx, @RequestParam(required = false) CapabilityScope scope) {
    boolean credentialAssetEnabled =
        previewFeatureService.isFeatureEnabled(PreviewFeature.CREDENTIAL_ASSET);
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree(scope, credentialAssetEnabled);
    return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(1))).body(tree);
  }
}
