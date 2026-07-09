import { RichTextEditor } from '@filigran/rich-text-editor';
import { InputLabel } from '@mui/material';

// eslint-disable-next-line import/no-cycle
import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';

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
      <RichTextEditor
        variant="outlined"
        data={value}
        onChange={(_, editor) => {
          onChange(editor.getData());
        }}
        onBlur={onBlur}
        disabled={disabled}
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
