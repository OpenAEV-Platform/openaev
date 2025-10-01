import { FormHelperText, InputLabel, useTheme } from '@mui/material';
import MDEditor, { commands } from '@uiw/react-md-editor/nohighlight';
import { type CSSProperties, type FunctionComponent } from 'react';
import { Field, type FieldInputProps, type FieldMetaState } from 'react-final-form';

import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';
import { useFormatter } from '../i18n';

interface Props {
  label: string;
  style: CSSProperties;
  disabled?: boolean;
  input: FieldInputProps<string, HTMLElement>;
  meta: FieldMetaState<string>;
  askAi?: boolean;
  inInject: boolean;
  inArticle?: boolean;
}

const MarkDownFieldBase: FunctionComponent<Props> = ({
  label,
  style,
  disabled,
  input: { onChange, value },
  meta: { touched, invalid, error, submitError },
  askAi,
  inInject,
  inArticle,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  return (
    <div
      style={{
        ...style,
        position: 'relative',
      }}
      className={touched && invalid ? 'error' : 'main'}
      data-color-mode={theme.palette.mode}
    >
      <InputLabel shrink={true} variant="standard">
        {label}
      </InputLabel>
      <MDEditor
        value={value}
        textareaProps={{ disabled }}
        preview="edit"
        onChange={data => onChange(data)}
        commands={[
          {
            ...commands.title,
            buttonProps: { disabled },
          },
          {
            ...commands.bold,
            buttonProps: { disabled },
          },
          {
            ...commands.italic,
            buttonProps: { disabled },
          },
          {
            ...commands.strikethrough,
            buttonProps: { disabled },
          },
          { ...commands.divider },
          {
            ...commands.link,
            buttonProps: { disabled },
          },
          {
            ...commands.quote,
            buttonProps: { disabled },
          },
          {
            ...commands.code,
            buttonProps: { disabled },
          },
          {
            ...commands.image,
            buttonProps: { disabled },
          },
          {
            ...commands.divider,
            buttonProps: { disabled },
          },
          {
            ...commands.unorderedListCommand,
            buttonProps: { disabled },
          },
          {
            ...commands.orderedListCommand,
            buttonProps: { disabled },
          },
          {
            ...commands.checkedListCommand,
            buttonProps: { disabled },
          },
        ]}
        extraCommands={[]}
      />
      {touched && invalid && (
        <FormHelperText error={true}>
          {(error && t(error)) || (submitError && t(submitError))}
        </FormHelperText>
      )}
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
        />
      )}
    </div>
  );
};

/**
 * @deprecated The component use old form libnary react-final-form
 */
const OldMarkDownField = (props: Props & { name: string }) => (
  <Field component={MarkDownFieldBase} {...props} />
);

export default OldMarkDownField;
