import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useEffect } from 'react';
import { type Control, FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import ColorPickerField from '../../../../components/ColorPickerField';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { type MarkingDefinitionInput } from '../../../../utils/api-types';

interface Props {
  defaultValues?: MarkingDefinitionInput;
  isEdit?: boolean;
  onSubmit: SubmitHandler<MarkingDefinitionInput>;
}

const HEX_COLOR_REGEX = /^#([0-9a-fA-F]{6})$/;

const DEFAULT_VALUES: MarkingDefinitionInput = {
  marking_definition_type: '',
  marking_definition_definition: '',
  marking_definition_color: '',
  marking_definition_order: 0,
};

const MarkingDefinitionForm: FunctionComponent<Props> = ({
  defaultValues,
  isEdit = false,
  onSubmit,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const formDefaultValues = defaultValues ?? DEFAULT_VALUES;

  const orderNumber = z.preprocess(
    value => (value === '' || value === null ? undefined : value),
    z
      .coerce
      .number({ message: t('Should not be empty') })
      .int({ message: t('Order must be an integer') })
      .min(0, { message: t('Order must be greater than or equal to 0') }),
  ) as unknown as z.ZodType<MarkingDefinitionInput['marking_definition_order']>;

  const schema = z.object({
    marking_definition_type: z.string().min(1, { message: t('Should not be empty') }),
    marking_definition_definition: z.string().min(1, { message: t('Should not be empty') }),
    marking_definition_color: z
      .string()
      .trim()
      .min(1, { message: t('Should not be empty') })
      .refine(value => HEX_COLOR_REGEX.test(value), { message: t('Color must be a valid hex value, e.g. #4CAF50') }),
    marking_definition_order: orderNumber,
  });

  type MarkingDefinitionFormValues = z.input<typeof schema>;

  const methods = useForm<MarkingDefinitionFormValues, unknown, MarkingDefinitionInput>({
    mode: 'onChange',
    resolver: zodResolver(schema),
    defaultValues: formDefaultValues,
  });

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = methods;

  useEffect(() => {
    reset(formDefaultValues);
  }, [formDefaultValues, reset]);

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(data => onSubmit(data))(e);
  };

  return (
    <FormProvider {...methods}>
      <form
        id="markingDefinitionForm"
        onSubmit={handleSubmitWithoutPropagation}
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
      >
        <TextFieldController
          variant="standard"
          name="marking_definition_type"
          label={t('Type')}
          required
          readOnly={isEdit}
        />
        <TextFieldController
          variant="standard"
          name="marking_definition_definition"
          label={t('Definition')}
          required
        />
        <ColorPickerField
          variant="standard"
          fullWidth
          label={t('Color')}
          required
          error={!!errors.marking_definition_color}
          helperText={errors.marking_definition_color?.message}
          sx={{ marginTop: 2 }}
          slotProps={{ htmlInput: { readOnly: true } }}
          control={control as Control<MarkingDefinitionFormValues>}
          name="marking_definition_color"
        />
        <TextFieldController
          variant="standard"
          name="marking_definition_order"
          label={t('Order')}
          type="number"
          required
          min={0}
          step={1}
          style={{ marginTop: 16 }}
        />
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            marginTop: 20,
          }}
        >
          <Button variant="contained" color="primary" type="submit" disabled={isSubmitting}>
            {isEdit ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default MarkingDefinitionForm;
