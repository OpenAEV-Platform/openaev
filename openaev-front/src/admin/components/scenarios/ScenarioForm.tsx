import { Button, Checkbox, Combobox, ComboboxChips, ComboboxField, ComboboxHelperText, ComboboxInput, ComboboxLabel } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';

import type { LoggedHelper } from '../../../actions/helper';
import TagField from '../../../components/fields/TagField';
import TextFieldFds from '../../../components/fields/TextFieldFds';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type PlatformSettings, type ScenarioInput } from '../../../utils/api-types';
import { zodImplement } from '../../../utils/Zod';
import LessonsLearnedSection from '../common/form/LessonsLearnedSection';
import ScenarioGeneralFields from './ScenarioGeneralFields';

// The lessons learned module toggle rides along the configuration form but is
// persisted through the dedicated PUT /scenarios/{id}/lessons endpoint (see
// ScenarioUpdate), so the general update input stays untouched.
export type ScenarioFormInput = ScenarioInput & { scenario_lessons_enabled?: boolean };

interface Props {
  onSubmit: (data: ScenarioFormInput, isScenarioAssistantChecked?: boolean) => void;
  handleClose: () => void;
  editing?: boolean;
  disabled?: boolean;
  initialValues: ScenarioFormInput;
  isCreation?: boolean;
  isChaining?: boolean;
}

const ScenarioForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing,
  initialValues,
  disabled,
  isCreation = false,
  isChaining = false,
}) => {
  // Standard hooks
  const theme = useTheme();
  const { t } = useFormatter();
  const [inputValue, setInputValue] = useState('');
  const [isScenarioAssistantChecked, setIsScenarioAssistantChecked] = useState(false);
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  const form = useForm<ScenarioFormInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<ScenarioFormInput>().with({
        scenario_name: z.string().min(1, { message: t('Should not be empty') }),
        scenario_category: z.string().optional().nullable(),
        scenario_main_focus: z.string().optional().nullable(),
        scenario_severity: z.enum(['low', 'medium', 'high', 'critical']).optional(),
        scenario_default_kill_chain: z.string().optional().nullable(),
        scenario_subtitle: z.string().optional(),
        scenario_description: z.string().optional(),
        scenario_tags: z.string().array().optional(),
        scenario_external_reference: z.string().optional().nullable(),
        scenario_external_url: z.string().optional().nullable(),
        scenario_mail_from_name: z.string().max(100, t('Should not exceed {max_length} characters', { max_length: '100' })).optional(),
        scenario_mails_reply_to: z.array(z.string().email(t('Should be a valid email address'))).optional(),
        scenario_message_header: z.string().optional(),
        scenario_message_footer: z.string().optional(),
        scenario_custom_dashboard: z.string().optional(),
        scenario_is_chaining: z.boolean().optional(),
        scenario_lessons_enabled: z.boolean().optional(),
      }),
    ),
    defaultValues: initialValues,
  });
  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isDirty, isSubmitting },
  } = form;

  return (
    <form
      noValidate
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
        marginTop: theme.spacing(2),
      }}
      id="scenarioForm"
      onSubmit={handleSubmit((data: ScenarioFormInput) => onSubmit(data, isScenarioAssistantChecked))}
    >
      {/* Same sectioned layout as the simulation form (ExerciseForm):
            General, Modules (edit only), Emails and SMS. */}
      <ScenarioGeneralFields form={form} initialValues={initialValues} />
      <Controller
        control={control}
        name="scenario_tags"
        render={({ field: { onChange, value }, fieldState: { error } }) => (
          <TagField
            label={t('Tags')}
            fieldValue={value ?? []}
            fieldOnChange={onChange}
            error={error}
          />
        )}
      />
      {isCreation && (
        <Checkbox
          checked={isScenarioAssistantChecked}
          onCheckedChange={() => setIsScenarioAssistantChecked(!isScenarioAssistantChecked)}
          label={t('Use the scenario assistant')}
        />
      )}
      <LessonsLearnedSection
        control={control}
        name="scenario_lessons_enabled"
        disabled={disabled}
        style={{ marginTop: theme.spacing(2) }}
      />
      {!isChaining && (
        <>
          <Typography variant="h2" style={{ marginTop: theme.spacing(2) }}>
            {t('Emails and SMS')}
          </Typography>
          <TextFieldFds
            label={t('Sender email address')}
            value={settings.default_mailer ?? ''}
            disabled
          />
          <TextFieldFds
            label={t('Sender email from')}
            error={!!errors.scenario_mail_from_name}
            helperText={errors.scenario_mail_from_name?.message}
            {...register('scenario_mail_from_name')}
            disabled={disabled}
          />
          <Controller
            control={control}
            name="scenario_mails_reply_to"
            render={({ field, fieldState }) => {
              return (
                <Combobox<string>
                  multiple
                  // No `onOpenChange` beside it: nothing can move the panel, so none is
                  // mounted and Enter/ArrowDown keep their native meaning in the input.
                  open={false}
                  options={[]}
                  value={field.value ?? []}
                  allowCustomValue
                  createValueFromInput={input => input.trim()}
                  getOptionLabel={email => email}
                  isOptionEqualToValue={(a, b) => a === b}
                  inputValue={inputValue}
                  onInputChange={(newInputValue, meta) => {
                    if (meta.cause === 'type') {
                      setInputValue(newInputValue);
                    }
                  }}
                  onValueChange={(next) => {
                    // Every value reaching here was typed, the list being empty by design.
                    const emails = (next as string[]).map(email => email.trim()).filter(email => email !== '');
                    field.onChange(Array.from(new Set(emails)));
                    // MUI cleared the text itself through its `reset` cause; the
                    // library reports causes instead, so the commit clears it here.
                    setInputValue('');
                  }}
                  clearable={false}
                  error={!!fieldState.error}
                >
                  <ComboboxLabel>{t('Reply to')}</ComboboxLabel>
                  <ComboboxField>
                    <ComboboxChips />
                    <ComboboxInput id="email-reply-to-input" onBlur={field.onBlur} />
                  </ComboboxField>
                  <ComboboxHelperText>
                    {errors.scenario_mails_reply_to?.find ? errors.scenario_mails_reply_to?.find(value => value != null)?.message ?? '' : ''}
                  </ComboboxHelperText>
                </Combobox>
              );
            }}
          />
          <TextFieldFds
            label={t('Messages header')}
            error={!!errors.scenario_message_header}
            helperText={errors.scenario_message_header?.message}
            {...register('scenario_message_header')}
            disabled={disabled}
          />
          <TextFieldFds
            label={t('Messages footer')}
            error={!!errors.scenario_message_footer}
            helperText={errors.scenario_message_footer?.message}
            {...register('scenario_message_footer')}
            disabled={disabled}
          />
        </>
      )}
      <div style={{
        display: 'flex',
        justifyContent: 'flex-end',
        gap: theme.spacing(1),
      }}
      >
        <Button type="button" priority="secondary" onClick={handleClose} disabled={isSubmitting}>
          {t('Cancel')}
        </Button>
        <Button type="submit" disabled={!isDirty || isSubmitting}>
          {editing ? t('Update') : t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default ScenarioForm;
