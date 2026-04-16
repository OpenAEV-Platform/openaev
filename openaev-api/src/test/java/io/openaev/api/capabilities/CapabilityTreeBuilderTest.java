package io.openaev.api.capabilities;

import static io.openaev.database.model.Capability.*;
import static io.openaev.database.model.CapabilityGroup.*;
import static io.openaev.database.model.CapabilityScope.PLATFORM;
import static io.openaev.database.model.CapabilityScope.TENANT;
import static org.assertj.core.api.Assertions.*;

import io.openaev.database.model.CapabilityScope;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilityTreeBuilderTest {

  @Test
  void should_build_full_tree_with_category_nodes() {
    // -- ACT --
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree();

    // -- ASSERT --
    assertThat(tree).isNotEmpty();
    // BYPASS is directly at root (no category wrapper)
    assertThat(tree).anyMatch(n -> BYPASS.name().equals(n.value()) && n.checkable());
    // ASSESSMENT is a non-checkable category node (value is the enum key, frontend translates)
    assertThat(tree).anyMatch(n -> ASSESSMENT.name().equals(n.value()) && !n.checkable());
  }

  @Test
  void should_have_access_teams_and_players_as_non_checkable_capability() {
    // -- ACT --
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree();

    CapabilityOutput targets =
        tree.stream().filter(n -> TARGETS.name().equals(n.value())).findFirst().orElseThrow();

    // -- ASSERT --
    // ACCESS_TEAMS_AND_PLAYERS is a real capability but not checkable (no resource/action pairs)
    CapabilityOutput accessTeams =
        targets.children().stream()
            .filter(n -> ACCESS_TEAMS_AND_PLAYERS.name().equals(n.value()))
            .findFirst()
            .orElseThrow();
    assertThat(accessTeams.checkable()).isFalse();
    // Underneath: MANAGE_TEAMS_AND_PLAYERS
    assertThat(accessTeams.children())
        .anyMatch(n -> MANAGE_TEAMS_AND_PLAYERS.name().equals(n.value()) && n.checkable());

    // ACCESS_ASSETS and ACCESS_SECURITY_PLATFORMS should also be direct children of Targets
    assertThat(targets.children()).anyMatch(n -> ACCESS_ASSETS.name().equals(n.value()));
    assertThat(targets.children())
        .anyMatch(n -> ACCESS_SECURITY_PLATFORMS.name().equals(n.value()));
  }

  @Test
  void should_have_scopes_on_category_nodes() {
    // -- ACT --
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree();

    // -- ASSERT --
    CapabilityOutput assessments =
        tree.stream().filter(n -> ASSESSMENT.name().equals(n.value())).findFirst().orElseThrow();
    assertThat(assessments.scopes()).containsExactly(TENANT.name());

    CapabilityOutput tenants =
        tree.stream().filter(n -> TENANTS.name().equals(n.value())).findFirst().orElseThrow();
    assertThat(tenants.scopes()).containsExactly(PLATFORM.name());
  }

  @Test
  void should_filter_by_platform_scope() {
    // -- ACT --
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree(CapabilityScope.PLATFORM);

    // -- ASSERT --
    assertThat(tree).anyMatch(n -> TENANTS.name().equals(n.value()));
    assertThat(tree).anyMatch(n -> PLATFORM_GROUPS_AND_ROLES.name().equals(n.value()));
    assertThat(tree).noneMatch(n -> ASSESSMENT.name().equals(n.value()));
    assertThat(tree).noneMatch(n -> PAYLOADS.name().equals(n.value()));
  }

  @Test
  void should_filter_by_tenant_scope() {
    // -- ACT --
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree(TENANT);

    // -- ASSERT --
    assertThat(tree).anyMatch(n -> ASSESSMENT.name().equals(n.value()));
    assertThat(tree).anyMatch(n -> TARGETS.name().equals(n.value()));
    assertThat(tree).anyMatch(n -> TENANT_SETTINGS.name().equals(n.value()));
    assertThat(tree).noneMatch(n -> TENANTS.name().equals(n.value()));
    assertThat(tree).noneMatch(n -> PLATFORM_GROUPS_AND_ROLES.name().equals(n.value()));
  }

  @Test
  void should_build_correct_hierarchy_for_tenant_settings() {
    // -- ACT --
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree(TENANT);

    // -- ASSERT --
    // TENANT_SETTING is a non-checkable category node in the tenant scope
    CapabilityOutput tenantSettingCategory =
        tree.stream()
            .filter(n -> TENANT_SETTINGS.name().equals(n.value()))
            .findFirst()
            .orElseThrow();
    assertThat(tenantSettingCategory.checkable()).isFalse();
    assertThat(tenantSettingCategory.scopes()).containsExactly(TENANT.name());

    // ACCESS_TENANT_SETTINGS is a checkable child of the category
    CapabilityOutput accessTenantSettings =
        tenantSettingCategory.children().stream()
            .filter(n -> ACCESS_TENANT_SETTINGS.name().equals(n.value()))
            .findFirst()
            .orElseThrow();
    assertThat(accessTenantSettings.checkable()).isTrue();

    // MANAGE_TENANT_SETTINGS is a checkable child of ACCESS
    CapabilityOutput manageTenantSettings =
        accessTenantSettings.children().stream()
            .filter(n -> MANAGE_TENANT_SETTINGS.name().equals(n.value()))
            .findFirst()
            .orElseThrow();
    assertThat(manageTenantSettings.checkable()).isTrue();

    // DELETE_TENANT_SETTINGS is a checkable child of MANAGE
    assertThat(manageTenantSettings.children())
        .anyMatch(n -> DELETE_TENANT_SETTINGS.name().equals(n.value()) && n.checkable());
  }

  @Test
  void should_not_include_tenant_settings_in_platform_scope() {
    // -- ACT --
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree(PLATFORM);

    // -- ASSERT --
    assertThat(tree).noneMatch(n -> TENANT_SETTINGS.name().equals(n.value()));
    assertThat(flattenValues(tree)).doesNotContain(ACCESS_TENANT_SETTINGS.name());
  }

  @Test
  void should_build_correct_hierarchy_within_categories() {
    // -- ACT --
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree();

    // -- ASSERT --
    CapabilityOutput tenantsCategory =
        tree.stream().filter(n -> TENANTS.name().equals(n.value())).findFirst().orElseThrow();
    CapabilityOutput accessTenants =
        tenantsCategory.children().stream()
            .filter(n -> ACCESS_TENANTS.name().equals(n.value()))
            .findFirst()
            .orElseThrow();
    assertThat(accessTenants.checkable()).isTrue();

    CapabilityOutput manageTenants =
        accessTenants.children().stream()
            .filter(n -> MANAGE_TENANTS.name().equals(n.value()))
            .findFirst()
            .orElseThrow();
    assertThat(manageTenants.children()).anyMatch(n -> DELETE_TENANTS.name().equals(n.value()));
  }

  @Test
  void category_nodes_should_not_be_checkable() {
    // -- ACT --
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree();

    // -- ASSERT --
    // Category nodes are not checkable
    tree.stream().filter(n -> !n.checkable()).forEach(n -> assertThat(n.children()).isNotEmpty());
  }

  @Test
  void hidden_capabilities_should_not_appear_in_tree() {
    // -- ACT --
    List<CapabilityOutput> tree = CapabilityTreeBuilder.buildTree();

    // -- ASSERT --
    // MANAGE_FINDINGS, DELETE_FINDINGS and MANAGE_STIX_BUNDLE are hidden
    assertThat(flattenValues(tree))
        .doesNotContain(MANAGE_FINDINGS.name(), DELETE_FINDINGS.name(), MANAGE_STIX_BUNDLE.name());
    // ACCESS_FINDINGS should still be visible
    assertThat(flattenValues(tree)).contains(ACCESS_FINDINGS.name());
    // STIX category should not appear (no visible children)
    assertThat(tree).noneMatch(n -> STIX.name().equals(n.value()));
  }

  /** Recursively collect all capability values from the tree. */
  private List<String> flattenValues(List<CapabilityOutput> nodes) {
    List<String> result = new java.util.ArrayList<>();
    for (CapabilityOutput node : nodes) {
      result.add(node.value());
      result.addAll(flattenValues(node.children()));
    }
    return result;
  }
}
