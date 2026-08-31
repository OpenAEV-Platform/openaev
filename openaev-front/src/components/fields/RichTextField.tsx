import { RichTextEditor } from '@filigran/rich-text-editor';
import { FormHelperText, InputLabel } from '@mui/material';
import { type CSSProperties } from 'react';
import { type Control, Controller, type FieldPath, type FieldValues } from 'react-hook-form';

import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';

interface Props<TFieldValues extends FieldValues = FieldValues> {
  label: string;
  control: Control<TFieldValues>;
  name: FieldPath<TFieldValues>;
  style?: CSSProperties;
  disabled: boolean;
  askAi: boolean;
  inInject: boolean;
  required?: boolean;
}

const RichTextField = <TFieldValues extends FieldValues = FieldValues>({
  control,
  label,
  name,
  style = {},
  disabled,
  askAi,
  inInject,
  required,
}: Props<TFieldValues>) => {
  return (
    <div style={{
      ...style,
      position: 'relative',
    }}
    >
      <Controller
        name={name}
        control={control}
        rules={{ required: true }}
        render={({
          field: { onChange, onBlur, value },
          fieldState: { invalid, error: fieldError },
        }) => (
          <>
            <InputLabel
              variant="standard"
              shrink={true}
              disabled={disabled}
              required={required}
              error={!!fieldError}
            >
              {label}
            </InputLabel>
            <RichTextEditor
              variant="outlined"
              data={value || ''}
              onChange={(_, editor) => {
                onChange(editor.getData());
              }}
              onBlur={onBlur}
              disabled={disabled}
            />
            {(invalid) && (
              <FormHelperText error>
                {(fieldError?.message)}
              </FormHelperText>
            )}
            {askAi && (
              <TextFieldAskAI
                currentValue={value ?? ''}
                setFieldValue={(val) => {
                  onChange(val);
                }}
                format="html"
                variant="ckeditor"
                disabled={disabled}
                inInject={inInject}
              />
            )}
          </>
        )}
      />
    </div>
  );
};

export default RichTextField;
