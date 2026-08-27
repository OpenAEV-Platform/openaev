import { zodResolver } from '@hookform/resolvers/zod';
import { Button, TextField } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import ColorPickerField from '../../../../components/ColorPickerField';
import { useFormatter } from '../../../../components/i18n';
import { type MarkingDefinitionInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';

interface Props {
  defaultValues?: MarkingDefinitionInput;
  isEdit?: boolean;
  onSubmit: SubmitHandler<MarkingDefinitionInput>;
}

const HEX_COLOR_REGEX = /^#([0-9a-fA-F]{6})$/;

const MarkingDefinitionForm: FunctionComponent<Props> = ({
  defaultValues,
  isEdit = false,
  onSubmit,
}) => {
  const { t } = useFormatter();

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<MarkingDefinitionInput>({
    mode: 'onChange',
    resolver: zodResolver(
      zodImplement<MarkingDefinitionInput>().with({
        marking_definition_type: z.string().min(1, { message: t('Should not be empty') }),
        marking_definition_definition: z.string().min(1, { message: t('Should not be empty') }),
        marking_definition_color: z
          .string()
          .optional()
          .refine(value => !value || HEX_COLOR_REGEX.test(value), { message: t('Color must be a valid hex value, e.g. #4CAF50') }),
        marking_definition_order: z
          .number({ message: t('Should not be empty') })
          .int({ message: t('Order must be an integer') })
          .min(0, { message: t('Order must be greater than or equal to 0') }),
      }),
    ),
    defaultValues: defaultValues ?? {
      marking_definition_type: '',
      marking_definition_definition: '',
      marking_definition_color: '',
      marking_definition_order: 0,
    },
  });

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  return (
    <form id="markingDefinitionForm" onSubmit={handleSubmitWithoutPropagation}>
      <TextField
        variant="standard"
        fullWidth
        label={t('Type')}
        error={!!errors.marking_definition_type}
        helperText={errors.marking_definition_type?.message}
        disabled={isEdit}
        {...register('marking_definition_type')}
      />
      <TextField
        variant="standard"
        fullWidth
        label={t('Definition')}
        error={!!errors.marking_definition_definition}
        helperText={errors.marking_definition_definition?.message}
        {...register('marking_definition_definition')}
      />
      <ColorPickerField
        variant="standard"
        fullWidth
        label={t('Color')}
        error={!!errors.marking_definition_color}
        helperText={errors.marking_definition_color?.message}
        sx={{ marginTop: 2 }}
        slotProps={{ htmlInput: { readOnly: true } }}
        control={control}
        name="marking_definition_color"
      />
      <TextField
        variant="standard"
        fullWidth
        label={t('Order')}
        type="number"
        error={!!errors.marking_definition_order}
        helperText={errors.marking_definition_order?.message}
        slotProps={{
          htmlInput: {
            min: 0,
            step: 1,
          },
        }}
        sx={{ marginTop: 2 }}
        {...register('marking_definition_order', { valueAsNumber: true })}
      />
      <div style={{
        float: 'right',
        marginTop: 20,
      }}
      >
        <Button variant="contained" color="primary" type="submit" disabled={isSubmitting}>
          {isEdit ? t('Update') : t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default MarkingDefinitionForm;
