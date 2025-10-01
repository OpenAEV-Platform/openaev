import { CloudUploadOutlined } from '@mui/icons-material';
import { Button, CircularProgress, type CircularProgressProps, IconButton, ToggleButton, Tooltip } from '@mui/material';
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
  allowReUpload = false,
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
      <Tooltip
        title={`Uploading ${upload}`}
        aria-label={`Uploading ${upload}`}
      >
        <IconButton disabled={true} style={{ marginRight: 10 }}>
          <CircularProgress
            size={24}
            thickness={2}
            color={color ?? 'primary'}
          />
        </IconButton>
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
        <ToggleButton
          value="import"
          aria-label="import"
          size="small"
          onClick={handleOpenUpload}
          disabled={disabled}
        >
          <Tooltip
            title={t(title)}
            aria-label={title}
          >
            <CloudUploadOutlined
              color="primary"
              fontSize="small"
            />
          </Tooltip>
        </ToggleButton>
      ) : (
        <Tooltip
          title={t(title)}
          aria-label={title}
        >
          <Button
            onClick={handleOpenUpload}
            disabled={disabled}
            size="small"
            variant="outlined"
            color="inherit"
            className={classes.buttonImport}
          >
            {t('Import')}
          </Button>
        </Tooltip>
      )}
    </>
  );
};

export default ImportUploader;
