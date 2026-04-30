import { AdjustOutlined, DownloadOutlined, EditOutlined, UploadFileOutlined } from '@mui/icons-material';
import { Button, Chip, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type ChangeEvent, type FunctionComponent, useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router';

import { findEndpoints } from '../../../../../actions/assets/endpoint-actions';
import { fetchWorkflowConfig, updateWorkflowScopeRules } from '../../../../../actions/chaining/workflow-actions';
import type { ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import type { EndpointOutput, Scenario, WorkflowScopeRuleInput, WorkflowScopeRuleOutput } from '../../../../../utils/api-types';
import { download } from '../../../../../utils/utils';
import ScopeDefinitionDialog from './ScopeDefinitionDialog';

const ScenarioScope: FunctionComponent = () => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { t } = useFormatter();
  const theme = useTheme();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  const workflowId = scenario?.scenario_workflow_id;

  const [scopeRules, setScopeRules] = useState<WorkflowScopeRuleOutput[]>([]);
  const [timeoutSeconds, setTimeoutSeconds] = useState<number>(7200);
  const [endpointNames, setEndpointNames] = useState<Record<string, string>>({});
  const [dialogOpen, setDialogOpen] = useState(false);

  const loadConfig = useCallback(() => {
    if (!workflowId) return;
    fetchWorkflowConfig(workflowId).then((config) => {
      setScopeRules(config.workflow_scope_rules ?? []);
      setTimeoutSeconds(config.workflow_configuration_timeout_seconds ?? 7200);
    });
  }, [workflowId]);

  useEffect(() => {
    loadConfig();
  }, [loadConfig]);

  // Resolve endpoint names from ASSET rules
  useEffect(() => {
    const assetIds = scopeRules
      .filter((r) => r.workflow_scope_rule_source === 'ASSET')
      .map((r) => r.workflow_scope_rule_value!)
      .filter(Boolean);
    if (assetIds.length > 0) {
      findEndpoints(assetIds).then((result: { data: EndpointOutput[] }) => {
        const names: Record<string, string> = {};
        result.data.forEach((ep) => { names[ep.asset_id] = ep.asset_name; });
        setEndpointNames(names);
      });
    } else {
      setEndpointNames({});
    }
  }, [scopeRules]);

  const handleSave = (newRules: WorkflowScopeRuleInput[], newTimeoutSeconds: number) => {
    if (!workflowId) return;
    updateWorkflowScopeRules(workflowId, newRules, newTimeoutSeconds).then(() => {
      setScopeRules(newRules as WorkflowScopeRuleOutput[]);
      setTimeoutSeconds(newTimeoutSeconds);
      setDialogOpen(false);
    });
  };

  const handleDownloadCsv = () => {
    const csv = 'type,value\nip,192.168.1.1\nhostname,dc01.corp.local\nsubnet,10.0.0.0/24\n';
    download(csv, 'scope_sample.csv', 'text/csv');
  };

  const handleImportCsv = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || !workflowId) return;
    const reader = new FileReader();
    reader.onload = (e) => {
      const text = e.target?.result as string;
      const lines = text.split('\n').map((l) => l.trim()).filter((l) => l.length > 0);
      const startIdx = lines.length > 0 && lines[0].toLowerCase().startsWith('type') ? 1 : 0;
      const entries: string[] = [];
      for (let i = startIdx; i < lines.length; i++) {
        const parts = lines[i].split(',');
        if (parts.length >= 2) {
          entries.push(parts.slice(1).join(',').trim());
        }
      }
      if (entries.length > 0) {
        const existingManual = new Set(
          scopeRules
            .filter((r) => r.workflow_scope_rule_source === 'MANUAL' && r.workflow_scope_rule_selected_mode === 'WHITELIST')
            .map((r) => r.workflow_scope_rule_value),
        );
        const newRules: WorkflowScopeRuleInput[] = entries
          .filter((en) => !existingManual.has(en))
          .map((en) => ({
            workflow_scope_rule_selected_mode: 'WHITELIST' as const,
            workflow_scope_rule_source: 'MANUAL' as const,
            workflow_scope_rule_value: en,
          }));
        const allRules = [...scopeRules as WorkflowScopeRuleInput[], ...newRules];
        updateWorkflowScopeRules(workflowId, allRules, timeoutSeconds).then(() => {
          setScopeRules(allRules as WorkflowScopeRuleOutput[]);
        });
      }
    };
    reader.readAsText(file);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  if (!workflowId) return null;

  const whitelistRules = scopeRules.filter((r) => r.workflow_scope_rule_selected_mode === 'WHITELIST');
  const blacklistRules = scopeRules.filter((r) => r.workflow_scope_rule_selected_mode === 'BLACKLIST');
  const hasScope = scopeRules.length > 0;
  const timeoutMinutes = Math.round(timeoutSeconds / 60);

  const renderRuleList = (label: string, color: 'success' | 'error', rules: WorkflowScopeRuleOutput[]) => {
    return (
      <div style={{ flex: 1 }}>
        <Typography variant="h4" sx={{ marginBottom: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
          <Chip label={label} size="small" color={color} variant="outlined" />
        </Typography>
        {rules.length > 0 ? (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: theme.spacing(1) }}>
            {rules.map((rule, idx) => {
              const isAsset = rule.workflow_scope_rule_source === 'ASSET';
              const displayLabel = isAsset
                ? (endpointNames[rule.workflow_scope_rule_value!] || rule.workflow_scope_rule_value)
                : rule.workflow_scope_rule_value;
              return (
                <Chip
                  key={`${rule.workflow_scope_rule_value}-${idx}`}
                  label={displayLabel}
                  variant={isAsset ? 'outlined' : 'filled'}
                  size="small"
                  color={color}
                />
              );
            })}
          </div>
        ) : (
          <Typography variant="body2" color="text.secondary">{t('No entries')}</Typography>
        )}
      </div>
    );
  };

  return (
    <Paper variant="outlined" sx={{ padding: 2 }}>
      <Typography variant="h4" sx={{ marginBottom: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ display: 'flex', alignItems: 'center', gap: theme.spacing(1) }}>
          <AdjustOutlined fontSize="inherit" />
          {t('Scope')}
          {hasScope && (
            <Chip
              label={`${t('Timeout')}: ${timeoutMinutes} min`}
              size="small"
              variant="outlined"
            />
          )}
        </span>
        <span style={{ display: 'flex', gap: theme.spacing(1) }}>
          <Button size="small" startIcon={<DownloadOutlined />} onClick={handleDownloadCsv}>
            {t('Download sample CSV')}
          </Button>
          <Button size="small" startIcon={<UploadFileOutlined />} onClick={() => fileInputRef.current?.click()}>
            {t('Import CSV')}
          </Button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv"
            style={{ display: 'none' }}
            onChange={handleImportCsv}
          />
          {hasScope && (
            <Button size="small" startIcon={<EditOutlined />} onClick={() => setDialogOpen(true)}>
              {t('Edit')}
            </Button>
          )}
        </span>
      </Typography>
      {hasScope ? (
        <div style={{ display: 'flex', gap: theme.spacing(3) }}>
          {renderRuleList(t('Whitelist'), 'success', whitelistRules)}
          {renderRuleList(t('Blacklist'), 'error', blacklistRules)}
        </div>
      ) : (
        <div style={{ textAlign: 'center', padding: theme.spacing(3) }}>
          <Typography variant="body2" color="text.secondary" sx={{ marginBottom: 2 }}>
            {t('No scope defined. Define which assets and endpoints are targeted by this scenario.')}
          </Typography>
          <Button variant="outlined" startIcon={<AdjustOutlined />} onClick={() => setDialogOpen(true)}>
            {t('Define Scope')}
          </Button>
        </div>
      )}
      <ScopeDefinitionDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onSubmit={handleSave}
        initialRules={scopeRules as WorkflowScopeRuleInput[]}
        initialTimeoutSeconds={timeoutSeconds}
      />
    </Paper>
  );
};

export default ScenarioScope;
