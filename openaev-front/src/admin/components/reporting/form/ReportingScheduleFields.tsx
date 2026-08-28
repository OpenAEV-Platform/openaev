import {
  Combobox,
  ComboboxChips,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
} from '@filigran/design-system';
import { TextField as MuiTextField } from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { searchPlayerByIdAsOption, searchPlayersAsOption } from '../../../../actions/users/User';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import SwitchFieldController from '../../../../components/fields/SwitchFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { type Option } from '../../../../utils/Option';
import { REPORTING_FORMATS, REPORTING_SCHEDULE_PERIODS, SCHEDULE_PERIOD_LABELS, WEEKDAY_LABELS } from '../ReportingFormUtils';
import ReportingAutocompleteField from './ReportingAutocompleteField';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Schedule form fields, shared between the creation wizard (step 4) and the
 * standalone schedule drawer of the report detail page. Field names are the
 * flat `schedule_*` keys of both hosting forms.
 */
export interface ReportingScheduleFieldsValues {
  schedule_name: string;
  schedule_period: 'HOUR' | 'DAY' | 'WEEK' | 'MONTH';
  /** ISO day of week ("1"-"7") for WEEK, day of month ("1"-"31") for MONTH. */
  schedule_day: string;
  schedule_time: string;
  schedule_format: 'PDF' | 'HTML';
  schedule_enabled: boolean;
  schedule_recipient_users: string[];
  schedule_recipient_emails: string[];
}

interface Props {
  /** The standalone drawer edits the schedule's own enabled flag; the wizard step has its own "add a schedule" switch instead. */
  showEnabledSwitch?: boolean;
}

const ReportingScheduleFields: FunctionComponent<Props> = ({ showEnabledSwitch = false }) => {
  const { t } = useFormatter();
  const { control, watch, getValues } = useFormContext();

  const period = watch('schedule_period');

  // -- Recipient users options: text-searched list merged with the resolved
  // currently-selected users (so their chips always display a name).
  const [searchedUsers, setSearchedUsers] = useState<Option[]>([]);
  const [selectedUserOptions, setSelectedUserOptions] = useState<Option[]>([]);

  useEffect(() => {
    searchPlayersAsOption()
      .then((result: { data: Option[] }) => setSearchedUsers(result.data))
      .catch(() => setSearchedUsers([]));
    const initialIds: string[] = getValues('schedule_recipient_users') ?? [];
    if (initialIds.length > 0) {
      searchPlayerByIdAsOption(initialIds)
        .then((result: { data: Option[] }) => setSelectedUserOptions(result.data))
        .catch(() => setSelectedUserOptions([]));
    }
    // Initial load only: subsequent selections always come from searchedUsers.
  }, []);

  const userOptions = useMemo(() => {
    const searchedIds = new Set(searchedUsers.map(option => option.id));
    return [...selectedUserOptions.filter(option => !searchedIds.has(option.id)), ...searchedUsers];
  }, [searchedUsers, selectedUserOptions]);

  // Free-entry email chips input state (same pattern as the simulation
  // "Reply to" field).
  const [emailInput, setEmailInput] = useState('');

  return (
    <>
      {showEnabledSwitch && (
        <SwitchFieldController name="schedule_enabled" label={t('Enabled')} />
      )}
      <TextFieldController
        variant="standard"
        name="schedule_name"
        label={t('Name')}
      />
      <SelectFieldController
        name="schedule_period"
        label={t('Period')}
        required
        items={REPORTING_SCHEDULE_PERIODS.map(value => ({
          value,
          label: t(SCHEDULE_PERIOD_LABELS[value]),
        }))}
      />
      {period === 'WEEK' && (
        <SelectFieldController
          name="schedule_day"
          label={t('Day of week')}
          required
          items={WEEKDAY_LABELS.map((label, index) => ({
            value: String(index + 1),
            label: t(label),
          }))}
        />
      )}
      {period === 'MONTH' && (
        <SelectFieldController
          name="schedule_day"
          label={t('Day of month')}
          required
          items={Array.from({ length: 31 }, (_, index) => ({
            value: String(index + 1),
            label: String(index + 1),
          }))}
        />
      )}
      {period !== 'HOUR' && (
        <Controller
          control={control}
          name="schedule_time"
          render={({ field, fieldState: { error } }) => (
            <MuiTextField
              {...field}
              type="time"
              variant="standard"
              fullWidth
              label={t('Time of day')}
              slotProps={{ inputLabel: { shrink: true } }}
              error={!!error}
              helperText={error?.message}
            />
          )}
        />
      )}
      <SelectFieldController
        name="schedule_format"
        label={t('Format')}
        required
        items={REPORTING_FORMATS.map(value => ({
          value,
          label: value,
        }))}
      />
      <Controller
        control={control}
        name="schedule_recipient_users"
        render={({ field }) => (
          <ReportingAutocompleteField
            multiple
            label={t('Recipient users')}
            value={field.value ?? []}
            onChange={field.onChange}
            options={userOptions}
            onInputChange={(search: string) => {
              searchPlayersAsOption(search)
                .then((result: { data: Option[] }) => setSearchedUsers(result.data))
                .catch(() => setSearchedUsers([]));
            }}
          />
        )}
      />
      <Controller
        control={control}
        name="schedule_recipient_emails"
        render={({ field, fieldState: { error } }) => {
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
              inputValue={emailInput}
              onInputChange={(newInputValue, meta) => {
                if (meta.cause === 'type') {
                  setEmailInput(newInputValue);
                }
              }}
              onValueChange={(next) => {
                // Same guard as the former `addEmail`: shape checked, duplicates dropped.
                const emails = (next as string[])
                  .map(email => email.trim())
                  .filter(email => EMAIL_REGEX.test(email));
                field.onChange(Array.from(new Set(emails)));
                // MUI cleared the text itself through its `reset` cause; the
                // library reports causes instead, so the commit clears it here.
                setEmailInput('');
              }}
              clearable={false}
              error={!!error}
            >
              <ComboboxLabel>{t('Recipient emails')}</ComboboxLabel>
              <ComboboxField>
                <ComboboxChips />
                <ComboboxInput
                  placeholder={t('Type an email and press Enter')}
                  onBlur={() => field.onBlur()}
                />
              </ComboboxField>
              {error?.message ? <ComboboxHelperText>{error.message}</ComboboxHelperText> : null}
            </Combobox>
          );
        }}
      />
    </>
  );
};

export default ReportingScheduleFields;
