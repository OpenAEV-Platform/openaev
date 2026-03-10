import { InfoOutlined, LanguageOutlined, LinkOutlined, PlayArrowOutlined, PushPinOutlined } from '@mui/icons-material';
import { Button, Chip, Divider, Switch, TextField, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';

import { directFetchInjectorContract } from '../../../../../actions/InjectorContracts';
import Drawer from '../../../../../components/common/Drawer';
import FindingIcon from '../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../components/i18n';
import type { InjectorContract } from '../../../../../utils/api-types';
import type { ContractElement, WorkflowStep } from '../../../../../utils/api-types-custom';
import InjectIcon from '../../../common/injects/InjectIcon';
import {
  extractInputBindings,
  extractOutputTypesFromStepData,
  formatBinding,
  getFieldScopes,
  getStepAttackPatterns,
  getStepInjectorType,
  getStepLabel,
  hasDependOnCondition,
  type InputBinding,
} from './logicUtils';

// Fields to hide in chaining (replaced by workflow scope / upstream binding)
const HIDDEN_FIELD_KEYS = new Set([
  'target_selector', 'targets', 'assets', 'asset_groups', 'assetgroups', 'asset-groups',
  'target_property_selector', 'expectations',
]);

interface Props {
  open: boolean;
  handleClose: () => void;
  step: WorkflowStep | null;
  allSteps: WorkflowStep[];
  onSave: (step: WorkflowStep, title: string, fieldScopes: Record<string, string>, injectContent: Record<string, unknown>) => void;
  attackPatternsMap: Record<string, { attack_pattern_external_id?: string }>;
}

const LogicActionEditDrawer: FunctionComponent<Props> = ({
  open,
  handleClose,
  step,
  allSteps,
  onSave,
  attackPatternsMap,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [title, setTitle] = useState('');
  const [fieldScopes, setFieldScopes] = useState<Record<string, string>>({});
  const [contractFields, setContractFields] = useState<ContractElement[]>([]);
  const [injectContent, setInjectContent] = useState<Record<string, unknown>>({});
  const [inputBindings, setInputBindings] = useState<InputBinding[]>([]);

  // Load contract fields on open
  const loadContract = useCallback(async (contractId: string) => {
    try {
      const result = await directFetchInjectorContract(contractId);
      const contract = result.data as InjectorContract;
      if (contract.injector_contract_content) {
        const content = JSON.parse(contract.injector_contract_content);
        if (Array.isArray(content.fields)) {
          setContractFields(content.fields);
        }
      }
    } catch { /* ignore */ }
  }, []);

  useEffect(() => {
    if (step && open) {
      setTitle(getStepLabel(step));
      setFieldScopes(getFieldScopes(step));
      setInputBindings(extractInputBindings(step, allSteps));

      // Load existing inject_content
      try {
        const data = JSON.parse(step.step_data ?? '{}');
        setInjectContent(data.inject_content ?? {});
        // Load contract
        const contractId = data.inject_injector_contract;
        if (contractId) loadContract(contractId);
      } catch {
        setInjectContent({});
      }
    } else {
      setContractFields([]);
      setInjectContent({});
      setInputBindings([]);
    }
  }, [step, open, allSteps, loadContract]);

  if (!step) return null;

  const injectorType = getStepInjectorType(step);
  const outputTypes = extractOutputTypesFromStepData(step);
  const isDownstream = hasDependOnCondition(step);
  const attackPatternIds = getStepAttackPatterns(step);
  const attackPatternExternalIds = attackPatternIds
    .map(id => attackPatternsMap[id]?.attack_pattern_external_id)
    .filter((eid): eid is string => !!eid);

  const toggleFieldScope = (fieldType: string) => {
    setFieldScopes(prev => ({
      ...prev,
      [fieldType]: (prev[fieldType] ?? 'LOCAL') === 'LOCAL' ? 'GLOBAL' : 'LOCAL',
    }));
  };

  const updateFieldValue = (key: string, value: unknown) => {
    setInjectContent(prev => ({ ...prev, [key]: value }));
  };

  // Find binding for a contract field
  const getBindingForField = (fieldKey: string): InputBinding | null =>
    inputBindings.find(b => b.argumentKey === fieldKey) ?? null;

  // Visible contract fields (exclude hidden ones)
  const visibleFields = contractFields.filter(f => !HIDDEN_FIELD_KEYS.has(f.key));

  const handleSubmit = () => {
    onSave(step, title, fieldScopes, injectContent);
    handleClose();
  };

  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={t('Edit action')}
    >
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
          padding: theme.spacing(2),
        }}
      >
        {/* Section 1: Action info */}
        <div style={{ display: 'flex', alignItems: 'center', gap: theme.spacing(1.5) }}>
          {injectorType
            ? <InjectIcon type={injectorType} size="medium" tooltip={{}} />
            : <PlayArrowOutlined color="primary" sx={{ fontSize: 28 }} />
          }
          <div>
            <Typography variant="subtitle2" color="text.secondary">
              {injectorType ?? t('Unknown injector')}
            </Typography>
            {attackPatternExternalIds.length > 0 && (
              <div style={{ display: 'flex', gap: 4, marginTop: 2, flexWrap: 'wrap' }}>
                {attackPatternExternalIds.map(eid => (
                  <Chip key={eid} size="small" label={eid} variant="outlined" color="secondary" sx={{ height: 20, fontSize: 11 }} />
                ))}
              </div>
            )}
          </div>
        </div>

        <TextField
          label={t('Action title')}
          fullWidth
          value={title}
          onChange={e => setTitle(e.target.value)}
          variant="standard"
          required
        />

        <Divider />

        {/* Section 2: Target source */}
        <Typography variant="subtitle2">{t('Targets')}</Typography>
        {!isDownstream ? (
          <Typography variant="body2" color="text.secondary">
            {t('From workflow scope (defined in scenario Definition tab)')}
          </Typography>
        ) : (
          <Typography variant="body2" color="text.secondary">
            {t('From upstream action through event')}
          </Typography>
        )}

        <Divider />

        {/* Section 3: Arguments */}
        {visibleFields.length > 0 && (
          <>
            <Typography variant="subtitle2">{t('Arguments')}</Typography>
            {visibleFields.map(field => {
              const binding = getBindingForField(field.key);
              const isBound = binding?.bound === true;

              if (isBound && binding) {
                // Auto-bound argument
                return (
                  <div
                    key={field.key}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: theme.spacing(1.5),
                      padding: theme.spacing(1, 1.5),
                      borderRadius: 4,
                      border: `1px solid ${theme.palette.success.main}40`,
                      background: `${theme.palette.success.main}08`,
                    }}
                  >
                    <LinkOutlined sx={{ fontSize: 16, color: theme.palette.success.main }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <Typography variant="body2" fontWeight={600}>
                          {field.label || field.key}
                        </Typography>
                        <Chip
                          size="small"
                          icon={<FindingIcon findingType={binding.inputType} />}
                          label={formatBinding(binding)}
                          variant="outlined"
                          color="success"
                          sx={{ height: 20, fontSize: 10 }}
                        />
                      </div>
                      {binding.providers.length === 1 ? (
                        <Typography variant="caption" color="text.secondary">
                          {binding.providers[0].providerLabel} → {binding.providers[0].eventLabel}
                        </Typography>
                      ) : (
                        <Tooltip
                          title={
                            <div>
                              {binding.providers.map(p => (
                                <div key={p.providerStepId}>{p.providerLabel} → {p.eventLabel}</div>
                              ))}
                            </div>
                          }
                        >
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, cursor: 'pointer' }}
                          >
                            {binding.providers.length} {t('providers')}
                            <InfoOutlined sx={{ fontSize: 12 }} />
                          </Typography>
                        </Tooltip>
                      )}
                    </div>
                    {/* Scope toggle per argument */}
                    {(() => {
                      const currentScope = fieldScopes[binding.argumentKey] ?? 'LOCAL';
                      const isLocal = currentScope === 'LOCAL';
                      return (
                        <Tooltip title={isLocal ? t('Local: only the triggering instance') : t('Global: all results of this type')}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                            {isLocal
                              ? <PushPinOutlined sx={{ fontSize: 14, color: theme.palette.primary.main }} />
                              : <LanguageOutlined sx={{ fontSize: 14, color: theme.palette.text.secondary }} />
                            }
                            <Switch
                              checked={isLocal}
                              onChange={() => toggleFieldScope(binding.argumentKey)}
                              size="small"
                            />
                            <Typography variant="caption" sx={{ fontSize: 10 }}>
                              {isLocal ? t('Local') : t('Global')}
                            </Typography>
                          </div>
                        </Tooltip>
                      );
                    })()}
                  </div>
                );
              }

              // Manual argument (editable)
              return (
                <div
                  key={field.key}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: theme.spacing(1.5),
                    padding: theme.spacing(1, 1.5),
                    borderRadius: 4,
                    border: `1px solid ${theme.palette.divider}`,
                  }}
                >
                  <div style={{ flex: 1 }}>
                    <TextField
                      label={field.label || field.key}
                      value={(injectContent[field.key] as string) ?? field.defaultValue ?? ''}
                      onChange={e => updateFieldValue(field.key, e.target.value)}
                      size="small"
                      fullWidth
                      variant="standard"
                    />
                  </div>
                  <Typography variant="caption" color="text.disabled" sx={{ fontSize: 10, whiteSpace: 'nowrap' }}>
                    {t('Manual')}
                  </Typography>
                </div>
              );
            })}
          </>
        )}

        <Divider />

        {/* Section 4: Outputs (read-only, scope defined by input bindings) */}
        {outputTypes.length > 0 && (
          <>
            <Typography variant="subtitle2">{t('Outputs')}</Typography>
            <Typography variant="caption" color="text.secondary">
              {t('Data this action provides to downstream events and actions.')}
            </Typography>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {outputTypes.map(type => (
                <Chip
                  key={type}
                  size="small"
                  icon={<FindingIcon findingType={type} />}
                  label={type}
                  variant="outlined"
                  sx={{ height: 24, fontSize: 11 }}
                />
              ))}
            </div>
          </>
        )}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: theme.spacing(1), marginTop: theme.spacing(2) }}>
          <Button variant="contained" onClick={handleClose}>
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="secondary"
            onClick={handleSubmit}
            disabled={!title}
          >
            {t('Save')}
          </Button>
        </div>
      </div>
    </Drawer>
  );
};

export default LogicActionEditDrawer;
