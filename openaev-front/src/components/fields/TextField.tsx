import { TextField as MuiTextField, type TextFieldProps as MuiTextFieldProps } from '@mui/material';
import { type Control, type FieldValues, type UseFormSetValue, useWatch } from 'react-hook-form';

import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';

export type TextFieldProps<TFieldValues extends FieldValues = FieldValues>
  = MuiTextFieldProps & {
    /** Show the AskAI adornment that lets the user transform the field via XTM One. */
    askAi?: boolean;
    /** react-hook-form `control` — required to watch the field value when `askAi` is enabled. */
    control?: Control<TFieldValues>;
    /** react-hook-form `setValue` — required to write the AI-generated value back into the form. */
    setValue?: UseFormSetValue<TFieldValues>;
    /**
     * Pre-existing pass-through used by some call sites (e.g. `ExerciseForm`). Not consumed by the
     * wrapper itself — kept to preserve the .jsx behaviour where unknown props were spread to MUI.
     */
    maxLength?: number;
  };

const TextField = <TFieldValues extends FieldValues = FieldValues>({
  askAi,
  control,
  setValue,
  maxLength: _maxLength,
  ...props
}: TextFieldProps<TFieldValues>) => {
  const fieldName = (props.inputProps as { name?: string } | undefined)?.name;
  const watchedValue = useWatch({
    // `name` is keyed off the underlying form so we widen here; runtime safety is enforced by the
    // `disabled` flag below (we only subscribe when both a control and a name are available).
    control: control as Control<FieldValues> | undefined,
    name: fieldName ?? '',
    disabled: !control || !fieldName,
  });

  const currentValue: unknown = fieldName ? watchedValue : undefined;

  return (
    <MuiTextField
      {...props}
      value={currentValue ?? undefined}
      slotProps={{
        input: {
          endAdornment: askAi && fieldName && setValue
            ? (
                <TextFieldAskAI
                  variant="text"
                  currentValue={typeof currentValue === 'string' ? currentValue : ''}
                  setFieldValue={(val: string) => (setValue as UseFormSetValue<FieldValues>)(
                    fieldName,
                    val,
                    {
                      shouldDirty: true,
                      shouldValidate: true,
                    },
                  )}
                  format="text"
                  disabled={props.disabled}
                />
              )
            : undefined,
        },
      }}
    />
  );
};

export default TextField;
