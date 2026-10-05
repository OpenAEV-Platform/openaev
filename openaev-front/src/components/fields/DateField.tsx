import { DatePicker } from '@filigran/design-system';
import { type ReactNode } from 'react';
import { type Control, Controller, type FieldPath, type FieldValues, useFormContext } from 'react-hook-form';

import { useFormatter } from '../i18n';

interface CommonProps {
  label?: string;
  /** Date and clock instead of date alone (the MUI DateTimePicker). */
  withTime?: boolean;
  required?: boolean;
  disabled?: boolean;
  clearable?: boolean;
  minDate?: Date;
  maxDate?: Date;
  minDateTime?: Date;
  maxDateTime?: Date;
  /** Fixed pattern; without it the locale decides. */
  format?: string;
  helperText?: ReactNode;
  /** Help bubble beside the label, where MUI took a ReactNode label. */
  infoTooltip?: ReactNode;
  className?: string;
  onAccept?: (value: Date | null) => void;
  /** Forwarded to the field: forms validating on touch need the blur. */
  onBlur?: () => void;
}

interface FormProps<T extends FieldValues> extends CommonProps {
  /** react-hook-form path. The stored value is an ISO string. */
  name: FieldPath<T>;
  /** Needed where the form is local (`useForm`) rather than in a provider. */
  control?: Control<T>;
  /** What an empty field stores: some forms keep '' rather than dropping the key. */
  clearedValue?: string;
  /** How a picked date becomes the stored string; the default is its ISO form. */
  toStorage?: (value: Date) => string;
  value?: never;
  onChange?: never;
  error?: never;
}

interface ControlledProps extends CommonProps {
  name?: never;
  clearedValue?: never;
  /** Omit it to leave the field uncontrolled, as a filter popover does. */
  value?: Date | null;
  onChange: (value: Date | null) => void;
  /** Error message; its presence paints the error state. */
  error?: string;
}

type Props<T extends FieldValues> = FormProps<T> | ControlledProps;

/**
 * A date-only pick keeps the wall-clock time of the current value, as the MUI X
 * pickers this field replaced did; the library returns local midnight. Values
 * stored as UTC midnight would otherwise move to the previous day east of UTC.
 */
const keepTimeOf = (picked: Date | null, current: Date | null): Date | null => {
  if (!picked || !current || Number.isNaN(current.getTime())) return picked;
  const merged = new Date(picked);
  merged.setHours(current.getHours(), current.getMinutes(), current.getSeconds(), current.getMilliseconds());
  return merged;
};

/**
 * The product's single date field. Two bindings: `name` for react-hook-form,
 * `value`/`onChange` for a controlled field.
 */
const DateField = <T extends FieldValues = FieldValues>({
  label, withTime, required, disabled, clearable, minDate, maxDate,
  minDateTime, maxDateTime, format, helperText, infoTooltip, className, onAccept, onBlur,
  ...binding
}: Props<T>) => {
  const { t, locale } = useFormatter();
  const formContext = useFormContext<T>();

  const labels = {
    openCalendarLabel: t('Open calendar'),
    clearLabel: t('Clear'),
    calendarLabel: t('Calendar'),
    previousMonthLabel: t('Previous month'),
    nextMonthLabel: t('Next month'),
    monthSelectLabel: t('Month'),
    yearSelectLabel: t('Year'),
    hoursLabel: t('Hours'),
    minutesLabel: t('Minutes'),
  };

  const shared = {
    label,
    withTime,
    required,
    disabled,
    clearable,
    minDate,
    maxDate,
    minDateTime,
    maxDateTime,
    format,
    helperText,
    infoTooltip,
    locale,
    onAccept,
    // The width belongs to the field, not to a wrapper around it.
    className: className ?? 'w-full',
    ...labels,
  };

  if (binding.name !== undefined) {
    const { name, clearedValue, toStorage, control } = binding as FormProps<T>;
    return (
      <Controller
        control={control ?? formContext?.control}
        name={name}
        render={({ field, fieldState }) => (
          <DatePicker
            {...shared}
            value={field.value ? new Date(field.value) : null}
            onChange={(picked) => {
              const date = withTime ? picked : keepTimeOf(picked, field.value ? new Date(field.value) : null);
              field.onChange(date ? (toStorage ?? ((d: Date) => d.toISOString()))(date) : clearedValue);
            }}
            onBlur={() => {
              field.onBlur();
              onBlur?.();
            }}
            error={fieldState.error?.message}
          />
        )}
      />
    );
  }

  const { value, onChange, error } = binding as ControlledProps;
  return (
    <DatePicker
      {...shared}
      value={value}
      onChange={picked => onChange(withTime ? picked : keepTimeOf(picked, value ?? null))}
      onBlur={onBlur}
      error={error}
    />
  );
};

export default DateField;
