import { Box, FormHelperText, InputLabel } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';
import { useController, useFormContext } from 'react-hook-form';

import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';
import MarkDownField from './MarkDownField';

interface Props {
  name: string;
  label: string;
  style: CSSProperties;
  disabled?: boolean;
  askAi?: boolean;
  inInject: boolean;
  inArticle?: boolean;
}

const MarkDownFieldController: FunctionComponent<Props> = ({
  name,
  label,
  style,
  disabled,
  askAi,
  inInject,
  inArticle,
}) => {
  const { control } = useFormContext();
  const {
    field: { onChange, value },
    fieldState: { invalid, error },
  } = useController({
    name,
    control,
    defaultValue: '',
  });

  return (
    <div
      style={{
        ...style,
        position: 'relative',
      }}
      className={invalid ? 'error' : 'main'}
    >
      <InputLabel shrink={true} variant="standard">
        {label}
      </InputLabel>
      <Box
        sx={{
          flexGrow: 1,
          position: 'relative',
        }}
      >
        <MarkDownField
          initialValue={value}
          disabled={disabled}
          onChange={onChange}
        />
        {askAi && (
          <TextFieldAskAI
            currentValue={value ?? ''}
            setFieldValue={(val) => {
              onChange(val);
            }}
            format="markdown"
            variant="markdown"
            disabled={disabled}
            inInject={inInject}
            inArticle={inArticle}
            style={{
              position: 'absolute',
              top: 4,
              right: 4,
            }}
          />
        )}
      </Box>
      {invalid && (
        <FormHelperText error={true}>
          {error?.message}
        </FormHelperText>
      )}
    </div>
  );
};

export default MarkDownFieldController;
