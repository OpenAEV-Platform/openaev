import { LanguageOutlined, PlayArrowOutlined, PushPinOutlined } from '@mui/icons-material';
import { Button, Chip, Divider, Switch, TextField, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import Drawer from '../../../../../components/common/Drawer';
import FindingIcon from '../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../components/i18n';
import type { WorkflowStep } from '../../../../../utils/api-types-custom';
import InjectIcon from '../../../common/injects/InjectIcon';
import {
  extractOutputTypesFromStepData,
  getFieldScopes,
  getStepAttackPatterns,
  getStepInjectorType,
  getStepLabel,
  hasDependOnCondition,
} from './logicUtils';

interface Props {
  open: boolean;
  handleClose: () => void;
  step: WorkflowStep | null;
  onSave: (step: WorkflowStep, title: string, fieldScopes: Record<string, string>) => void;
  attackPatternsMap: Record<string, { attack_pattern_external_id?: string }>;
}

const LogicActionEditDrawer: FunctionComponent<Props> = ({
  open,
  handleClose,
  step,
  onSave,
  attackPatternsMap,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [title, setTitle] = useState('');
  const [fieldScopes, setFieldScopes] = useState<Record<string, string>>({});

  useEffect(() => {
    if (step) {
      setTitle(getStepLabel(step));
      setFieldScopes(getFieldScopes(step));
    }
  }, [step, open]);

  if (!step) return null;

  const injectorType = getStepInjectorType(step);
  const outputTypes = extractOutputTypesFromStepData(step);
  const showFieldScopes = hasDependOnCondition(step);
  const attackPatternIds = getStepAttackPatterns(step);
  const attackPatternExternalIds = attackPatternIds
    .map(id => attackPatternsMap[id]?.attack_pattern_external_id)
    .filter((eid): eid is string => !!eid);

  const toggleFieldScope = (fieldType: string) => {
    setFieldScopes(prev => ({
      ...prev,
      [fieldType]: (prev[fieldType] ?? 'GLOBAL') === 'GLOBAL' ? 'LOCAL' : 'GLOBAL',
    }));
  };

  const handleSubmit = () => {
    onSave(step, title, fieldScopes);
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
        {/* Injector info */}
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

        {/* Input fields scope - only for actions linked to an event */}
        {showFieldScopes && outputTypes.length > 0 && (
          <>
            <Typography variant="subtitle2">
              {t('Input fields scope')}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {t('Local: only matching results from the triggering event. Global: all results.')}
            </Typography>

            {outputTypes.map(type => {
              const isLocal = (fieldScopes[type] ?? 'GLOBAL') === 'LOCAL';
              return (
                <div
                  key={type}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: theme.spacing(1.5),
                    padding: theme.spacing(1, 1.5),
                    borderRadius: 4,
                    border: `1px solid ${theme.palette.divider}`,
                    background: isLocal
                      ? `${theme.palette.primary.main}08`
                      : 'transparent',
                  }}
                >
                  <FindingIcon findingType={type} />
                  <Typography variant="body2" sx={{ flex: 1 }}>
                    {type}
                  </Typography>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <LanguageOutlined
                      sx={{
                        fontSize: 16,
                        color: !isLocal ? theme.palette.text.primary : theme.palette.text.disabled,
                      }}
                    />
                    <Typography
                      variant="caption"
                      sx={{
                        fontWeight: !isLocal ? 'bold' : 'normal',
                        color: !isLocal ? theme.palette.text.primary : theme.palette.text.disabled,
                      }}
                    >
                      {t('Global')}
                    </Typography>
                    <Switch
                      checked={isLocal}
                      onChange={() => toggleFieldScope(type)}
                      size="small"
                      color="primary"
                    />
                    <PushPinOutlined
                      sx={{
                        fontSize: 16,
                        color: isLocal ? theme.palette.primary.main : theme.palette.text.disabled,
                      }}
                    />
                    <Typography
                      variant="caption"
                      sx={{
                        fontWeight: isLocal ? 'bold' : 'normal',
                        color: isLocal ? theme.palette.primary.main : theme.palette.text.disabled,
                      }}
                    >
                      {t('Local')}
                    </Typography>
                  </div>
                </div>
              );
            })}
          </>
        )}

        {!showFieldScopes && outputTypes.length > 0 && (
          <>
            <Typography variant="subtitle2">
              {t('Output fields')}
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

        {outputTypes.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            {t('No output fields configured for this action.')}
          </Typography>
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
