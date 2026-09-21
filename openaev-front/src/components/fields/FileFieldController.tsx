import { type FileRejection, FileSelect } from '@filigran/design-system';
import { type CSSProperties, type FunctionComponent } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { useFormatter } from '../i18n';

interface Props {
  name: string;
  label?: string;
  /** Accepted mime type fragments, e.g. ['image/', 'application/pdf'] */
  filters?: string[];
  style?: CSSProperties;
  disabled?: boolean;
}

// The form keeps a File[] — every consumer and the submit path read it that
// way — while the field itself is single-choice, so the two are bridged here
// rather than in the callers.
const FileFieldController: FunctionComponent<Props> = ({
  name,
  label,
  filters,
  style,
  disabled = false,
}) => {
  const { t } = useFormatter();
  const { control } = useFormContext();

  // `filters` holds mime FRAGMENTS ('image/', 'application/pdf'); the native
  // accept attribute takes the same strings, a bare prefix reading as the
  // wildcard it already is.
  const accept = filters && filters.length > 0
    ? filters.map(filter => (filter.endsWith('/') ? `${filter}*` : filter)).join(',')
    : undefined;

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { value, onChange }, fieldState: { error } }) => {
        const files: File[] = (value as File[]) ?? [];
        return (
          <div style={style}>
            <FileSelect
              value={files[0] ?? null}
              onValueChange={next => onChange(next ? [next as File] : [])}
              triggerLabel={label ?? t('Select a file')}
              accept={accept}
              disabled={disabled}
              error={error?.message}
              rejectionMessage={(rejections: FileRejection[]) => (rejections.length > 0
                ? t('This file type is not accepted here.')
                : '')}
            />
          </div>
        );
      }}
    />
  );
};

export default FileFieldController;
