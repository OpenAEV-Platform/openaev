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
  Tooltip,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useMemo, useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { ScopeVariableInput, ScopeVariableOutput, WorkflowConfigurationInput, WorkflowConfigurationOutput } from '../../../utils/api-types';
import { useFormatter } from '../../../components/i18n';
import useArgumentTypes from '../threat_arsenal/form/useArgumentTypes';
import { Transition } from 'mdi-material-ui';
import TextFieldController from '../../../components/fields/TextFieldController';
import SelectFieldController from '../../../components/fields/SelectFieldController';

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

  const {
    handleSubmit,
    reset,
    setError,
    formState: { isDirty, isSubmitting },
  } = methods;

  const handleOpen = () => {
    reset();
    setOpen(true);
  };
  const handleClose = () => setOpen(false);

  const toInput = (v: ScopeVariableOutput): ScopeVariableInput => ({
    scope_variable_id: v.scope_variable_id,
    scope_variable_key: v.scope_variable_key ?? '',
    scope_variable_type: v.scope_variable_type ?? 'text',
    scope_variable_value: v.scope_variable_value ?? '',
    scope_variable_description: v.scope_variable_description,
  });

  const onSubmit = (data: VariableFormValues) => {
    const key = data.scope_variable_key?.trim() ?? '';
    const value = data.scope_variable_value?.trim() ?? '';
    const type = data.scope_variable_type;

    if (!key) {
      setError('scope_variable_key', {
        type: 'required',
        message: t('Key is required'),
      });
      return;
    }
    if (!type) {
      setError('scope_variable_type', {
        type: 'required',
        message: t('Type is required'),
      });
      return;
    }
    if (!value) {
      setError('scope_variable_value', {
        type: 'required',
        message: t('Value is required'),
      });
      return;
    }

    onUpdate({
      workflow_scope_variables: [
        ...variables.map(toInput),
        {
          ...data,
          scope_variable_key: key,
          scope_variable_value: value,
        },
      ],
    });
    handleClose();
  };

  const handleDelete = (id: string | undefined) => {
    onUpdate({ workflow_scope_variables: variables.filter(v => v.scope_variable_id !== id).map(toInput) });
  };

  return (
    <div style={{
      display: 'grid',
      gridTemplateRows: 'min-content 1fr',
      gap: theme.spacing(1),
    }}
    >
      {/* Header */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
      >
        <Typography variant="h4">{t('Variables')}</Typography>
        <IconButton color="primary" size="small" onClick={handleOpen} aria-label={t('Add variable')}>
          <Add fontSize="small" />
        </IconButton>
      </div>

      {/* List */}
      <Paper variant="outlined" sx={{ p: theme.spacing(2) }}>
        {variables.length > 0 ? (
          <div style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr 1fr 1fr auto',
            gap: theme.spacing(1),
            alignItems: 'center',
          }}
          >
            {/* Column headers */}
            {[t('Key'), t('Type'), t('Value'), t('Description')].map(label => (
              <Typography
                key={label}
                variant="caption"
                sx={{
                  color: 'text.disabled',
                  fontWeight: 600,
                  textAlign: 'left',
                }}
              >
                {label}
              </Typography>
            ))}
            <span />

            {/* Rows */}
            {variables.map(variable => (
              <>
                <Typography
                  key={`key-${variable.scope_variable_id}`}
                  variant="body2"
                  sx={{
                    fontWeight: 600,
                    wordBreak: 'break-all',
                  }}
                >
                  {variable.scope_variable_key}
                </Typography>
                <Chip
                  key={`type-${variable.scope_variable_id}`}
                  label={variable.scope_variable_type ?? '—'}
                  size="small"
                  variant="outlined"
                  sx={{
                    justifySelf: 'start',
                    fontSize: '0.7rem',
                  }}
                />
                <Typography
                  key={`value-${variable.scope_variable_id}`}
                  variant="body2"
                  sx={{
                    color: 'text.secondary',
                    wordBreak: 'break-all',
                  }}
                >
                  {variable.scope_variable_value ?? '—'}
                </Typography>
                <Typography
                  key={`desc-${variable.scope_variable_id}`}
                  variant="body2"
                  sx={{
                    color: 'text.secondary',
                    fontStyle: variable.scope_variable_description ? 'normal' : 'italic',
                    wordBreak: 'break-all',
                  }}
                >
                  {variable.scope_variable_description ?? '—'}
                </Typography>
                <Tooltip key={`del-${variable.scope_variable_id}`} title={t('Delete variable')}>
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => handleDelete(variable.scope_variable_id)}
                    aria-label={t('Delete variable')}
                  >
                    <DeleteOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              </>
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
            <DialogContent style={{
              display: 'grid',
              gap: theme.spacing(2),
            }}
            >
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
                required
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
