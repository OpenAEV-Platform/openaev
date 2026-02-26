import { Add } from '@mui/icons-material';
import { Button, Divider, MenuItem, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import type { ConditionType, WorkflowStep } from '../../../../../utils/api-types-custom';
import LogicConditionRuleRow, { type ConditionRule } from './LogicConditionRuleRow';
import { getStepLabel } from './logicUtils';

interface Props {
  open: boolean;
  handleClose: () => void;
  onSubmit: (data: {
    label: string;
    targetStepId: string;
    conditions: ConditionRule[];
    logicOperator: 'AND' | 'OR';
  }) => void;
  editingStep?: WorkflowStep | null;
  allSteps: WorkflowStep[];
}

const LogicEventForm: FunctionComponent<Props> = ({
  open,
  handleClose,
  onSubmit,
  editingStep,
  allSteps,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [label, setLabel] = useState('');
  const [targetStepId, setTargetStepId] = useState('');
  const [logicOperator, setLogicOperator] = useState<'AND' | 'OR'>('AND');
  const [conditions, setConditions] = useState<ConditionRule[]>([]);

  useEffect(() => {
    if (editingStep) {
      setLabel(getStepLabel(editingStep));
      // Try to extract target step from conditions
      const firstCondition = editingStep.step_conditions[0];
      setTargetStepId(firstCondition?.step_from_id ?? '');
      setConditions(
        editingStep.step_conditions
          .filter(c => c.condition_key)
          .map(c => ({
            key: c.condition_key ?? '',
            operator: c.condition_type as ConditionType,
            value: c.condition_value ?? '',
            caseSensitive: false,
          })),
      );
    } else {
      setLabel('');
      setTargetStepId('');
      setLogicOperator('AND');
      setConditions([]);
    }
  }, [editingStep, open]);

  const addCondition = () => {
    setConditions(prev => [...prev, { key: '', operator: 'EQ', value: '', caseSensitive: false }]);
  };

  const updateCondition = (index: number, rule: ConditionRule) => {
    setConditions(prev => prev.map((r, i) => (i === index ? rule : r)));
  };

  const removeCondition = (index: number) => {
    setConditions(prev => prev.filter((_, i) => i !== index));
  };

  const handleSubmit = () => {
    onSubmit({
      label,
      targetStepId,
      conditions,
      logicOperator,
    });
    handleClose();
  };

  // Available steps for the "target action" dropdown (steps without conditions = root actions)
  const availableTargetSteps = allSteps.filter(s => s.step_conditions.length === 0);

  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={editingStep ? t('Edit event') : t('Create event')}
    >
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
          padding: theme.spacing(2),
        }}
      >
        <TextField
          label={t('Event name')}
          fullWidth
          value={label}
          onChange={e => setLabel(e.target.value)}
          variant="standard"
          required
        />

        <TextField
          select
          label={t('Target action (step from)')}
          fullWidth
          value={targetStepId}
          onChange={e => setTargetStepId(e.target.value)}
          variant="standard"
        >
          <MenuItem value="">
            <em>{t('None')}</em>
          </MenuItem>
          {availableTargetSteps.map(step => (
            <MenuItem key={step.step_id} value={step.step_id}>
              {getStepLabel(step)}
            </MenuItem>
          ))}
        </TextField>

        <Divider />

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="subtitle2">
            {t('Trigger conditions')}
          </Typography>
          <ToggleButtonGroup
            value={logicOperator}
            exclusive
            onChange={(_e, val) => { if (val) setLogicOperator(val); }}
            size="small"
          >
            <ToggleButton value="AND">{t('AND')}</ToggleButton>
            <ToggleButton value="OR">{t('OR')}</ToggleButton>
          </ToggleButtonGroup>
        </div>

        {conditions.map((rule, index) => (
          <LogicConditionRuleRow
            key={index}
            rule={rule}
            onChange={updated => updateCondition(index, updated)}
            onDelete={() => removeCondition(index)}
            allSteps={allSteps}
          />
        ))}

        <Button
          variant="outlined"
          size="small"
          startIcon={<Add />}
          onClick={addCondition}
        >
          {t('Add condition')}
        </Button>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: theme.spacing(1), marginTop: theme.spacing(2) }}>
          <Button variant="contained" onClick={handleClose}>
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="secondary"
            onClick={handleSubmit}
            disabled={!label}
          >
            {editingStep ? t('Update') : t('Create')}
          </Button>
        </div>
      </div>
    </Drawer>
  );
};

export default LogicEventForm;
