import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useMemo } from 'react';
import { FormProvider, useForm } from 'react-hook-form';

import Transition from '../../../components/common/Transition';
import SelectFieldController from '../../../components/fields/SelectFieldController';
import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import type { ScopeVariableInput } from '../../../utils/api-types';
import useArgumentTypes from '../threat_arsenal/form/useArgumentTypes';

interface ScopeVariableCreateDialogProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: VariableFormValues) => void;
}

type VariableFormValues = Omit<ScopeVariableInput, 'scope_variable_id'>;

const ScopeVariableCreateDialog = ({ open, onClose, onSubmit }: ScopeVariableCreateDialogProps) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const { argumentTypes } = useArgumentTypes();

  const typeItems = useMemo(
    () => argumentTypes.map(at => ({
      value: at.argument_type,
      label: t(at.argument_type.charAt(0).toUpperCase() + at.argument_type.slice(1)),
    })),
    [argumentTypes, t],
  );

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

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleFormSubmit = (data: VariableFormValues) => {
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

    onSubmit({
      ...data,
      scope_variable_key: key,
      scope_variable_value: value,
    });
    handleClose();
  };

  return (
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
        <form id="scopeVariableForm" onSubmit={handleSubmit(handleFormSubmit)}>
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
  );
};

export default ScopeVariableCreateDialog;
