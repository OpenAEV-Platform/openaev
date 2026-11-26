import { Slider } from '@filigran/design-system';
import { type ReactNode } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

interface Props {
  name: string;
  /** Visible label. Without it, `ariaLabel` is the accessible name of the thumb. */
  label?: string;
  ariaLabel?: string;
  helperText?: ReactNode;
  min?: number;
  max?: number;
  step?: number;
  disabled?: boolean;
  /** Draws `min` and `max` under the track. */
  showBounds?: boolean;
}

/**
 * Single-thumb slider bound to a number in the form. The library slider takes and
 * returns an array (one entry per thumb): the form keeps the plain number. A value that is not
 * a finite number (unset, null, a string) is shown at `min`, the form value itself is untouched.
 */
const SliderFieldController = ({
  name,
  label,
  ariaLabel,
  helperText,
  min = 0,
  max = 100,
  step = 1,
  disabled = false,
  showBounds = false,
}: Props) => {
  const { control } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => (
        <Slider
          ref={field.ref}
          name={field.name}
          label={label}
          aria-label={label ? undefined : ariaLabel}
          // The library slider has no `error` prop: the validation message takes the help text slot.
          helperText={error?.message ?? helperText}
          min={min}
          max={max}
          step={step}
          disabled={disabled}
          showBounds={showBounds}
          value={[Number.isFinite(field.value) ? field.value : min]}
          onValueChange={([value]) => field.onChange(value)}
          onValueCommit={field.onBlur}
        />
      )}
    />
  );
};

export default SliderFieldController;
