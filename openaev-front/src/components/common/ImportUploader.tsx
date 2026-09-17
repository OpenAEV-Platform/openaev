import { Button, IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CloudUploadOutlined } from '@mui/icons-material';
import { CircularProgress, type CircularProgressProps } from '@mui/material';
import { type ChangeEvent, type FunctionComponent, useRef, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../i18n';

const useStyles = makeStyles()(theme => ({ buttonImport: { borderColor: theme.palette.divider } }));

interface Props {
  title: string;
  handleUpload: (formData: FormData, file: File) => void;
  color?: CircularProgressProps['color'];
  isIconButton?: boolean;
  fileAccepted?: string;
  allowReUpload?: boolean;
  disabled?: boolean;
}

const ImportUploader: FunctionComponent<Props> = ({
  title,
  handleUpload,
  color,
  isIconButton = true,
  fileAccepted = '',
  allowReUpload = true,
  disabled = false,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const uploadRef = useRef<HTMLInputElement | null>(null);
  const [upload, setUpload] = useState(false);
  const handleOpenUpload = () => uploadRef.current && uploadRef.current.click();

  const onUpload = async (file: File) => {
    setUpload(true);
    const formData = new FormData();
    formData.append('file', file);
    handleUpload(formData, file);
    setUpload(false);
  };

  if (upload) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="inline-flex">
            <IconButton
              icon={<CircularProgress size={24} thickness={2} color={color ?? 'primary'} />}
              aria-label={t('Import')}
              disabled={true}
              style={{ marginRight: 10 }}
              priority="tertiary"
              size="md"
            />
          </span>
        </TooltipTrigger>
        <TooltipContent>{`Uploading ${upload}`}</TooltipContent>
      </Tooltip>
    );
  }

  return (
    <>
      <input
        ref={uploadRef}
        type="file"
        style={{ display: 'none' }}
        accept={fileAccepted}
        onChange={(event: ChangeEvent<HTMLInputElement>) => {
          const target = event.target as HTMLInputElement;
          const file: File = (target.files as FileList)[0];
          if (target.validity.valid) {
            onUpload(file);
          }
          if (allowReUpload) {
            event.target.value = '';
          }
        }}
      />
      {isIconButton ? (
        <Tooltip>
          <TooltipTrigger asChild>
            <span style={{ display: 'inline-flex' }}>
              <IconButton
                priority="secondary"
                size="md"
                aria-label={t(title)}
                icon={<CloudUploadOutlined fontSize="small" />}
                onClick={handleOpenUpload}
                disabled={disabled}
              />
            </span>
          </TooltipTrigger>
          <TooltipContent>{t(title)}</TooltipContent>
        </Tooltip>
      ) : (
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="inline-flex">
              <Button priority="secondary" onClick={handleOpenUpload} disabled={disabled} className={classes.buttonImport}>
                {t('Import')}
              </Button>
            </span>
          </TooltipTrigger>
          <TooltipContent>{t(title)}</TooltipContent>
        </Tooltip>
      )}
    </>
  );
};

export default ImportUploader;
