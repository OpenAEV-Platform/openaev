import { Add, InfoOutlined } from '@mui/icons-material';
import { Box, Button, Divider, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

import type { AssetGroupsHelper } from '../../../actions/asset_groups/assetgroup-helper';
import type { EndpointHelper } from '../../../actions/assets/asset-helper';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import type {
  WorkflowConfigurationInput,
  WorkflowConfigurationOutput,
  WorkflowScopeRuleInput,
  WorkflowScopeRuleOutput,
} from '../../../utils/api-types';
import ScopeForm, { type ScopeCustomRule } from './ScopeForm';

type ScopeMode = 'ALLOWLIST' | 'DENYLIST';

interface ScopeRulesProps {
  workflowConfiguration: WorkflowConfigurationOutput | undefined;
  onUpdate: (overrides: Partial<WorkflowConfigurationInput>) => void;
}

interface ScopeColumnProps {
  title: string;
  rules: WorkflowScopeRuleOutput[];
  resolveLabel: (rule: WorkflowScopeRuleOutput) => string;
  onAdd: () => void;
}

const ScopeColumn = ({ title, rules, resolveLabel, onAdd }: ScopeColumnProps) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <Box sx={{
      display: 'grid',
      gap: 1.5,
      minHeight: 140,
    }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: theme.spacing(2),
        }}
      >
        <Typography variant="subtitle2">
          {title}
          {' '}
          (
          {rules.length}
          )
        </Typography>

        <Button size="small" startIcon={<Add />} onClick={onAdd}>
          {t('Add')}
        </Button>
      </Box>

      {rules.length > 0 ? (
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {rules.map(rule => resolveLabel(rule)).join(', ')}
        </Typography>
      ) : (
        <Typography variant="body2" sx={{ color: 'text.disabled' }}>
          {t('No asset added yet.')}
        </Typography>
      )}
    </Box>
  );
};

const ScopeRules = ({ workflowConfiguration, onUpdate }: ScopeRulesProps) => {
  const { t } = useFormatter();
  const theme = useTheme();

  // Drawer state
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<ScopeMode>('ALLOWLIST');

  // Selected IDs for the form
  const [selectedEndpointIds, setSelectedEndpointIds] = useState<string[]>([]);
  const [selectedAssetGroupIds, setSelectedAssetGroupIds] = useState<string[]>([]);
  const [selectedCustomRules, setSelectedCustomRules] = useState<ScopeCustomRule[]>([]);
  const [initialEndpointIds, setInitialEndpointIds] = useState<string[]>([]);
  const [initialAssetGroupIds, setInitialAssetGroupIds] = useState<string[]>([]);
  const [initialCustomRules, setInitialCustomRules] = useState<ScopeCustomRule[]>([]);

  const handleOpenDrawer = (mode: ScopeMode) => {
    setDrawerMode(mode);

    // Pre-populate with existing rules for the given mode
    const scopeRules = workflowConfiguration?.workflow_scope_rules ?? [];
    const rulesForMode = scopeRules.filter(r => r.workflow_scope_rule_selected_mode === mode);
    const endpointIds = rulesForMode
      .filter(r => r.workflow_scope_rule_source === 'ASSET')
      .map(r => r.workflow_scope_rule_value ?? '')
      .filter(Boolean);
    const assetGroupIds = rulesForMode
      .filter(r => r.workflow_scope_rule_source === 'ASSET_GROUP')
      .map(r => r.workflow_scope_rule_value ?? '')
      .filter(Boolean);
    const customRules = rulesForMode
      .filter(r => r.workflow_scope_rule_source === 'MANUAL' || r.workflow_scope_rule_source === 'CSV')
      .map(r => ({
        source: (r.workflow_scope_rule_source as 'MANUAL' | 'CSV') ?? 'CSV',
        value: r.workflow_scope_rule_value ?? '',
      }))
      .filter(r => !!r.value);

    setSelectedEndpointIds(endpointIds);
    setSelectedAssetGroupIds(assetGroupIds);
    setSelectedCustomRules(customRules);
    setInitialEndpointIds(endpointIds);
    setInitialAssetGroupIds(assetGroupIds);
    setInitialCustomRules(customRules);

    setDrawerOpen(true);
  };

  const handleCloseDrawer = () => setDrawerOpen(false);

  const handleSubmitScope = () => {
    // Build new rules for the current drawer mode
    const newRulesForMode: WorkflowScopeRuleInput[] = [
      ...selectedEndpointIds.map(id => ({
        workflow_scope_rule_selected_mode: drawerMode,
        workflow_scope_rule_source: 'ASSET' as const,
        workflow_scope_rule_value: id,
      })),
      ...selectedAssetGroupIds.map(id => ({
        workflow_scope_rule_selected_mode: drawerMode,
        workflow_scope_rule_source: 'ASSET_GROUP' as const,
        workflow_scope_rule_value: id,
      })),
      ...selectedCustomRules.map(rule => ({
        workflow_scope_rule_selected_mode: drawerMode,
        workflow_scope_rule_source: rule.source,
        workflow_scope_rule_value: rule.value,
      })),
    ];

    // Keep existing rules for the OTHER mode, replace rules for the current mode
    const otherMode: ScopeMode = drawerMode === 'ALLOWLIST' ? 'DENYLIST' : 'ALLOWLIST';
    const existingRulesOtherMode: WorkflowScopeRuleInput[] = (workflowConfiguration?.workflow_scope_rules ?? [])
      .filter(r => r.workflow_scope_rule_selected_mode === otherMode)
      .map(r => ({
        workflow_scope_rule_id: r.workflow_scope_rule_id,
        workflow_scope_rule_selected_mode: r.workflow_scope_rule_selected_mode!,
        workflow_scope_rule_source: r.workflow_scope_rule_source!,
        workflow_scope_rule_value: r.workflow_scope_rule_value!,
      }));

    onUpdate({ workflow_scope_rules: [...existingRulesOtherMode, ...newRulesForMode] });

    handleCloseDrawer();
  };

  const drawerTitle = drawerMode === 'ALLOWLIST'
    ? t('Define allowlisted scope')
    : t('Define denylisted scope');

  const { endpointsMap, assetGroupsMap } = useHelper((helper: EndpointHelper & AssetGroupsHelper) => ({
    endpointsMap: helper.getEndpointsMap(),
    assetGroupsMap: helper.getAssetGroupMaps(),
  }));

  const resolveLabel = (rule: WorkflowScopeRuleOutput): string => {
    const value = rule.workflow_scope_rule_value ?? '';

    switch (rule.workflow_scope_rule_source) {
      case 'ASSET': {
        const endpoint = endpointsMap[value];
        return endpoint?.asset_name ?? value;
      }
      case 'ASSET_GROUP': {
        const group = assetGroupsMap[value];
        return group?.asset_group_name ?? value;
      }
      default:
        return value;
    }
  };

  const scopeRules = workflowConfiguration?.workflow_scope_rules ?? [];
  const allowlisted = scopeRules.filter(
    (r: WorkflowScopeRuleOutput) => r.workflow_scope_rule_selected_mode === 'ALLOWLIST',
  );
  const denylisted = scopeRules.filter(
    (r: WorkflowScopeRuleOutput) => r.workflow_scope_rule_selected_mode === 'DENYLIST',
  );

  return (
    <Box sx={{
      display: 'grid',
      gap: theme.spacing(1),
    }}
    >
      <Typography
        variant="h4"
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
        }}
      >
        {t('Scope')}
        <Tooltip title={t('Entries in the deny list always take priority over those in the allow list.')}>
          <InfoOutlined
            color="primary"
            sx={{
              fontSize: 18,
              cursor: 'pointer',
            }}
          />
        </Tooltip>
      </Typography>

      <Paper variant="outlined" sx={{ p: theme.spacing(2) }}>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: '1fr auto 1fr',
            gap: theme.spacing(2),
            alignItems: 'start',
          }}
        >
          <ScopeColumn
            title={t('Allow list')}
            rules={allowlisted}
            resolveLabel={resolveLabel}
            onAdd={() => handleOpenDrawer('ALLOWLIST')}
          />

          <Divider
            orientation="vertical"
            flexItem
            sx={{ display: 'block' }}
          />

          <ScopeColumn
            title={t('Deny list')}
            rules={denylisted}
            resolveLabel={resolveLabel}
            onAdd={() => handleOpenDrawer('DENYLIST')}
          />
        </Box>
      </Paper>

      <Drawer
        open={drawerOpen}
        handleClose={handleCloseDrawer}
        title={drawerTitle}
      >
        <ScopeForm
          mode={drawerMode}
          selectedEndpointIds={selectedEndpointIds}
          selectedAssetGroupIds={selectedAssetGroupIds}
          selectedCustomRules={selectedCustomRules}
          initialEndpointIds={initialEndpointIds}
          initialAssetGroupIds={initialAssetGroupIds}
          initialCustomRules={initialCustomRules}
          onEndpointIdsChange={setSelectedEndpointIds}
          onAssetGroupIdsChange={setSelectedAssetGroupIds}
          onCustomRulesChange={setSelectedCustomRules}
          onCancel={handleCloseDrawer}
          onSubmit={handleSubmitScope}
        />
      </Drawer>
    </Box>
  );
};

export default ScopeRules;
