import { type FunctionComponent } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import CustomFileUploader from '../common/CustomFileUploader';

interface Props {
  name: string;
  label?: string;
  required?: boolean;
  /** html input "accept", MIME types only. */
  acceptMimeTypes?: string;
  /** Maximum accepted size in bytes, 0 means no limit. */
  sizeLimit?: number;
}

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
  sizeLimit,
}) => {
  const { control, formState: { errors } } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { onChange, value }, fieldState: { error } }) => (
        <CustomFileUploader
          name={name}
          label={label}
          required={required}
          fieldOnChange={onChange}
          acceptMimeTypes={acceptMimeTypes}
          sizeLimit={sizeLimit}
          errors={errors}
          errorMessage={error?.message}
          initialFileName={typeof value === 'string' ? value : undefined}
        />
      )}
    />
  );
};

export default FileFieldController;
