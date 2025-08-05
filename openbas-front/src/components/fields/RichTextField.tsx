import { FormHelperText, InputLabel } from '@mui/material';
import { type CSSProperties } from 'react';
import { type Control, Controller } from 'react-hook-form';

import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';
import CKEditor from '../CKEditor';

interface Props {
  label: string;
  control: Control;
  name: string;
  style?: CSSProperties;
  disabled: boolean;
  askAi: boolean;
  inInject: boolean;
  required?: boolean;
}

const RichTextField = ({
  control,
  label,
  name,
  style = {},
  disabled,
  askAi,
  inInject,
  required,
}: Props) => {
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
            <CKEditor
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
