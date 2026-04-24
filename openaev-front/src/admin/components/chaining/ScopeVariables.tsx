import { Add, DeleteOutlined } from '@mui/icons-material';
import {
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Paper,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useMemo, useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';

import Transition from '../../../../../components/common/Transition';
import SelectFieldController from '../../../../../components/fields/SelectFieldController';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import useArgumentTypes from '../../../threat_arsenal/form/useArgumentTypes';
import type {
  ScopeVariableInput,
  ScopeVariableOutput,
  WorkflowConfigurationInput,
  WorkflowConfigurationOutput,
} from '../../../../../utils/api-types';

interface ScopeVariablesProps {
  workflowConfiguration: WorkflowConfigurationOutput | undefined;
  onUpdate: (overrides: Partial<WorkflowConfigurationInput>) => void;
}

type VariableFormValues = Omit<ScopeVariableInput, 'scope_variable_id'>;

const ScopeVariables = ({ workflowConfiguration, onUpdate }: ScopeVariablesProps) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const variables: ScopeVariableOutput[] = workflowConfiguration?.workflow_scope_variables ?? [];

  const { argumentTypes } = useArgumentTypes();

  const typeItems = useMemo(
    () => argumentTypes.map(at => ({
      value: at.argument_type,
      label: t(at.argument_type.charAt(0).toUpperCase() + at.argument_type.slice(1)),
    })),
    [argumentTypes, t],
  );

  // -- Dialog --
  const [open, setOpen] = useState(false);

  const methods = useForm<VariableFormValues>({
    defaultValues: {
      scope_variable_key: '',
      scope_variable_type: 'text',
      scope_variable_value: '',
      scope_variable_description: '',
    },
  });

  const { handleSubmit, reset, formState: { isDirty, isSubmitting } } = methods;

  const handleOpen = () => { reset(); setOpen(true); };
  const handleClose = () => setOpen(false);

  const toInput = (v: ScopeVariableOutput): ScopeVariableInput => ({
    scope_variable_id: v.scope_variable_id,
    scope_variable_key: v.scope_variable_key ?? '',
    scope_variable_type: (v.scope_variable_type ?? 'text') as ScopeVariableInput['scope_variable_type'],
    scope_variable_value: v.scope_variable_value,
    scope_variable_description: v.scope_variable_description,
  });

  const onSubmit = (data: VariableFormValues) => {
    onUpdate({ workflow_scope_variables: [...variables.map(toInput), { ...data }] });
    handleClose();
  };

  const handleDelete = (id: string | undefined) => {
    onUpdate({ workflow_scope_variables: variables.filter(v => v.scope_variable_id !== id).map(toInput) });
  };

  return (
    <div style={{ display: 'grid', gridTemplateRows: 'min-content 1fr', gap: theme.spacing(1) }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h4">{t('Variables')}</Typography>
        <IconButton color="primary" size="small" onClick={handleOpen} aria-label={t('Add variable')}>
          <Add fontSize="small" />
        </IconButton>
      </div>

      {/* List */}
      <Paper variant="outlined" sx={{ p: theme.spacing(2) }}>
        {variables.length > 0 ? (
          <div style={{ display: 'grid', gap: theme.spacing(1) }}>
            {/* Column headers */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr auto', gap: theme.spacing(1) }}>
              {[t('Key'), t('Type'), t('Value')].map(label => (
                <Typography key={label} variant="caption" sx={{ color: 'text.disabled', fontWeight: 600 }}>
                  {label}
                </Typography>
              ))}
              <span />
            </div>

            {/* Rows */}
            {variables.map(variable => (
              <div
                key={variable.scope_variable_id}
                style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr auto', gap: theme.spacing(1), alignItems: 'center' }}
              >
                <Typography variant="body2" sx={{ fontWeight: 600, wordBreak: 'break-all' }}>
                  {variable.scope_variable_key}
                </Typography>
                <Chip
                  label={variable.scope_variable_type ?? '—'}
                  size="small"
                  variant="outlined"
                  sx={{ justifySelf: 'start', fontSize: '0.7rem' }}
                />
                <Typography variant="body2" sx={{ color: 'text.secondary', wordBreak: 'break-all' }}>
                  {variable.scope_variable_value ?? '—'}
                </Typography>
                <IconButton
                  size="small"
                  color="error"
                  onClick={() => handleDelete(variable.scope_variable_id)}
                  aria-label={t('Delete variable')}
                >
                  <DeleteOutlined fontSize="small" />
                </IconButton>
              </div>
            ))}
          </div>
        ) : (
          <Typography variant="body2" sx={{ color: 'text.disabled' }}>
            {t('No variable defined yet.')}
          </Typography>
        )}
      </Paper>

      {/* Create dialog */}
      <Dialog
        open={open}
        slots={{ transition: Transition }}
        onClose={handleClose}
        fullWidth
        maxWidth="sm"
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogTitle>{t('Create a new variable')}</DialogTitle>
        <FormProvider {...methods}>
          <form id="scopeVariableForm" onSubmit={handleSubmit(onSubmit)}>
            <DialogContent style={{ display: 'grid', gap: theme.spacing(2) }}>
              <TextFieldController
                name="scope_variable_key"
                label={t('Key')}
                required
              />
              <SelectFieldController
                name="scope_variable_type"
                label={t('Type')}
                items={typeItems}
                required
              />
              <TextFieldController
                name="scope_variable_value"
                label={t('Value')}
              />
              <TextFieldController
                name="scope_variable_description"
                label={t('Description')}
                multiline
                rows={2}
              />
            </DialogContent>
            <DialogActions>
              <Button variant="contained" onClick={handleClose} disabled={isSubmitting}>
                {t('Cancel')}
              </Button>
              <Button
                variant="contained"
                color="secondary"
                type="submit"
                disabled={!isDirty || isSubmitting}
              >
                {t('Create')}
              </Button>
            </DialogActions>
          </form>
        </FormProvider>
      </Dialog>
    </div>
  );
};

export default ScopeVariables;
