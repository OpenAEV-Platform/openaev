import { Add } from '@mui/icons-material';
import { Alert, Button, Divider, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
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
    description: string;
    conditions: ConditionRule[];
    logicOperator: 'AND' | 'OR';
  }) => void;
  editingStep?: WorkflowStep | null;
  allSteps: WorkflowStep[];
  parentActionStepId?: string | null;
}

const LogicEventForm: FunctionComponent<Props> = ({
  open,
  handleClose,
  onSubmit,
  editingStep,
  allSteps,
  parentActionStepId,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [label, setLabel] = useState('');
  const [description, setDescription] = useState('');
  const [logicOperator, setLogicOperator] = useState<'AND' | 'OR'>('AND');
  const [conditions, setConditions] = useState<ConditionRule[]>([]);

  useEffect(() => {
    if (editingStep) {
      setLabel(getStepLabel(editingStep));
      try {
        const data = JSON.parse(editingStep.step_data ?? '{}');
        setDescription(data.event_description ?? '');
      } catch {
        setDescription('');
      }
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
      setDescription('');
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
      description,
      conditions,
      logicOperator,
    });
    handleClose();
  };

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
          label={t('Description')}
          fullWidth
          value={description}
          onChange={e => setDescription(e.target.value)}
          variant="standard"
          multiline
          rows={2}
        />

        <Divider />

        {parentActionStepId && (
          <Alert severity="info" sx={{ mb: 1 }}>
            {t('Conditions below evaluate the output of the parent action.')}
          </Alert>
        )}

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="subtitle2">
            {t('Trigger conditions')}
            <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
              ({t('at least one required')})
            </Typography>
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
            index={index}
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
            disabled={!label || (conditions.length === 0 && !editingStep)}
          >
            {editingStep ? t('Update') : t('Create')}
          </Button>
        </div>
      </div>
    </Drawer>
  );
};

export default LogicEventForm;
