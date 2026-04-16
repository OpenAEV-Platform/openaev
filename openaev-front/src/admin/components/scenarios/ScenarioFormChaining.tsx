import { zodResolver } from '@hookform/resolvers/zod';
import { ExpandMore } from '@mui/icons-material';
import {
  Accordion,
  AccordionDetails, AccordionSummary,
  Autocomplete,
  Checkbox,
  Chip,
  FormControlLabel,
  MenuItem,
  TextField as MuiTextField, Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';

import type { LoggedHelper } from '../../../actions/helper';
import Button from '../../../components/common/button/Button';
import SelectField from '../../../components/fields/SelectField';
import TagField from '../../../components/fields/TagField';
import TextField from '../../../components/fields/TextField';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type PlatformSettings, type ScenarioInput } from '../../../utils/api-types';
import { zodImplement } from '../../../utils/Zod';
import { scenarioCategories } from './constants';

interface Props {
  onSubmit: (data: ScenarioInput, isScenarioAssistantChecked?: boolean) => void;
  handleClose: () => void;
  editing?: boolean;
  disabled?: boolean;
  initialValues: ScenarioInput;
  isCreation?: boolean;
  isChaining?: boolean;
}

const ScenarioFormChaining: FunctionComponent<Props> = ({
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

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isDirty, isSubmitting },
    setValue,
  } = useForm<ScenarioInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<ScenarioInput>().with({
        scenario_name: z.string().min(1, { message: t('Should not be empty') }),
        scenario_category: z.string().optional(),
        scenario_main_focus: z.string().optional(),
        scenario_severity: z.enum(['low', 'medium', 'high', 'critical']).optional(),
        scenario_subtitle: z.string().optional(),
        scenario_description: z.string().optional(),
        scenario_tags: z.string().array().optional(),
        scenario_external_reference: z.string().optional(),
        scenario_external_url: z.string().optional(),
        scenario_mail_from_name: z.string().max(100, t('Should not exceed {max_length} characters', { max_length: '100' })).optional(),
        scenario_mails_reply_to: z.array(z.email(t('Should be a valid email address'))).optional(),
        scenario_message_header: z.string().optional(),
        scenario_message_footer: z.string().optional(),
        scenario_custom_dashboard: z.string().optional(),
        scenario_is_chaining: z.boolean().optional(),
      }),
    ),
    defaultValues: initialValues,
  });

  return (
    <>
      <form
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
        id="scenarioForm"
        onSubmit={handleSubmit((data: ScenarioInput) => onSubmit(data, isScenarioAssistantChecked))}
      >
        <Typography
          variant="h2"
          gutterBottom
        >
          {t('General')}
        </Typography>
        <>
          <TextField
            variant="standard"
            fullWidth
            label={t('Name')}
            error={!!errors.scenario_name}
            helperText={errors.scenario_name?.message}
            inputProps={register('scenario_name')}
            InputLabelProps={{ required: true }}
            control={control}
            setValue={setValue}
            askAi={true}
          />
          <div style={{
            display: 'flex',
            flexDirection: 'row',
            gap: 20,
          }}
          >
            <SelectField
              variant="standard"
              fullWidth={true}
              name="scenario_category"
              label={t('Category')}
              error={!!errors.scenario_category}
              control={control}
              defaultValue={initialValues.scenario_category}
            >
              {Array.from(scenarioCategories).map(([key, value]) => (
                <MenuItem key={key} value={key}>
                  {t(value)}
                </MenuItem>
              ))}
            </SelectField>
            <SelectField
              variant="standard"
              fullWidth={true}
              name="scenario_main_focus"
              label={t('Main focus')}
              error={!!errors.scenario_main_focus}
              control={control}
              defaultValue={initialValues.scenario_main_focus}
            >
              <MenuItem key="endpoint-protection" value="endpoint-protection">
                {t('Endpoint Protection')}
              </MenuItem>
              <MenuItem key="web-filtering" value="web-filtering">
                {t('Web Filtering')}
              </MenuItem>
              <MenuItem key="incident-response" value="incident-response">
                {t('Incident Response')}
              </MenuItem>
              <MenuItem key="standard-operating-procedure" value="standard-operating-procedure">
                {t('Standard Operating Procedures')}
              </MenuItem>
              <MenuItem key="crisis-communication" value="crisis-communication">
                {t('Crisis Communication')}
              </MenuItem>
              <MenuItem key="strategic-reaction" value="strategic-reaction">
                {t('Strategic Reaction')}
              </MenuItem>
            </SelectField>
          </div>
          <SelectField
            variant="standard"
            fullWidth={true}
            name="scenario_severity"
            label={t('Severity')}
            error={!!errors.scenario_severity}
            control={control}
            defaultValue={initialValues.scenario_severity}
          >
            <MenuItem key="low" value="low">
              {t('Low')}
            </MenuItem>
            <MenuItem key="medium" value="medium">
              {t('Medium')}
            </MenuItem>
            <MenuItem key="high" value="high">
              {t('High')}
            </MenuItem>
            <MenuItem key="critical" value="critical">
              {t('Critical')}
            </MenuItem>
          </SelectField>
          <TextField
            variant="standard"
            fullWidth
            multiline
            rows={5}
            label={t('Description')}
            error={!!errors.scenario_description}
            helperText={errors.scenario_description?.message}
            inputProps={register('scenario_description')}
            control={control}
            setValue={setValue}
            askAi={true}
          />
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
            <FormControlLabel
              control={(
                <Checkbox
                  checked={isScenarioAssistantChecked}
                  onChange={() => setIsScenarioAssistantChecked(!isScenarioAssistantChecked)}
                />
              )}
              label={t('Use the scenario assistant')}
            />
          )}
        </>
        {!isChaining && (
          <Accordion
            defaultExpanded
            variant="outlined"
            sx={{
              'marginTop': 4,
              '&:before': { display: 'none' },
              'borderRadius': 1,
            }}
          >
            <AccordionSummary expandIcon={<ExpandMore />}>
              <Typography variant="h2" sx={{ margin: 0 }}>
                {t('Emails & SMS')}
              </Typography>
            </AccordionSummary>
            <AccordionDetails sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: theme.spacing(2),
            }}
            >
              <MuiTextField
                variant="standard"
                fullWidth
                label={t('Sender email address')}
                value={settings.default_mailer ?? ''}
                disabled
              />
              <MuiTextField
                variant="standard"
                fullWidth
                label={t('Sender email from')}
                error={!!errors.scenario_mail_from_name}
                helperText={errors.scenario_mail_from_name?.message}
                slotProps={{ htmlInput: register('scenario_mail_from_name') }}
                disabled={disabled}
              />
              <Controller
                control={control}
                name="scenario_mails_reply_to"
                render={({ field, fieldState }) => {
                  return (
                    <Autocomplete
                      multiple
                      id="email-reply-to-input"
                      freeSolo
                      open={false}
                      options={[]}
                      value={field.value}
                      onChange={() => {
                        if (undefined !== field.value && inputValue !== '' && !field.value.includes(inputValue)) {
                          field.onChange([...(field.value || []), inputValue.trim()]);
                        }
                      }}
                      onBlur={field.onBlur}
                      inputValue={inputValue}
                      onInputChange={(_event, newInputValue) => {
                        setInputValue(newInputValue);
                      }}
                      disableClearable={true}
                      renderTags={(tags: string[], getTagProps) => tags.map((email: string, index: number) => {
                        return (
                          <Chip
                            variant="outlined"
                            label={email}
                            {...getTagProps({ index })}
                            key={email}
                            style={{ borderRadius: 4 }}
                            onDelete={() => {
                              const newValue = [...(field.value || [])];
                              newValue.splice(index, 1);
                              field.onChange(newValue);
                            }}
                          />
                        );
                      })}
                      renderInput={params => (
                        <MuiTextField
                          {...params}
                          variant="standard"
                          label={t('Reply to')}
                          error={!!fieldState.error}
                          helperText={errors.scenario_mails_reply_to?.find ? errors.scenario_mails_reply_to?.find(value => value != null)?.message ?? '' : ''}
                        />
                      )}
                    />
                  );
                }}
              />
              <MuiTextField
                variant="standard"
                fullWidth
                label={t('Messages header')}
                error={!!errors.scenario_message_header}
                helperText={errors.scenario_message_header?.message}
                slotProps={{ htmlInput: register('scenario_message_header') }}
                disabled={disabled}
              />
              <MuiTextField
                variant="standard"
                fullWidth
                label={t('Messages footer')}
                error={!!errors.scenario_message_footer}
                helperText={errors.scenario_message_footer?.message}
                slotProps={{ htmlInput: register('scenario_message_footer') }}
                disabled={disabled}
              />
            </AccordionDetails>
          </Accordion>
        )}
        <div style={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: theme.spacing(1),
        }}
        >
          <Button
            variant="secondary"
            onClick={handleClose}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
          <Button
            variant="primary"
            type="submit"
            disabled={!isDirty || isSubmitting}
          >
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </>
  );
}
;

export default ScenarioFormChaining;
