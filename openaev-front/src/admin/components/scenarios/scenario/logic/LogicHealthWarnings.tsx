import { Add, InfoOutlined } from '@mui/icons-material';
import { Alert, AlertTitle, Button, Chip, IconButton, List, ListItem, ListItemIcon, ListItemSecondaryAction, ListItemText, Tooltip, Typography } from '@mui/material';
import { type FunctionComponent, useCallback, useState } from 'react';

import { simplePostCall } from '../../../../../utils/Action';
import Drawer from '../../../../../components/common/Drawer';
import FindingIcon from '../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../components/i18n';
import type { Workflow } from '../../../../../utils/api-types-custom';
import InjectIcon from '../../../common/injects/InjectIcon';
import { extractInputBindings, formatBinding, getActionsProvisioningField, getStepLabel, getRootActionSteps, isActionStep, isEventStep } from './logicUtils';

interface Props {
  workflow: Workflow;
  onAddActionFromContract?: (contractId: string, contractLabel: string) => void;
}

interface FieldWarning {
  fieldType: string;
  eventLabel: string;
}

interface BindingWarning {
  actionLabel: string;
  argumentKey: string;
  bindingLabel: string; // e.g. "portscan.host"
  inputType: string;
}

interface CompatibleContract {
  id: string;
  label: string;
  injectorType: string | undefined;
  injectorName: string | undefined;
  source: 'contract' | 'payload';
}

// Check if a contract provides a given output type (using search result fields)
const contractProvidesField = (contract: Record<string, unknown>, fieldType: string): boolean => {
  const content = contract.injector_contract_content as string | undefined;
  if (!content) return false;
  try {
    const parsed = JSON.parse(content);
    if (Array.isArray(parsed.outputs)) {
      return parsed.outputs.some((o: { type?: string }) => o.type === fieldType);
    }
  } catch { /* ignore */ }
  return false;
};

const LogicHealthWarnings: FunctionComponent<Props> = ({ workflow, onAddActionFromContract }) => {
  const { t } = useFormatter();
  const steps = workflow.workflow_steps;
  const rootActions = getRootActionSteps(steps);
  const allActions = steps.filter(isActionStep);
  const eventSteps = steps.filter(isEventStep);

  // Drawer state
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerFieldType, setDrawerFieldType] = useState('');
  const [drawerResults, setDrawerResults] = useState<CompatibleContract[]>([]);
  const [drawerLoading, setDrawerLoading] = useState(false);

  // Cache fetched contracts from targeted searches
  const [contractsCache, setContractsCache] = useState<Record<string, unknown>[] | null>(null);

  // Fetch contracts from injector types known to have outputs
  const fetchContractsWithOutputs = async (): Promise<Record<string, unknown>[]> => {
    if (contractsCache) return contractsCache;

    const searches = ['NetExec', 'Nmap', 'Nuclei'].map(term =>
      simplePostCall(
        '/api/injector_contracts/search',
        { size: 500, page: 0, sorts: [], textSearch: term },
      ).then(r => (r.data as { content: Record<string, unknown>[] }).content ?? [])
        .catch(() => [] as Record<string, unknown>[]),
    );

    const results = await Promise.all(searches);
    const all = results.flat();

    // Deduplicate by contract ID
    const seen = new Set<string>();
    const unique = all.filter(c => {
      const id = c.injector_contract_id as string;
      if (seen.has(id)) return false;
      seen.add(id);
      return true;
    });

    setContractsCache(unique);
    return unique;
  };

  const handleShowCompatible = useCallback(async (fieldType: string) => {
    setDrawerFieldType(fieldType);
    setDrawerOpen(true);
    setDrawerLoading(true);

    try {
      const contracts = await fetchContractsWithOutputs();

      const compatible: CompatibleContract[] = [];
      for (const contract of contracts) {
        if (contractProvidesField(contract, fieldType)) {
          const labels = contract.injector_contract_labels as Record<string, string> | undefined;
          let label = (contract.injector_contract_id as string).substring(0, 8);
          if (labels) {
            const firstLabel = Object.values(labels)[0];
            if (firstLabel) label = firstLabel;
          }
          compatible.push({
            id: contract.injector_contract_id as string,
            label,
            injectorType: contract.injector_contract_injector_type as string | undefined,
            injectorName: contract.injector_contract_injector_name as string | undefined,
            source: 'contract',
          });
        }
      }

      compatible.sort((a, b) => a.label.localeCompare(b.label));
      setDrawerResults(compatible);
    } catch {
      setDrawerResults([]);
    } finally {
      setDrawerLoading(false);
    }
  }, [contractsCache]);

  const handleAddAction = (contractId: string, contractLabel: string) => {
    if (onAddActionFromContract) {
      onAddActionFromContract(contractId, contractLabel);
      setDrawerOpen(false);
    }
  };

  // Find fields not provisioned by any action
  const fieldWarnings: FieldWarning[] = [];

  for (const step of eventSteps) {
    for (const condition of step.step_conditions) {
      if (condition.condition_key) {
        const providers = getActionsProvisioningField(allActions, condition.condition_key);
        if (providers.length === 0) {
          let eventLabel = `Step ${step.step_id.substring(0, 8)}`;
          try {
            const data = JSON.parse(step.step_data ?? '{}');
            eventLabel = data.event_name ?? eventLabel;
          } catch { /* ignore */ }

          fieldWarnings.push({ fieldType: condition.condition_key, eventLabel });
        }
      }
    }
  }

  // Find unresolved input bindings on action steps
  const bindingWarnings: BindingWarning[] = [];
  for (const step of allActions) {
    const bindings = extractInputBindings(step, steps);
    for (const binding of bindings) {
      if (!binding.resolved) {
        bindingWarnings.push({
          actionLabel: getStepLabel(step),
          argumentKey: binding.argumentKey,
          bindingLabel: formatBinding(binding),
          inputType: binding.inputType,
        });
      }
    }
  }

  const hasNoRootActions = rootActions.length === 0 && steps.length > 0;

  if (!hasNoRootActions && fieldWarnings.length === 0 && bindingWarnings.length === 0) return null;

  return (
    <>
      {hasNoRootActions && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          <AlertTitle>{t('Warning')}</AlertTitle>
          {t('No root actions found. At least one action should have no triggering conditions.')}
        </Alert>
      )}

      {fieldWarnings.map((warning, index) => (
        <Alert key={`field-${index}`} severity="warning" sx={{ mb: 2 }}>
          <AlertTitle>{t('Warning')}</AlertTitle>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span>
              {t('Event')} <strong>{warning.eventLabel}</strong>{' '}
              {t('references field')}{' '}
              <Chip
                size="small"
                icon={<FindingIcon findingType={warning.fieldType} />}
                label={warning.fieldType}
                variant="outlined"
                sx={{ height: 22, fontSize: 11, mx: 0.5 }}
              />{' '}
              {t('which is not provisioned by any action.')}
            </span>
            <Button
              size="small"
              variant="outlined"
              startIcon={<InfoOutlined />}
              onClick={() => handleShowCompatible(warning.fieldType)}
              sx={{ textTransform: 'none', fontSize: 11, whiteSpace: 'nowrap' }}
            >
              {t('Show compatible actions')}
            </Button>
          </div>
        </Alert>
      ))}

      {bindingWarnings.map((warning, index) => (
        <Alert key={`binding-${index}`} severity="error" sx={{ mb: 2 }}>
          <AlertTitle>{t('Unresolved input binding')}</AlertTitle>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span>
              {t('Action')} <strong>{warning.actionLabel}</strong>{' '}
              {t('argument')} <code>{warning.argumentKey}</code>{' '}
              {t('requires')}{' '}
              <Chip
                size="small"
                icon={<FindingIcon findingType={warning.inputType} />}
                label={warning.bindingLabel}
                variant="filled"
                color="error"
                sx={{ height: 22, fontSize: 11, mx: 0.5 }}
              />{' '}
              {t('but no upstream action produces this output type.')}
            </span>
            <Button
              size="small"
              variant="outlined"
              color="error"
              startIcon={<InfoOutlined />}
              onClick={() => handleShowCompatible(warning.inputType)}
              sx={{ textTransform: 'none', fontSize: 11, whiteSpace: 'nowrap' }}
            >
              {t('Show compatible actions')}
            </Button>
          </div>
        </Alert>
      ))}

      {/* Compatible actions drawer */}
      <Drawer
        open={drawerOpen}
        handleClose={() => setDrawerOpen(false)}
        title={`${t('Actions providing')} "${drawerFieldType}"`}
      >
        <div style={{ padding: 16 }}>
          {drawerLoading && (
            <Typography color="text.secondary">{t('Loading...')}</Typography>
          )}

          {!drawerLoading && drawerResults.length === 0 && (
            <Typography color="text.secondary">
              {t('No compatible actions found for this field type.')}
            </Typography>
          )}

          {!drawerLoading && drawerResults.length > 0 && (
            <>
              <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: 'block' }}>
                {drawerResults.length} {t('compatible action(s) found')}
              </Typography>
              <List dense>
                {drawerResults.map(item => (
                  <ListItem key={item.id} sx={{ borderRadius: 1, mb: 0.5, pr: 6 }}>
                    <ListItemIcon sx={{ minWidth: 36 }}>
                      <InjectIcon type={item.injectorType} size="small" tooltip={{}} />
                    </ListItemIcon>
                    <ListItemText
                      primary={item.label}
                      secondary={
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                          {item.injectorName && (
                            <Typography component="span" variant="caption" color="text.secondary">
                              {item.injectorName}
                            </Typography>
                          )}
                          {item.source === 'contract' && (
                            <Chip size="small" label="native" color="primary" variant="outlined" sx={{ height: 16, fontSize: 9 }} />
                          )}
                        </span>
                      }
                      primaryTypographyProps={{ variant: 'body2', fontWeight: 500 }}
                    />
                    {onAddActionFromContract && (
                      <ListItemSecondaryAction>
                        <Tooltip title={t('Add this action')}>
                          <IconButton
                            edge="end"
                            size="small"
                            color="primary"
                            onClick={() => handleAddAction(item.id, item.label)}
                          >
                            <Add />
                          </IconButton>
                        </Tooltip>
                      </ListItemSecondaryAction>
                    )}
                  </ListItem>
                ))}
              </List>
            </>
          )}
        </div>
      </Drawer>
    </>
  );
};

export default LogicHealthWarnings;
