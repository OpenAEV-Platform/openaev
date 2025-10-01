const getAuthorizedPerspectives = (): Map<string, string[]> => new Map([
  ['expectation-inject', ['base_created_at', 'inject_expectation_status', 'inject_expectation_type', 'base_updated_at', 'base_simulation_side', 'base_scenario_side', 'base_asset_side', 'base_asset_group_side', 'base_security_platforms_side']],
  ['finding', ['base_created_at', 'finding_type', 'base_updated_at', 'base_endpoint_side', 'base_simulation_side', 'base_scenario_side']],
  ['endpoint', ['endpoint_arch', 'endpoint_platform', 'endpoint_ips', 'endpoint_hostname', 'base_simulation_side', 'base_scenario_side']],
  ['vulnerable-endpoint', ['vulnerable_endpoint_architecture', 'vulnerable_endpoint_agents_active_status', 'vulnerable_endpoint_agents_privileges', 'vulnerable_endpoint_platform', 'base_simulation_side', 'base_scenario_side']],
  ['inject', ['inject_status', 'base_tags_side', 'base_assets_side', 'base_asset_groups_side', 'base_teams_side', 'base_platforms_side_denormalized', 'base_simulation_side', 'base_scenario_side']],
  ['simulation', ['status', 'base_tags_side', 'base_assets_side', 'base_asset_groups_side', 'base_teams_side', 'base_platforms_side_denormalized', 'base_scenario_side']],
  ['scenario', ['status', 'base_tags_side', 'base_assets_side', 'base_asset_groups_side', 'base_teams_side', 'base_platforms_side_denormalized']],
]);

export default getAuthorizedPerspectives;
