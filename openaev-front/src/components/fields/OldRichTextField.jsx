import { FormHelperText, InputLabel } from '@mui/material';
import { Field } from 'react-final-form';
import { makeStyles } from 'tss-react/mui';

// eslint-disable-next-line import/no-cycle
import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';
import CKEditor from '../CKEditor';
import { useFormatter } from '../i18n';

const useStyles = makeStyles()(theme => ({ errorColor: { color: theme.palette.error.main } }));

const RichTextFieldBase = ({
  label,
  input: { onChange, onBlur, value },
  meta: { touched, error, invalid, submitError },
  style,
  disabled,
  askAi,
  inInject,
  context,
}) => {
  const { t } = useFormatter();
  const { classes, cx } = useStyles();
  return (
    (
      <div style={{
        ...style,
        position: 'relative',
      }}
      >
        <InputLabel
          variant="standard"
          shrink={true}
          disabled={disabled}
          className={cx({ [classes.errorColor]: touched && invalid })}
        >
          {label}
        </InputLabel>
        <CKEditor
          data={value}
          onChange={(event, editor) => {
            onChange(editor.getData());
          }}
          onBlur={event => onBlur(event)}
          disabled={disabled}
        />
        {touched && invalid
          && (
            <FormHelperText error>
              {(error && t(error)) || (submitError && t(submitError))}
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
            context={context}
          />
        )}
      </div>
    )
  );
};

/**
 * @deprecated The component use old form libnary react-final-form
 */
const OldRichTextField = props => (
  <Field name={props.name} component={RichTextFieldBase} {...props} />
);

export default OldRichTextField;
