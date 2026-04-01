package io.openaev.api.capabilities;

import io.openaev.database.model.CapabilityScope;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/capabilities")
public class CapabilityApi {
  @Operation(
      summary = "Get the capability tree",
      description =
          "Returns the hierarchical tree of all capabilities. "
              + "Optionally filter by scope (PLATFORM or TENANT).")
  @GetMapping
  public ResponseEntity<List<CapabilityOutput>> getCapabilities(
      @RequestParam(required = false) CapabilityScope scope) {
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree(scope);
    return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(1))).body(tree);
  }
}
