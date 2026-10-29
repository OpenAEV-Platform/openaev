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
import { FORM_HEX_COLOR_REGEX } from '../../../../utils/Colors';

interface Props {
  defaultValues?: MarkingDefinitionInput;
  isEdit?: boolean;
  onSubmit: SubmitHandler<MarkingDefinitionInput>;
}

const TEXT_MAX_LENGTH = 255;
const ORDER_MAX = 2147483647;

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
      .min(0, { message: t('Order must be greater than or equal to 0') })
      .max(ORDER_MAX, { message: t('Order must be less than or equal to {max}', { max: ORDER_MAX.toString() }) }),
  ) as unknown as z.ZodType<MarkingDefinitionInput['marking_definition_order']>;

  const requiredText = z
    .string()
    .trim()
    .min(1, { message: t('Should not be empty') })
    .max(TEXT_MAX_LENGTH, { message: t('Should not exceed {max_length} characters', { max_length: TEXT_MAX_LENGTH.toString() }) });

  const schema = z.object({
    marking_definition_type: requiredText,
    marking_definition_definition: requiredText,
    marking_definition_color: z
      .string()
      .trim()
      .min(1, { message: t('Should not be empty') })
      .refine(value => FORM_HEX_COLOR_REGEX.test(value), { message: t('Color must be a valid hex value, e.g. #4CAF50') }),
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
    formState: { isSubmitting },
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
          gap: theme.spacing(2),
        }}
      >
        <TextFieldController
          name="marking_definition_type"
          label={t('Type')}
          required
          readOnly={isEdit}
        />
        <TextFieldController
          name="marking_definition_definition"
          label={t('Definition')}
          required
        />
        <ColorPickerField
          label={t('Color')}
          required
          className="w-full mt-4"
          control={control as Control<MarkingDefinitionFormValues>}
          name="marking_definition_color"
        />
        <TextFieldController
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
