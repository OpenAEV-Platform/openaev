import { Button, Chip, MenuItem, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import Drawer from '../../../../../components/common/Drawer';
import FindingIcon from '../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../components/i18n';
import type { StepFieldScope, WorkflowStep } from '../../../../../utils/api-types-custom';
import { extractOutputTypesFromStepData, getStepLabel } from './logicUtils';

interface Props {
  open: boolean;
  handleClose: () => void;
  onSubmit: (data: {
    label: string;
    step_action_class: 'INJECT_EXECUTION';
    step_data: string;
    step_output_parser: string;
    step_limit_execution: number;
    step_field_scope: StepFieldScope;
  }) => void;
  editingStep?: WorkflowStep | null;
  isLinkedToEvent?: boolean;
}

const LogicActionForm: FunctionComponent<Props> = ({
  open,
  handleClose,
  onSubmit,
  editingStep,
  isLinkedToEvent = false,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [label, setLabel] = useState('');
  const [limitExecution, setLimitExecution] = useState(1);
  const [fieldScope, setFieldScope] = useState<StepFieldScope>('GLOBAL');
  const [data, setData] = useState('');
  const [outputParser, setOutputParser] = useState('');

  useEffect(() => {
    if (editingStep) {
      setLabel(getStepLabel(editingStep));
      setLimitExecution(editingStep.step_limit_execution);
      setFieldScope(editingStep.step_field_scope);
      setData(editingStep.step_data ?? '');
      setOutputParser(editingStep.step_output_parser ?? '');
    } else {
      setLabel('');
      setLimitExecution(1);
      setFieldScope('GLOBAL');
      setData('');
      setOutputParser('');
    }
  }, [editingStep, open]);

  const outputTypes = editingStep ? extractOutputTypesFromStepData(editingStep) : [];

  const handleSubmit = () => {
    // Embed label in data JSON
    let parsedData: Record<string, unknown> = {};
    try {
      parsedData = data ? JSON.parse(data) : {};
    } catch {
      // keep empty
    }
    parsedData.inject_title = label;

    onSubmit({
      label,
      step_action_class: 'INJECT_EXECUTION',
      step_data: JSON.stringify(parsedData),
      step_output_parser: outputParser,
      step_limit_execution: limitExecution,
      step_field_scope: fieldScope,
    });
    handleClose();
  };

  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={editingStep ? t('Edit action') : t('Create action')}
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
          label={t('Name')}
          fullWidth
          value={label}
          onChange={e => setLabel(e.target.value)}
          variant="standard"
          required
        />

        <TextField
          label={t('Action type')}
          fullWidth
          value="INJECT_EXECUTION"
          disabled
          variant="standard"
        />

        <TextField
          label={t('Execution limit')}
          type="number"
          fullWidth
          value={limitExecution}
          onChange={e => setLimitExecution(parseInt(e.target.value, 10) || 1)}
          variant="standard"
          inputProps={{ min: 1 }}
        />

        <TextField
          label={t('Step data (JSON)')}
          fullWidth
          multiline
          rows={4}
          value={data}
          onChange={e => setData(e.target.value)}
          variant="outlined"
          placeholder='{"injector_contract_id": "...", "inject_title": "..."}'
        />

        <TextField
          label={t('Output parser (JSON)')}
          fullWidth
          multiline
          rows={3}
          value={outputParser}
          onChange={e => setOutputParser(e.target.value)}
          variant="outlined"
          placeholder='[{"contract_output_elements": [{"contract_output_element_type": "port"}]}]'
        />

        {outputTypes.length > 0 && (
          <div>
            <Typography variant="caption" color="text.secondary">
              {t('Provisioned outputs')}
            </Typography>
            <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginTop: 4 }}>
              {outputTypes.map(type => (
                <Chip
                  key={type}
                  size="small"
                  icon={<FindingIcon findingType={type} />}
                  label={type}
                  variant="outlined"
                />
              ))}
            </div>
          </div>
        )}

        {isLinkedToEvent && (
          <div>
            <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
              {t('Field scope')}
            </Typography>
            <ToggleButtonGroup
              value={fieldScope}
              exclusive
              onChange={(_e, val) => { if (val) setFieldScope(val); }}
              size="small"
            >
              <ToggleButton value="GLOBAL">{t('Global')}</ToggleButton>
              <ToggleButton value="LOCAL">{t('Local')}</ToggleButton>
            </ToggleButtonGroup>
          </div>
        )}

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

export default LogicActionForm;
