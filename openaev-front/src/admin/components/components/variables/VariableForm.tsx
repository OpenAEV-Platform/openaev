import { zodResolver } from '@hookform/resolvers/zod';
import { Button, TextField as MuiTextField } from '@mui/material';
import { type FunctionComponent } from 'react';
import { type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import { useFormatter } from '../../../../components/i18n';
import { type VariableInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';

interface Props {
  onSubmit: SubmitHandler<VariableInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: VariableInput;
}

const VariableForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing,
  initialValues = {
    variable_key: '',
    variable_description: '',
    variable_value: '',
  },
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<VariableInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<VariableInput>().with({
        variable_key: z
          .string()
          .regex(
            /^[a-z_]+$/,
            t(
              'Invalid input. Please use only letters or underscores, and ensure the field is not empty.',
            ),
          ),
        variable_description: z.string().optional(),
        variable_value: z.string().optional(),
      }),
    ),
    defaultValues: initialValues,
  });

  return (
    <form id="variableForm" onSubmit={handleSubmit(onSubmit)}>
      <MuiTextField
        variant="standard"
        fullWidth
        label={t('Key')}
        error={!!errors.variable_key}
        helperText={errors.variable_key && errors.variable_key?.message}
        inputProps={register('variable_key')}
      />
      <MuiTextField
        variant="standard"
        fullWidth
        label={t('Value')}
        style={{ marginTop: 20 }}
        error={!!errors.variable_value}
        helperText={errors.variable_value && errors.variable_value?.message}
        inputProps={register('variable_value')}
      />
      <MuiTextField
        variant="standard"
        fullWidth
        multiline
        rows={2}
        label={t('Description')}
        style={{ marginTop: 20 }}
        error={!!errors.variable_description}
        helperText={
          errors.variable_description && errors.variable_description?.message
        }
        inputProps={register('variable_description')}
      />
      <div style={{
        float: 'right',
        marginTop: 20,
      }}
      >
        <Button
          variant="contained"
          onClick={handleClose}
          style={{ marginRight: 10 }}
          disabled={isSubmitting}
        >
          {t('Cancel')}
        </Button>
        <Button
          variant="contained"
          color="secondary"
          type="submit"
          disabled={!isDirty || isSubmitting}
        >
          {editing ? t('Update') : t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default VariableForm;
