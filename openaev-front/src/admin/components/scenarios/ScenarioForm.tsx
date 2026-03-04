import { zodResolver } from '@hookform/resolvers/zod';
import { LinkOutlined, ScheduleOutlined } from '@mui/icons-material';
import { Autocomplete, Button, Card, CardActionArea, CardContent, Checkbox, Chip, FormControlLabel, MenuItem, TextField as MuiTextField, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';

import Tabs, { type TabsEntry } from '../../../components/common/tabs/Tabs';
import useTabs from '../../../components/common/tabs/useTabs';
import SelectField from '../../../components/fields/SelectField';
import TagField from '../../../components/fields/TagField';
import TextField from '../../../components/fields/TextField';
import { useFormatter } from '../../../components/i18n';
import { type ScenarioInput } from '../../../utils/api-types';
import { zodImplement } from '../../../utils/Zod';
import { scenarioCategories } from './constants';

interface Props {
  onSubmit: (data: ScenarioInput, isScenarioAssistantChecked?: boolean) => void;
  handleClose: () => void;
  editing?: boolean;
  disabled?: boolean;
  initialValues: ScenarioInput;
  isCreation?: boolean;
}

const ScenarioForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing,
  initialValues,
  disabled,
  isCreation = false,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [inputValue, setInputValue] = useState('');
  const [isScenarioAssistantChecked, setIsScenarioAssistantChecked] = useState(false);
  const [selectedType, setSelectedType] = useState<'time-based' | 'chaining' | null>(
    editing ? (initialValues.scenario_type as 'time-based' | 'chaining' ?? 'time-based') : null,
  );

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
        scenario_type: z.enum(['time-based', 'chaining']).optional(),
        scenario_subtitle: z.string().optional(),
        scenario_description: z.string().optional(),
        scenario_tags: z.string().array().optional(),
        scenario_external_reference: z.string().optional(),
        scenario_external_url: z.string().optional(),
        scenario_mail_from: z.email(t('Should be a valid email address')).optional(),
        scenario_mails_reply_to: z.array(z.email(t('Should be a valid email address'))).optional(),
        scenario_message_header: z.string().optional(),
        scenario_message_footer: z.string().optional(),
        scenario_custom_dashboard: z.string().optional(),
      }),
    ),
    defaultValues: initialValues,
  });

  const handleTypeSelect = (type: 'time-based' | 'chaining') => {
    setSelectedType(type);
    setValue('scenario_type', type, { shouldDirty: true });
  };

  const tabEntries: TabsEntry[] = [{
    key: 'General',
    label: t('General'),
  }, {
    key: 'Emails and SMS',
    label: t('Emails and SMS'),
  }];
  const { currentTab, handleChangeTab } = useTabs(tabEntries[0].key);

  // Step 1: Type selection (only in creation mode, before type is chosen)
  if (isCreation && selectedType === null) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(3),
          padding: theme.spacing(2),
        }}
      >
        <Typography variant="body1" color="text.secondary">
          {t('Select the type of scenario you want to create.')}
        </Typography>
        <div style={{ display: 'flex', gap: theme.spacing(2) }}>
          <Card
            variant="outlined"
            sx={{ flex: 1, '&:hover': { borderColor: theme.palette.primary.main } }}
          >
            <CardActionArea onClick={() => handleTypeSelect('chaining')} sx={{ height: '100%' }}>
              <CardContent sx={{ textAlign: 'center', py: 4 }}>
                <LinkOutlined sx={{ fontSize: 48, color: theme.palette.primary.main, mb: 1 }} />
                <Typography variant="h6" gutterBottom>
                  {t('Chaining Scenario')}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('Event-driven attack chain with conditional logic between actions.')}
                </Typography>
              </CardContent>
            </CardActionArea>
          </Card>
          <Card
            variant="outlined"
            sx={{ flex: 1, '&:hover': { borderColor: theme.palette.primary.main } }}
          >
            <CardActionArea onClick={() => handleTypeSelect('time-based')} sx={{ height: '100%' }}>
              <CardContent sx={{ textAlign: 'center', py: 4 }}>
                <ScheduleOutlined sx={{ fontSize: 48, color: theme.palette.primary.main, mb: 1 }} />
                <Typography variant="h6" gutterBottom>
                  {t('Time-based Scenario')}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('Scheduled inject execution based on a timeline.')}
                </Typography>
              </CardContent>
            </CardActionArea>
          </Card>
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button variant="contained" onClick={handleClose}>
            {t('Cancel')}
          </Button>
        </div>
      </div>
    );
  }

  // Step 2: Form (shown after type selection, or always when editing)
  return (
    <>
      <Tabs
        entries={tabEntries}
        currentTab={currentTab}
        onChange={newValue => handleChangeTab(newValue)}
      />
      <form
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
          marginTop: theme.spacing(2),
        }}
        id="scenarioForm"
        onSubmit={handleSubmit((data: ScenarioInput) => onSubmit({ ...data, scenario_type: selectedType ?? initialValues.scenario_type ?? undefined }, isScenarioAssistantChecked))}
      >
        {currentTab === 'General' && (
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
            <div style={{ display: 'flex', flexDirection: 'row', gap: 20 }}>
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
                <MenuItem key="endpoint-protection" value="endpoint-protection">{t('Endpoint Protection')}</MenuItem>
                <MenuItem key="web-filtering" value="web-filtering">{t('Web Filtering')}</MenuItem>
                <MenuItem key="incident-response" value="incident-response">{t('Incident Response')}</MenuItem>
                <MenuItem key="standard-operating-procedure" value="standard-operating-procedure">{t('Standard Operating Procedures')}</MenuItem>
                <MenuItem key="crisis-communication" value="crisis-communication">{t('Crisis Communication')}</MenuItem>
                <MenuItem key="strategic-reaction" value="strategic-reaction">{t('Strategic Reaction')}</MenuItem>
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
              <MenuItem key="low" value="low">{t('Low')}</MenuItem>
              <MenuItem key="medium" value="medium">{t('Medium')}</MenuItem>
              <MenuItem key="high" value="high">{t('High')}</MenuItem>
              <MenuItem key="critical" value="critical">{t('Critical')}</MenuItem>
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
        )}
        {currentTab === 'Emails and SMS' && (
          <>
            <MuiTextField
              variant="standard"
              fullWidth
              label={t('Sender email address')}
              error={!!errors.scenario_mail_from}
              helperText={
                errors.scenario_mail_from
                  ? errors.scenario_mail_from?.message
                  : (
                      <span style={{ color: theme.palette.warning.main }}>
                        {t('If you remove the default email address, the email reception for this simulation / scenario will be disabled.')}
                      </span>
                    )
              }
              inputProps={register('scenario_mail_from')}
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
              helperText={errors.scenario_message_header && errors.scenario_message_header?.message}
              inputProps={register('scenario_message_header')}
              disabled={disabled}
            />
            <MuiTextField
              variant="standard"
              fullWidth
              label={t('Messages footer')}
              error={!!errors.scenario_message_footer}
              helperText={errors.scenario_message_footer && errors.scenario_message_footer?.message}
              inputProps={register('scenario_message_footer')}
              disabled={disabled}
            />
          </>
        )}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: theme.spacing(1) }}>
          {isCreation && (
            <Button
              variant="text"
              onClick={() => setSelectedType(null)}
            >
              {t('Back')}
            </Button>
          )}
          <Button variant="contained" onClick={handleClose} disabled={isSubmitting}>
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
    </>
  );
};

export default ScenarioForm;
