import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import ScopeRules from '../../../../admin/components/chaining/ScopeRules';
import type { WorkflowConfigurationOutput } from '../../../../utils/api-types';

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

vi.mock('../../../../store', () => ({ useHelper: vi.fn() }));

vi.mock('../../../../components/common/Drawer', () => ({ default: () => null }));

vi.mock('../../../../admin/components/chaining/ScopeForm', () => ({ default: () => null }));

import { useHelper } from '../../../../store';

const renderScopeRules = (maps: {
  endpointsMap: Record<string, { asset_name: string }>;
  assetGroupsMap: Record<string, { asset_group_name: string }>;
}) => {
  vi.mocked(useHelper).mockImplementation((selector: (helper: {
    getEndpointsMap: () => Record<string, { asset_name: string }>;
    getAssetGroupMaps: () => Record<string, { asset_group_name: string }>;
  }) => unknown) => selector({
    getEndpointsMap: () => maps.endpointsMap,
    getAssetGroupMaps: () => maps.assetGroupsMap,
  }));

  const workflowConfiguration = {
    workflow_scope_rules: [
      {
        workflow_scope_rule_selected_mode: 'ALLOWLIST',
        workflow_scope_rule_source: 'ASSET',
        workflow_scope_rule_value: 'asset-id-1',
      },
    ],
  } as WorkflowConfigurationOutput;

  render(
    <ScopeRules
      workflowConfiguration={workflowConfiguration}
      onUpdate={() => {}}
    />,
  );
};

describe('ScopeRules', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('given_assetNameNotLoaded_should_displayLoadingLabelInsteadOfRawId', () => {
    // Arrange
    renderScopeRules({
      endpointsMap: {},
      assetGroupsMap: {},
    });

    // Assert
    expect(screen.getByText('Loading...')).toBeDefined();
    expect(screen.queryByText('asset-id-1')).toBeNull();
  });

  it('given_assetNameLoaded_should_displayAssetName', () => {
    // Arrange
    renderScopeRules({
      endpointsMap: { 'asset-id-1': { asset_name: 'Laptop-Fili6159' } },
      assetGroupsMap: {},
    });

    // Assert
    expect(screen.getByText('Laptop-Fili6159')).toBeDefined();
  });
});
