import { Button, FormHelperText } from '@mui/material';
import { type ChangeEvent, type CSSProperties, type FunctionComponent, useRef, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { bytesFormat } from '../../utils/number';
import { useFormatter } from '../i18n';

interface Props {
  name: string;
  label?: string;
  /** Accepted mime type fragments, e.g. ['image/', 'application/pdf'] */
  filters?: string[];
  style?: CSSProperties;
  disabled?: boolean;
}

const isAccepted = (file: File, filters?: string[]) => !filters || filters.length === 0
  || filters.some(filter => file.type.includes(filter));

const FileFieldController: FunctionComponent<Props> = ({
  name,
  label,
  filters,
  style,
  disabled = false,
}) => {
  const { t } = useFormatter();
  const { control } = useFormContext();
  const inputRef = useRef<HTMLInputElement>(null);
  const [rejectedFile, setRejectedFile] = useState(false);

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { value, onChange }, fieldState: { error } }) => {
        const files: File[] = (value as File[]) ?? [];

        const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
          const selectedFiles = [...(event.target.files ?? [])];
          if (selectedFiles.length === 0) {
            return;
          }
          // Reset value so selecting the same file again triggers onChange.
          event.target.value = '';
          const acceptedFiles = selectedFiles.filter(file => isAccepted(file, filters));
          setRejectedFile(acceptedFiles.length === 0);
          if (acceptedFiles.length > 0) {
            onChange(acceptedFiles);
          }
        };

        return (
          <div style={style}>
            <input
              ref={inputRef}
              style={{ display: 'none' }}
              type="file"
              disabled={disabled}
              onChange={handleChange}
            />
            <Button
              variant="outlined"
              color="primary"
              disabled={disabled}
              onClick={() => inputRef.current?.click()}
            >
              {label ?? t('Select a file')}
            </Button>
            {rejectedFile && (
              <FormHelperText error focused>
                {t('This file type is not accepted here.')}
              </FormHelperText>
            )}
            {!rejectedFile && files.map(file => (
              <FormHelperText key={file.name} focused>
                {`${file.name} - ${bytesFormat(file.size).number}${bytesFormat(file.size).symbol}`}
              </FormHelperText>
            ))}
            {error && <FormHelperText error>{error.message}</FormHelperText>}
          </div>
        );
      }}
    />
  );
};

export default FileFieldController;
