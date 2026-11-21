import { MenuItem, Typography } from '@mui/material';
import { type FunctionComponent } from 'react';
import { type UseFormReturn } from 'react-hook-form';

import SelectField from '../../../components/fields/SelectField';
import TextField from '../../../components/fields/TextField';
import { useFormatter } from '../../../components/i18n';
import DefaultKillChainSelectField from '../common/filters/DefaultKillChainSelectField';
import { scenarioCategories } from './constants';
import { type ScenarioFormInput } from './ScenarioForm';

interface Props {
  form: UseFormReturn<ScenarioFormInput>;
  initialValues: ScenarioFormInput;
  readOnly?: boolean;
}

// The general facts of a scenario: edited in its form, shown as they are for a catalogue one.
const ScenarioGeneralFields: FunctionComponent<Props> = ({ form, initialValues, readOnly = false }) => {
  const { t } = useFormatter();
  const { register, control, setValue, formState: { errors } } = form;
  return (
    <>
      <Typography variant="h2">
        {t('General')}
      </Typography>
      <TextField
        required
        label={t('Name')}
        error={!!errors.scenario_name}
        helperText={errors.scenario_name?.message}
        {...register('scenario_name')}
        control={control}
        setValue={setValue}
        disabled={readOnly}
        askAi={!readOnly}
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
          disabled={readOnly}
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
          disabled={readOnly}
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
      <div style={{
        display: 'flex',
        flexDirection: 'row',
        gap: 20,
      }}
      >
        <SelectField
          variant="standard"
          fullWidth={true}
          name="scenario_severity"
          disabled={readOnly}
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
        <DefaultKillChainSelectField<ScenarioFormInput>
          name="scenario_default_kill_chain"
          disabled={readOnly}
          control={control}
          defaultValue={initialValues.scenario_default_kill_chain ?? undefined}
        />
      </div>
      <TextField
        multiline
        rows={5}
        label={t('Description')}
        error={!!errors.scenario_description}
        helperText={errors.scenario_description?.message}
        {...register('scenario_description')}
        control={control}
        setValue={setValue}
        disabled={readOnly}
        askAi={!readOnly}
      />
    </>
  );
};

export default ScenarioGeneralFields;
