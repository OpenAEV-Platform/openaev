import { TimePicker } from '@filigran/design-system';
import { type ReactNode } from 'react';
import { type Control, Controller, type FieldPath, type FieldValues, useFormContext } from 'react-hook-form';

import { useFormatter } from '../i18n';

interface CommonProps {
  label?: string;
  required?: boolean;
  disabled?: boolean;
  clearable?: boolean;
  minTime?: Date;
  maxTime?: Date;
  /** Minutes between two offered values, as the MUI timeSteps did. */
  minutesStep?: number;
  /** Fixed pattern; without it the locale decides. */
  format?: string;
  /** 'UTC' or an IANA name where the stored time is not the browser's. */
  timezone?: string;
  helperText?: ReactNode;
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
  value?: never;
  onChange?: never;
  error?: never;
}

interface ControlledProps extends CommonProps {
  name?: never;
  control?: never;
  value?: Date | null;
  onChange: (value: Date | null) => void;
  error?: string;
}

type Props<T extends FieldValues> = FormProps<T> | ControlledProps;

/**
 * The product's single time-of-day field, over the library TimePicker. Same two
 * bindings as DateField, and the same things settled once: the product locale,
 * the translated names the clock renders, and the width on the field itself.
 */
const TimeField = <T extends FieldValues = FieldValues>({
  label, required, disabled, clearable, minTime, maxTime, minutesStep,
  format, timezone, helperText, infoTooltip, className, onAccept, onBlur,
  ...binding
}: Props<T>) => {
  const { t, locale } = useFormatter();
  const formContext = useFormContext<T>();

  const shared = {
    label,
    required,
    disabled,
    clearable,
    minTime,
    maxTime,
    minutesStep,
    format,
    timezone,
    helperText,
    infoTooltip,
    locale,
    onAccept,
    className: className ?? 'w-full',
    openClockLabel: t('Open clock'),
    clearLabel: t('Clear'),
    clockLabel: t('Clock'),
    hoursLabel: t('Hours'),
    minutesLabel: t('Minutes'),
  };

  if (binding.name !== undefined) {
    const { name, control } = binding as FormProps<T>;
    return (
      <Controller
        control={control ?? formContext?.control}
        name={name}
        render={({ field, fieldState }) => (
          <TimePicker
            {...shared}
            value={field.value ? new Date(field.value) : null}
            onChange={time => field.onChange(time ? time.toISOString() : undefined)}
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
  return <TimePicker {...shared} value={value} onChange={time => onChange(time)} onBlur={onBlur} error={error} />;
};

export default TimeField;
