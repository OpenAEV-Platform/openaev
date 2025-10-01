import { InputLabel } from '@mui/material';

// eslint-disable-next-line import/no-cycle
import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';
import CKEditor from '../CKEditor';

const SimpleRichTextField = (props) => {
  const {
    label,
    value,
    onChange = () => {},
    style,
    disabled,
    askAi,
    inInject,
    context,
    onBlur = () => {},
  } = props;
  return (
    <div style={{
      ...style,
      position: 'relative',
    }}
    >
      <InputLabel
        variant="standard"
        shrink={true}
        disabled={disabled}
      >
        {label}
      </InputLabel>
      <CKEditor
        data={value}
        onChange={(_, editor) => {
          onChange(editor.getData());
        }}
        onBlur={onBlur}
        disabled={disabled}
        toolbarDropdownSize="386px" // set a size for the ckeditor items toolbar to avoid it to be cut off when overflowing
      />
      {askAi && (
        <TextFieldAskAI
          currentValue={value ?? ''}
          setFieldValue={(val) => {
            onChange(val);
          }}
          format="html"
          variant="html"
          disabled={disabled}
          inInject={inInject}
          context={context}
        />
      )}
    </div>
  );
};

export default SimpleRichTextField;
