import { Box, Button, InputLabel } from '@mui/material';
import { type FormEvent, type FunctionComponent, useEffect, useState } from 'react';
import { type FieldErrors } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { truncate } from '../../utils/String';
import { useFormatter } from '../i18n';
import VisuallyHiddenInput from './VisuallyHiddenInput';

interface CustomFileUploadProps {
  name: string;
  fieldOnChange: (value: File | string | undefined) => void;
  label?: string;
  errors: FieldErrors;
  acceptMimeTypes?: string; // html input "accept" with MIME types only
  sizeLimit?: number; // in bytes
}

const useStyles = makeStyles()(theme => ({
  box: {
    'width': '100%',
    'marginTop': '0.2rem',
    'paddingBottom': '0.35rem',
    'borderBottom': `0.1rem solid ${theme.palette.grey[500]}`,
    'cursor': 'default',
    '&:hover': { borderBottom: '0.1rem solid white' },
    '&:active': { borderBottom: `0.1rem solid ${theme.palette.primary.main}` },
  },
  boxError: { borderBottom: `0.1rem solid ${theme.palette.error.main}` },
  button: { lineHeight: '0.65rem' },
  div: {
    marginTop: 20,
    width: '100%',
  },
  error: {
    color: theme.palette.error.main,
    fontSize: '0.75rem',
  },
  span: {
    marginLeft: 5,
    verticalAlign: 'bottom',
  },
}));

const CustomFileUploader: FunctionComponent<CustomFileUploadProps> = ({
  name,
  fieldOnChange,
  label,
  acceptMimeTypes,
  sizeLimit = 0, // defaults to 0 = no limit
  errors,
}) => {
  const { t } = useFormatter();
  const { classes, cx } = useStyles();
  const [fileNameForDisplay, setFileNameForDisplay] = useState('');
  const [errorText, setErrorText] = useState<string>('');

  useEffect(() => {
    if (errors[name]) {
      setErrorText('Should be a valid XLS file');
    } else {
      setErrorText('');
    }
  }, [errors[name]]);

  const onChange = async (event: FormEvent) => {
    const inputElement = event.target as HTMLInputElement;
    const eventTargetValue = inputElement.value as string;
    const file = inputElement.files?.[0];
    const fileSize = file?.size || 0;

    const newFileName = eventTargetValue.substring(
      eventTargetValue.lastIndexOf('\\') + 1,
    );
    setFileNameForDisplay(truncate(newFileName, 60) ?? '');
    setErrorText('');

    // check the file type; user might still provide something bypassing 'accept'
    // this will work only if accept is using MIME types only
    const acceptedList = acceptMimeTypes?.split(',').map(a => a.trim()) || [];
    if (
      acceptedList.length > 0
      && !!file?.type
      && !acceptedList.includes(file?.type)
    ) {
      setErrorText(t('This file is not in the specified format'));
      return;
    }

    // check the size limit if any set; if file is too big it is not set as value
    if (fileSize > 0 && sizeLimit > 0 && fileSize > sizeLimit) {
      setErrorText(t('This file is too large'));
      return;
    }

    fieldOnChange(inputElement.files?.[0]);
  };

  return (
    (
      <div className={classes.div}>
        <InputLabel shrink={true} variant="standard">
          {label ? t(label) : t('Associated file')}
        </InputLabel>
        <Box
          className={cx({
            [classes.box]: true,
            [classes.boxError]: !!errorText,
          })}
        >
          <Button
            component="label"
            variant="contained"
            onChange={onChange}
            className={classes.button}
          >
            {t('Select your file')}
            <VisuallyHiddenInput type="file" accept={acceptMimeTypes} />
          </Button>
          <span
            title={fileNameForDisplay || t('No file selected.')}
            className={classes.span}
          >
            {fileNameForDisplay || t('No file selected.')}
          </span>
        </Box>
        {!!errorText && (
          <div>
            <span className={classes.error}>{t(errorText)}</span>
          </div>
        )}
      </div>
    )
  );
};

export default CustomFileUploader;
