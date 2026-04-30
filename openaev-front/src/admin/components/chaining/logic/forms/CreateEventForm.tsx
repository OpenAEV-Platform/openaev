import { AddOutlined, DeleteOutline } from '@mui/icons-material';
import { Button, Divider, FormControl, IconButton, InputLabel, MenuItem, Select, TextField, Typography } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';

export interface ConditionRow {
  condition_type: string;
  condition_key_type: string;
  condition_value: string;
}

export interface CreateEventData {
  event_name: string;
  event_description: string;
  root_logical_type: string;
  conditions: ConditionRow[];
}

interface Props {
  onSubmit: (data: CreateEventData) => void;
  handleClose: () => void;
}

const emptyCondition = (): ConditionRow => ({
  condition_type: 'EQ',
  condition_key_type: 'status',
  condition_value: 'SUCCESS',
});

const CreateEventForm: FunctionComponent<Props> = ({ onSubmit, handleClose }) => {
  const { t } = useFormatter();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [rootLogicalType, setRootLogicalType] = useState<string>('AND');
  const [conditions, setConditions] = useState<ConditionRow[]>([emptyCondition()]);

  const updateCondition = (index: number, field: keyof ConditionRow, value: string) => {
    setConditions(prev => prev.map((c, i) => (i === index
      ? {
          ...c,
          [field]: value,
        }
      : c)));
  };

  const addCondition = () => setConditions(prev => [...prev, emptyCondition()]);

  const removeCondition = (index: number) => {
    setConditions(prev => prev.filter((_, i) => i !== index));
  };

  const handleSubmit = () => {
    if (!name.trim() || conditions.length === 0) return;
    onSubmit({
      event_name: name.trim(),
      event_description: description.trim(),
      root_logical_type: rootLogicalType,
      conditions,
    });
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      <TextField
        label={t('Name (event_name)')}
        fullWidth
        required
        value={name}
        onChange={e => setName(e.target.value)}
        autoFocus
      />
      <TextField
        label={t('Description (event_description)')}
        fullWidth
        multiline
        minRows={2}
        value={description}
        onChange={e => setDescription(e.target.value)}
      />

      <Divider />
      <Typography variant="subtitle2">{t('Root logical operator')}</Typography>
      <FormControl fullWidth>
        <InputLabel>{t('Logical type (root)')}</InputLabel>
        <Select
          label={t('Logical type (root)')}
          value={rootLogicalType}
          onChange={e => setRootLogicalType(e.target.value)}
        >
          <MenuItem value="AND">{t('AND')}</MenuItem>
          <MenuItem value="OR">{t('OR')}</MenuItem>
        </Select>
      </FormControl>

      <Divider />
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
      >
        <Typography variant="subtitle2">{t('Conditions')}</Typography>
        <IconButton size="small" color="primary" onClick={addCondition}>
          <AddOutlined fontSize="small" />
        </IconButton>
      </div>

      {conditions.map((cond, idx) => (
        <div
          key={idx}
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 12,
            padding: 12,
            border: '1px solid #ccc',
            borderRadius: 4,
            position: 'relative',
          }}
        >
          {conditions.length > 1 && (
            <IconButton
              size="small"
              onClick={() => removeCondition(idx)}
              sx={{
                position: 'absolute',
                top: 4,
                right: 4,
              }}
            >
              <DeleteOutline fontSize="small" />
            </IconButton>
          )}
          <FormControl fullWidth size="small">
            <InputLabel>{t('Type (condition_type)')}</InputLabel>
            <Select
              label={t('Type (condition_type)')}
              value={cond.condition_type}
              onChange={e => updateCondition(idx, 'condition_type', e.target.value)}
            >
              <MenuItem value="EQ">{t('EQ')}</MenuItem>
              <MenuItem value="NEQ">{t('NEQ')}</MenuItem>
              <MenuItem value="IS_NULL">{t('IS_NULL')}</MenuItem>
              <MenuItem value="IS_NOT_NULL">{t('IS_NOT_NULL')}</MenuItem>
              <MenuItem value="GT">{t('GT')}</MenuItem>
              <MenuItem value="GTE">{t('GTE')}</MenuItem>
              <MenuItem value="LT">{t('LT')}</MenuItem>
              <MenuItem value="LTE">{t('LTE')}</MenuItem>
              <MenuItem value="IN">{t('IN')}</MenuItem>
              <MenuItem value="NIN">{t('NIN')}</MenuItem>
              <MenuItem value="AFTER">{t('AFTER')}</MenuItem>
              <MenuItem value="BEFORE">{t('BEFORE')}</MenuItem>
              <MenuItem value="DEPEND_ON">{t('DEPEND_ON')}</MenuItem>
            </Select>
          </FormControl>
          <FormControl fullWidth size="small">
            <InputLabel>{t('Key type (condition_key_type)')}</InputLabel>
            <Select
              label={t('Key type (condition_key_type)')}
              value={cond.condition_key_type}
              onChange={e => updateCondition(idx, 'condition_key_type', e.target.value)}
            >
              <MenuItem value="status">{t('status')}</MenuItem>
              <MenuItem value="text">{t('text')}</MenuItem>
              <MenuItem value="number">{t('number')}</MenuItem>
              <MenuItem value="execution_time">{t('execution_time')}</MenuItem>
              <MenuItem value="step_template_id">{t('step_template_id')}</MenuItem>
            </Select>
          </FormControl>
          <TextField
            label={t('Value (condition_value)')}
            fullWidth
            size="small"
            value={cond.condition_value}
            onChange={e => updateCondition(idx, 'condition_value', e.target.value)}
          />
        </div>
      ))}

      <Button
        variant="contained"
        color="secondary"
        onClick={handleSubmit}
        disabled={!name.trim() || conditions.length === 0}
      >
        {t('Create')}
      </Button>
      <Button variant="outlined" color="primary" onClick={handleClose}>
        {t('Cancel')}
      </Button>
    </div>
  );
};

export default CreateEventForm;
