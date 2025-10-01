import { TextField as MuiTextField } from '@mui/material';
import { useWatch } from 'react-hook-form';

import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';

const TextFieldBase = ({ askAi, control, setValue, ...props }) => {
  const currentValue = control && useWatch({
    control,
    name: props.inputProps.name,
  });
  return (
    <MuiTextField
      {...props}
      value={currentValue ?? undefined}
      slotProps={{
        input: {
          endAdornment: askAi && (
            <TextFieldAskAI
              variant="text"
              currentValue={currentValue ?? ''}
              setFieldValue={val => setValue(props.inputProps.name, val)}
              format="text"
              disabled={props.disabled}
            />
          ),
        },
      }}
    />
  );
};

const TextField = (props) => {
  return (
    <TextFieldBase {...props} />
  );
};

export default TextField;
