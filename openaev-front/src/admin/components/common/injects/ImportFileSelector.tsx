import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';
import { z } from 'zod';

import CustomFileUploader from '../../../../components/common/CustomFileUploader';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { zodImplement } from '../../../../utils/Zod';

const useStyles = makeStyles()(() => ({
  container: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  buttons: {
    display: 'flex',
    justifyContent: 'right',
    gap: '8px',
    marginTop: '24px',
  },
}));

interface FormProps { file: File }

interface Props {
  label: string;
  mimeTypes: string;
  submitActionLabel: string;
  handleClose: () => void;
  handleSubmit: (values: FormProps) => void;
}

const ImportFileSelector: FunctionComponent<Props> = ({
  label,
  mimeTypes,
  submitActionLabel,
  handleClose,
  handleSubmit,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();

  const [loading, setLoading] = useState(false);

  // Form
  const {
    control,
    handleSubmit: handleSubmitForm,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<FormProps>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<FormProps>().with({ file: z.instanceof(File) }),
    ),
  });

  const onSubmitImportFile = (values: FormProps) => {
    setLoading(true);
    handleSubmit(values);
    setLoading(false);
  };

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmitForm(onSubmitImportFile)(e);
  };

  return (
    <>
      {loading && <Loader variant="inElement" />}
      {!loading
        && (
          <form id="importUploadInjectForm" onSubmit={handleSubmitWithoutPropagation}>
            <div className={classes.container}>
              <Controller
                control={control}
                name="file"
                render={({ field: { onChange } }) => (
                  <CustomFileUploader
                    name="file"
                    fieldOnChange={onChange}
                    label={label}
                    acceptMimeTypes={mimeTypes}
                    errors={errors}
                  />
                )}
              />
            </div>
            <div className={classes.buttons}>
              <Button
                onClick={handleClose}
                disabled={isSubmitting}
              >
                {t('Cancel')}
              </Button>
              <Button
                color="secondary"
                type="submit"
                disabled={!isDirty || isSubmitting}
              >
                {submitActionLabel}
              </Button>
            </div>
          </form>
        )}
    </>
  );
};

export default ImportFileSelector;
