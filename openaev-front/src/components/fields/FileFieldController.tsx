import { type FileRejection, FileSelect } from '@filigran/design-system';
import { type CSSProperties, type FunctionComponent } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { useFormatter } from '../i18n';

interface Props {
  name: string;
  label?: string;
  required?: boolean;
  /** html input "accept", MIME types only. */
  acceptMimeTypes?: string;
  /** Accepted mime type fragments, e.g. ['image/', 'application/pdf'] */
  filters?: string[];
  /** Maximum accepted size in bytes, 0 means no limit. */
  sizeLimit?: number;
  style?: CSSProperties;
  disabled?: boolean;
}

/** Turns a mime fragment such as `image/` into the html "accept" wildcard `image/*`. */
const toAcceptMimeTypes = (filters?: string[]) => (filters && filters.length > 0)
  ? filters.map(filter => filter.endsWith('/') ? `${filter}*` : filter).join(',')
  : undefined;

/**
 * Upload control bound to a react-hook-form field.
 *
 * <p>The form value is either a freshly picked `File` — the only case producing a multipart part —
 * or a write-only placeholder string set in edit mode, which means "keep the stored file".
 */
const FileFieldController: FunctionComponent<Props> = ({
  name,
  label,
  required = false,
  acceptMimeTypes,
  filters,
  sizeLimit,
  style,
  disabled = false,
}) => {
  const { t } = useFormatter();
  const { control } = useFormContext();
  const maxSize = sizeLimit && sizeLimit > 0 ? sizeLimit : undefined;

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { onChange, value }, fieldState: { error } }) => (
        <div style={style}>
          <FileSelect
            label={label}
            required={required}
            disabled={disabled}
            accept={acceptMimeTypes ?? toAcceptMimeTypes(filters)}
            maxSize={maxSize}
            showSizeCounter={maxSize !== undefined}
            value={value instanceof File ? value : null}
            // Edit mode carries the stored file as a plain string; it names the
            // kept file instead of leaving the control looking empty.
            placeholder={typeof value === 'string' ? value : undefined}
            onValueChange={next => onChange((next as File | null) ?? undefined)}
            error={error?.message}
            rejectionMessage={(rejections: FileRejection[]) => (rejections.some(rejection => rejection.reason === 'maxSize')
              ? t('This file is too large')
              : t('This file is not in the specified format'))}
          />
        </div>
      )}
    />
  );
};

export default FileFieldController;
