// fds:keep-mui the Ask AI adornment is a product component the library's end slot cannot host yet; decision pending, see LIBRARY-FEEDBACK #52
import { TextField as MuiTextField } from '@mui/material';
import { type Control, type FieldValues, type UseFormSetValue, useWatch } from 'react-hook-form';

import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';
import TextFieldFds, { type TextFieldFdsProps } from './TextFieldFds';

export type TextFieldProps<TFieldValues extends FieldValues = FieldValues>
  = TextFieldFdsProps & {
    /** Show the AskAI adornment that lets the user transform the field via XTM One. */
    askAi?: boolean;
    /** react-hook-form `control` — required to watch the field value when `askAi` is enabled. */
    control?: Control<TFieldValues>;
    /** react-hook-form `setValue` — required to write the AI-generated value back into the form. */
    setValue?: UseFormSetValue<TFieldValues>;
  };

const TextField = <TFieldValues extends FieldValues = FieldValues>({
  askAi,
  control,
  setValue,
  ...props
}: TextFieldProps<TFieldValues>) => {
  const fieldName = props.name;
  const watchedValue = useWatch({
    // `name` is keyed off the underlying form so we widen here; runtime safety is enforced by the
    // `disabled` flag below (we only subscribe when both a control and a name are available).
    control: control as Control<FieldValues> | undefined,
    name: fieldName ?? '',
    disabled: !control || !fieldName,
  });

  const currentValue: unknown = fieldName ? watchedValue : undefined;

  if (askAi && fieldName && setValue) {
    const { name, onChange, onBlur, label, required, error, helperText, multiline, rows, style, type, disabled, id, defaultValue, maxLength: _maxLength, ...rest } = props;
    return (
      <MuiTextField
        variant="standard"
        fullWidth
        label={required ? `${label}*` : label}
        error={typeof error === 'string' ? true : !!error}
        helperText={typeof error === 'string' ? error : helperText}
        multiline={multiline}
        rows={rows}
        style={style}
        type={type}
        disabled={disabled}
        id={id}
        defaultValue={defaultValue}
        inputProps={{
          name,
          onChange,
          onBlur,
          ...('data-testid' in rest ? { 'data-testid': rest['data-testid'] } : {}),
        }}
        value={currentValue ?? undefined}
        slotProps={{
          input: {
            endAdornment: (
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
                disabled={disabled}
              />
            ),
          },
        }}
      />
    );
  }

  return (
    <TextFieldFds
      {...props}
      value={control && fieldName ? (currentValue as string | undefined) ?? '' : props.value}
    />
  );
};

export default TextField;
