import { Button, FormHelperText } from '@mui/material';
import * as R from 'ramda';
import { useCallback, useRef, useState } from 'react';
import { Field } from 'react-final-form';

import { bytesFormat } from '../utils/number';
import { useFormatter } from './i18n';

const FileFieldInput = ({ input, filters, ...props }) => {
  const [acceptedFiles, setAcceptedFiles] = useState([]);
  const inputRef = useRef();
  const { t } = useFormatter();
  const onChange = useCallback(
    (e) => {
      const files = [...e.target.files];
      const isErroredFile = files.length > 0
        && files.filter(
          f => !filters || R.any(n => f.type.includes(n), filters),
        ).length === 0;
      if (!isErroredFile) {
        setAcceptedFiles(files);
        input.onChange(files);
      }
    },
    [filters, input],
  );

  const isErroredFile = acceptedFiles.length > 0
    && acceptedFiles.filter(
      f => !filters || R.any(n => f.type.includes(n), filters),
    ).length === 0;
  const files = isErroredFile
    ? [
        <FormHelperText key={1} error={true} focused={true}>
          {t('This file type is not accepted here.')}
        </FormHelperText>,
      ]
    : acceptedFiles.map(file => (
        <FormHelperText key={file.name} focused={true}>
          {file.name}
          {' '}
          -
          {bytesFormat(file.size).number}
          {bytesFormat(file.size).symbol}
        </FormHelperText>
      ));
  return (
    <div
      style={{ marginTop: 20 }}
      onClick={() => {
        inputRef.current.click();
      }}
    >
      <input ref={inputRef} style={{ display: 'none' }} type="file" onInput={onChange} />
      <Button {...props} variant="outlined" color="primary">
        {t('Select a file')}
      </Button>
      {files}
      <div className="clearfix" />
    </div>
  );
};

const FileField = ({ name, ...props }) => (
  <>
    <Field name={name} {...props} component={FileFieldInput} />
    <Field
      name={name}
      subscribe={{
        touched: true,
        error: true,
      }}
      render={({ meta: { touched, error } }) => (touched && error
        ? (
            <FormHelperText error={true}>{error}</FormHelperText>
          )
        : null)}
    />
  </>
);

export default FileField;
