package io.openaev.api.capabilities;

import io.openaev.database.model.CapabilityScope;
import io.openaev.database.model.User;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.UserService;
import io.openaev.service.account.PrivilegeEscalationValidator;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Set;
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
  private final UserService userService;

  @Operation(
      summary = "Get the capability tree",
      description =
          "Returns the hierarchical tree of all capabilities. "
              + "Optionally filter by scope (PLATFORM or TENANT).")
  @Transactional
  @GetMapping
  public ResponseEntity<List<CapabilityOutput>> getCapabilities(
      @RequestParam(required = false) CapabilityScope scope) {
    boolean credentialAssetEnabled =
        previewFeatureService.isFeatureEnabled(PreviewFeature.CREDENTIAL_ASSET);
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree(scope, credentialAssetEnabled);
    User currentUser = userService.currentUser();
    Set<CapabilityScope> effectiveScopes =
        scope == null ? Set.of(CapabilityScope.PLATFORM, CapabilityScope.TENANT) : Set.of(scope);
    List<CapabilityOutput> treeWithUserFlags =
        tree.stream().map(node -> withUserCanHave(node, currentUser, effectiveScopes)).toList();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(treeWithUserFlags);
  }

  private CapabilityOutput withUserCanHave(
      CapabilityOutput node, User currentUser, Set<CapabilityScope> effectiveScopes) {
    boolean userCanHave = computeUserCanHave(node, currentUser, effectiveScopes);
    List<CapabilityOutput> children =
        node.children().stream()
            .map(child -> withUserCanHave(child, currentUser, effectiveScopes))
            .toList();
    return new CapabilityOutput(
        node.value(), node.checkable(), userCanHave, node.scopes(), children);
  }

  private boolean computeUserCanHave(
      CapabilityOutput node, User currentUser, Set<CapabilityScope> effectiveScopes) {
    if (!node.checkable()) {
      return false;
    }
    io.openaev.database.model.Capability capability;
    try {
      capability = io.openaev.database.model.Capability.valueOf(node.value());
    } catch (IllegalArgumentException e) {
      return false;
    }
    return effectiveScopes.stream()
        .filter(capability.getScopes()::contains)
        .anyMatch(
            scope ->
                PrivilegeEscalationValidator.canAssignCapability(currentUser, capability, scope));
  }
}
