import { zodResolver } from '@hookform/resolvers/zod';
import { TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import ActionButtons from '../../../../components/common/ActionButtons';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { type MarkingDefinitionInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';

// Mirrors the @Pattern on MarkingDefinitionInput.marking_color so an invalid colour is caught
// before the round trip; the backend stays authoritative.
const HEX_COLOR_REGEX = /^#[0-9a-f]{6}$/i;

interface Props {
  onSubmit: SubmitHandler<MarkingDefinitionInput>;
  onCancel: () => void;
  editing?: boolean;
  initialValues?: MarkingDefinitionInput;
}

const MarkingDefinitionForm: FunctionComponent<Props> = ({
  onSubmit,
  onCancel,
  editing,
  initialValues = {
    marking_type: 'TLP',
    marking_name: '',
    marking_order: 10,
    marking_color: '#2e7d32',
  },
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const schema = useMemo(
    () =>
      zodImplement<MarkingDefinitionInput>().with({
        marking_type: z.string().min(1, { message: t('Should not be empty') }),
        marking_name: z.string().min(1, { message: t('Should not be empty') }),
        marking_order: z.number().int().positive({ message: t('Should be a positive number') }),
        marking_color: z.string().regex(HEX_COLOR_REGEX, { message: t('Should be a hex colour, e.g. #c62828') }).optional(),
      }),
    [t],
  );

  const methods = useForm<MarkingDefinitionInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: initialValues,
  });

  const {
    formState: {
      errors,
      isDirty,
      isSubmitting,
    },
    handleSubmit,
    register,
  } = methods;

  return (
    <FormProvider {...methods}>
      <form
        id="markingDefinitionFormId"
        onSubmit={handleSubmit(onSubmit)}
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
      >
        <TextFieldController
          name="marking_type"
          label={t('Type')}
          required
        />
        <TextFieldController
          name="marking_name"
          label={t('Name')}
          required
        />
        {/* Plain TextField rather than TextFieldController: valueAsNumber is what keeps the
            registered value a number, so the schema can stay z.number() and match the API type. */}
        <TextField
          variant="standard"
          fullWidth
          label={`${t('Order')}*`}
          type="number"
          error={!!errors.marking_order}
          helperText={errors.marking_order?.message}
          slotProps={{
            htmlInput: {
              ...register('marking_order', { valueAsNumber: true }),
              min: 1,
            },
          }}
        />
        <TextFieldController
          name="marking_color"
          label={t('Color')}
        />
        <div style={{ alignSelf: 'flex-end' }}>
          <ActionButtons
            onCancel={onCancel}
            cancelLabel={t('Cancel')}
            submitLabel={editing ? t('Update') : t('Create')}
            disabled={!isDirty}
            submitting={isSubmitting}
          />
        </div>
      </form>
    </FormProvider>
  );
};

export default MarkingDefinitionForm;
