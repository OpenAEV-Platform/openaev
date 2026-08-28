import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { type FunctionComponent, useEffect, useMemo } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import ColorPickerField from '../../../../components/ColorPickerField';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { type ChannelUpdateInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';

interface Props {
  initialValues: ChannelUpdateInput;
  onSubmit: SubmitHandler<ChannelUpdateInput>;
  disabled?: boolean;
  /** Streams the current (possibly unsaved) form values to the live preview. */
  onLiveChange?: (values: Partial<ChannelUpdateInput>) => void;
}

const ChannelParametersForm: FunctionComponent<Props> = ({
  initialValues,
  onSubmit,
  disabled = false,
  onLiveChange,
}) => {
  const { t } = useFormatter();

  const methods = useForm<ChannelUpdateInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<ChannelUpdateInput>().with({
        channel_type: z.string().min(1, { message: t('Should not be empty') }),
        channel_name: z.string().min(1, { message: t('Should not be empty') }),
        channel_description: z.string().min(1, { message: t('Should not be empty') }),
        channel_mode: z.string().optional(),
        channel_primary_color_dark: z.string().optional(),
        channel_primary_color_light: z.string().optional(),
        channel_secondary_color_dark: z.string().optional(),
        channel_secondary_color_light: z.string().optional(),
      }),
    ),
    defaultValues: initialValues,
  });
  const { control, handleSubmit, watch, reset, formState: { isDirty, isSubmitting } } = methods;

  // Feed every keystroke to the live preview so users see the front page
  // change as they type, before saving.
  useEffect(() => {
    const subscription = watch(values => onLiveChange?.(values as Partial<ChannelUpdateInput>));
    return () => subscription.unsubscribe();
  }, [watch, onLiveChange]);

  // If the channel is updated elsewhere (e.g. the header drawer) while this
  // form is pristine, adopt the new values instead of showing stale ones.
  const initialValuesKey = JSON.stringify(initialValues);
  useEffect(() => {
    if (!isDirty) {
      reset(initialValues);
    }
    // Keyed on the serialized values (not the object identity) so the effect
    // does not fire on every parent render.
  }, [initialValuesKey]);

  const typeItems = useMemo(() => [
    {
      value: 'newspaper',
      label: t('newspaper'),
    },
    {
      value: 'microblogging',
      label: t('microblogging'),
    },
    {
      value: 'tv',
      label: t('tv'),
    },
  ], [t]);

  const modeItems = useMemo(() => [
    {
      value: 'title',
      label: t('Title only'),
    },
    {
      value: 'logo',
      label: t('logo'),
    },
    {
      value: 'logo-title',
      label: t('logo-title'),
    },
  ], [t]);

  const submit: SubmitHandler<ChannelUpdateInput> = async (data) => {
    await onSubmit(data);
    // Clear the dirty state so the Update button disables until the next edit.
    reset(data);
  };

  return (
    <FormProvider {...methods}>
      <form id="channelParametersForm" onSubmit={handleSubmit(submit)}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, 1fr)',
          gap: 20,
        }}
        >
          <SelectFieldController
            name="channel_type"
            label={t('Type')}
            items={typeItems}
            required
            disabled={disabled}
          />
          <SelectFieldController
            name="channel_mode"
            label={t('Header mode')}
            items={modeItems}
            disabled={disabled}
          />
          <TextFieldController
            variant="standard"
            name="channel_name"
            label={t('Name')}
            required
            disabled={disabled}
          />
          <TextFieldController
            variant="standard"
            name="channel_description"
            label={t('Subtitle')}
            required
            disabled={disabled}
          />
          <ColorPickerField
            variant="standard"
            control={control}
            name="channel_primary_color_dark"
            label={t('Primary color (dark)')}
            disabled={disabled}
            fullWidth
          />
          <ColorPickerField
            variant="standard"
            control={control}
            name="channel_primary_color_light"
            label={t('Primary color (light)')}
            disabled={disabled}
            fullWidth
          />
          <ColorPickerField
            variant="standard"
            control={control}
            name="channel_secondary_color_dark"
            label={t('Secondary color (dark)')}
            disabled={disabled}
            fullWidth
          />
          <ColorPickerField
            variant="standard"
            control={control}
            name="channel_secondary_color_light"
            label={t('Secondary color (light)')}
            disabled={disabled}
            fullWidth
          />
        </div>
        {!disabled && (
          <div style={{
            display: 'flex',
            justifyContent: 'flex-end',
            marginTop: 20,
          }}
          >
            <Button
              variant="contained"
              color="primary"
              type="submit"
              disabled={!isDirty || isSubmitting}
            >
              {t('Update')}
            </Button>
          </div>
        )}
      </form>
    </FormProvider>
  );
};

export default ChannelParametersForm;
