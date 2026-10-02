import { type CSSProperties, type FunctionComponent } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import CustomFileUploader from '../common/CustomFileUploader';

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
  const { control, formState: { errors } } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { onChange, value }, fieldState: { error } }) => (
        <div style={style}>
          <CustomFileUploader
            name={name}
            label={label}
            required={required}
            disabled={disabled}
            fieldOnChange={onChange}
            acceptMimeTypes={acceptMimeTypes ?? toAcceptMimeTypes(filters)}
            sizeLimit={sizeLimit}
            errors={errors}
            errorMessage={error?.message}
            initialFileName={typeof value === 'string' ? value : undefined}
          />
        </div>
      )}
    />
  );
};

export default FileFieldController;
