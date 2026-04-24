import { InfoOutlined } from '@mui/icons-material';
import { Box, Divider, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import type { AssetGroupsHelper } from '../../../../../actions/asset_groups/assetgroup-helper';
import type { EndpointHelper } from '../../../../../actions/assets/asset-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import type { WorkflowConfigurationOutput, WorkflowScopeRuleOutput } from '../../../../../utils/api-types';

interface ScopeRulesProps { workflowConfiguration: WorkflowConfigurationOutput | undefined }

interface ScopeColumnProps {
  title: string;
  rules: WorkflowScopeRuleOutput[];
  resolveLabel: (rule: WorkflowScopeRuleOutput) => string;
}

const ScopeColumn = ({ title, rules, resolveLabel }: ScopeColumnProps) => {
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

        {/*
        TODO : to add when we'll do the form
        <Button size="small" startIcon={<Add />}> */}
        {/*  {t('Add')} */}
        {/* </Button> */}
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

const ScopeRules = ({ workflowConfiguration }: ScopeRulesProps) => {
  const { t } = useFormatter();
  const theme = useTheme();

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
  const whitelisted = scopeRules.filter(
    (r: WorkflowScopeRuleOutput) => r.workflow_scope_rule_selected_mode === 'WHITELIST',
  );
  const blacklisted = scopeRules.filter(
    (r: WorkflowScopeRuleOutput) => r.workflow_scope_rule_selected_mode === 'BLACKLIST',
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
          <ScopeColumn title={t('Allow list')} rules={whitelisted} resolveLabel={resolveLabel} />

          <Divider
            orientation="vertical"
            flexItem
            sx={{ display: 'block' }}
          />

          <ScopeColumn title={t('Deny list')} rules={blacklisted} resolveLabel={resolveLabel} />
        </Box>
      </Paper>
    </Box>
  );
};

export default ScopeRules;
