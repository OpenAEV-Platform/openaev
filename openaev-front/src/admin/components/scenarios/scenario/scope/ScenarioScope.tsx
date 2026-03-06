import { AdjustOutlined, DownloadOutlined, EditOutlined, UploadFileOutlined } from '@mui/icons-material';
import { Button, Chip, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type ChangeEvent, type FunctionComponent, useCallback, useEffect, useRef, useState } from 'react';

import { findEndpoints } from '../../../../../actions/assets/endpoint-actions';
import { fetchWorkflow, updateWorkflowScope } from '../../../../../actions/workflows/workflow-actions';
import { useFormatter } from '../../../../../components/i18n';
import type { EndpointOutput } from '../../../../../utils/api-types';
import type { ScopeList, Workflow, WorkflowScope } from '../../../../../utils/api-types-custom';
import { download } from '../../../../../utils/utils';
import ScopeDefinitionDialog from './ScopeDefinitionDialog';

interface Props {
  scenarioId: string;
}

const EMPTY_SCOPE_LIST: ScopeList = { endpoint_ids: [], manual_entries: [] };
const DEFAULT_TIMEOUT_SECONDS = 7200;

const ScenarioScope: FunctionComponent<Props> = ({ scenarioId }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [workflow, setWorkflow] = useState<Workflow | null>(null);
  const [scope, setScope] = useState<WorkflowScope | null>(null);
  const [timeoutSeconds, setTimeoutSeconds] = useState<number>(DEFAULT_TIMEOUT_SECONDS);
  const [endpointNames, setEndpointNames] = useState<Record<string, string>>({});
  const [dialogOpen, setDialogOpen] = useState(false);

  const loadWorkflow = useCallback(() => {
    fetchWorkflow(scenarioId).then((result: { data: Workflow }) => {
      setWorkflow(result.data);
      setTimeoutSeconds(result.data.workflow_timeout ?? DEFAULT_TIMEOUT_SECONDS);
      if (result.data.workflow_scope) {
        try {
          const parsed = JSON.parse(result.data.workflow_scope) as WorkflowScope;
          setScope(parsed);
        } catch {
          setScope(null);
        }
      } else {
        setScope(null);
      }
    });
  }, [scenarioId]);

  useEffect(() => {
    loadWorkflow();
  }, [loadWorkflow]);

  // Resolve endpoint names for both whitelist and blacklist
  useEffect(() => {
    if (!scope) {
      setEndpointNames({});
      return;
    }
    const allIds = [
      ...scope.whitelist.endpoint_ids,
      ...scope.blacklist.endpoint_ids,
    ];
    if (allIds.length > 0) {
      findEndpoints(allIds).then((result: { data: EndpointOutput[] }) => {
        const names: Record<string, string> = {};
        result.data.forEach((ep) => {
          names[ep.asset_id] = ep.asset_name;
        });
        setEndpointNames(names);
      });
    } else {
      setEndpointNames({});
    }
  }, [scope]);

  const handleSave = (newScope: WorkflowScope, newTimeoutSeconds: number) => {
    updateWorkflowScope(scenarioId, newScope, newTimeoutSeconds).then(() => {
      setScope(newScope);
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
    if (!file) return;
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
        const currentScope: WorkflowScope = scope ?? { whitelist: { ...EMPTY_SCOPE_LIST }, blacklist: { ...EMPTY_SCOPE_LIST } };
        const existingEntries = new Set(currentScope.whitelist.manual_entries);
        const newEntries = entries.filter((en) => !existingEntries.has(en));
        const updatedScope: WorkflowScope = {
          ...currentScope,
          whitelist: {
            ...currentScope.whitelist,
            manual_entries: [...currentScope.whitelist.manual_entries, ...newEntries],
          },
        };
        updateWorkflowScope(scenarioId, updatedScope, timeoutSeconds).then(() => {
          setScope(updatedScope);
        });
      }
    };
    reader.readAsText(file);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const hasWhitelist = scope && (scope.whitelist.endpoint_ids.length > 0 || scope.whitelist.manual_entries.length > 0);
  const hasBlacklist = scope && (scope.blacklist.endpoint_ids.length > 0 || scope.blacklist.manual_entries.length > 0);
  const hasScope = hasWhitelist || hasBlacklist;

  const timeoutMinutes = Math.round(timeoutSeconds / 60);

  const renderScopeList = (label: string, color: 'success' | 'error', list: ScopeList) => {
    const hasEntries = list.endpoint_ids.length > 0 || list.manual_entries.length > 0;
    return (
      <div style={{ flex: 1 }}>
        <Typography variant="h4" sx={{ marginBottom: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
          <Chip label={label} size="small" color={color} variant="outlined" />
        </Typography>
        {hasEntries ? (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: theme.spacing(1) }}>
            {list.endpoint_ids.map((id) => (
              <Chip key={id} label={endpointNames[id] || id} variant="outlined" size="small" color={color} />
            ))}
            {list.manual_entries.map((entry) => (
              <Chip key={entry} label={entry} variant="filled" size="small" color={color} />
            ))}
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
          {renderScopeList(t('Whitelist'), 'success', scope!.whitelist)}
          {renderScopeList(t('Blacklist'), 'error', scope!.blacklist)}
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
        initialScope={scope ?? { whitelist: { ...EMPTY_SCOPE_LIST }, blacklist: { ...EMPTY_SCOPE_LIST } }}
        initialTimeoutSeconds={timeoutSeconds}
      />
    </Paper>
  );
};

export default ScenarioScope;
