import { CloudUploadOutlined, DeleteOutline } from '@mui/icons-material';
import { Box, Button, IconButton, Typography } from '@mui/material';
import { type ChangeEvent, type DragEvent, useRef, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../i18n';
import Dialog from '../dialog/Dialog';

const ACCEPTED_MIME_TYPES = new Set(['application/zip', 'application/x-zip-compressed']);

const useStyles = makeStyles()(theme => ({
  container: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(2),
    minHeight: 420,
  },
  dropArea: {
    'border': `1px dashed ${theme.palette.divider}`,
    'borderRadius': theme.spacing(2),
    'minHeight': 280,
    'display': 'flex',
    'alignItems': 'center',
    'justifyContent': 'center',
    'textAlign': 'center',
    'padding': theme.spacing(3),
    'transition': 'border-color 0.2s ease, background-color 0.2s ease',
    'cursor': 'pointer',
    'outline': 'none',
    'backgroundColor': 'transparent',
    'appearance': 'none',
    '&:hover': { backgroundColor: theme.palette.background.secondary },
    '&:disabled': {
      'cursor': 'default',
      '&:hover': { backgroundColor: 'transparent' },
    },
  },
  dropAreaActive: {
    borderColor: theme.palette.primary.main,
    backgroundColor: theme.palette.background.secondary,
  },
  dropAreaContent: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: theme.spacing(1),
  },
  actionRow: {
    display: 'flex',
    gap: theme.spacing(1),
    marginTop: theme.spacing(1),
  },
  footerButtons: {
    marginTop: 'auto',
    display: 'flex',
    justifyContent: 'flex-end',
    gap: theme.spacing(1),
  },
  fileName: { color: theme.palette.text.secondary },
  filesList: {
    borderTop: `1px solid ${theme.palette.divider}`,
    marginTop: theme.spacing(1),
  },
  filesHeader: {
    display: 'grid',
    gridTemplateColumns: '1fr auto',
    gap: theme.spacing(2),
    padding: `${theme.spacing(1.5)} ${theme.spacing(1)}`,
    color: theme.palette.text.secondary,
  },
  fileRow: {
    display: 'grid',
    gridTemplateColumns: '1fr auto',
    gap: theme.spacing(2),
    alignItems: 'center',
    padding: `${theme.spacing(1.5)} ${theme.spacing(1)}`,
  },
  fileEntry: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
  },
}));

const isZipFile = (file: File) => {
  const lowerName = file.name.toLowerCase();
  return ACCEPTED_MIME_TYPES.has(file.type) || lowerName.endsWith('.zip');
};

interface Props {
  open: boolean;
  onClose: () => void;
  onImport: (formData: FormData) => Promise<unknown>;
  maxFiles?: number;
}

const DragAndDropImportDialog = ({ open, onClose, onImport, maxFiles }: Props) => {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const { classes, cx } = useStyles();
  const { t } = useFormatter();

  const [uploading, setUploading] = useState(false);
  const [isDragActive, setIsDragActive] = useState(false);
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);

  const isLimitReached = maxFiles !== undefined && selectedFiles.length >= maxFiles;

  const handleClose = () => {
    setSelectedFiles([]);
    setIsDragActive(false);
    setUploading(false);
    onClose();
  };

  const setFileIfValid = (file: File | null | undefined) => {
    if (!file || !isZipFile(file)) {
      return;
    }
    const fileKey = `${file.name}-${file.lastModified}-${file.size}`;
    setSelectedFiles((prev) => {
      if (maxFiles !== undefined && prev.length >= maxFiles) {
        return prev;
      }
      const hasSameFile = prev.some(f => `${f.name}-${f.lastModified}-${f.size}` === fileKey);
      if (hasSameFile) {
        return prev;
      }
      return [...prev, file];
    });
  };

  const handleDragOver = (event: DragEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    setIsDragActive(true);
  };

  const handleDragLeave = (event: DragEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    setIsDragActive(false);
  };

  const handleDrop = (event: DragEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    setIsDragActive(false);
    if (!isLimitReached) {
      setFileIfValid(event.dataTransfer.files?.[0]);
    }
  };

  const removeFile = (indexToRemove: number) => {
    setSelectedFiles(prev => prev.filter((_, index) => index !== indexToRemove));
  };

  const handleFileInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    setFileIfValid(event.target.files?.[0]);
    event.target.value = '';
  };

  const handleSubmit = async () => {
    if (selectedFiles.length === 0 || uploading) {
      return;
    }
    setUploading(true);

    const importPromises = selectedFiles.map(async (file) => {
      const formData = new FormData();
      formData.append('file', file);
      await onImport(formData);
    });

    await Promise.all(importPromises);
    handleClose();
  };

  return (
    <Dialog
      open={open}
      handleClose={handleClose}
      title={t('Import data')}
    >
      <Box className={classes.container}>
        <input
          ref={inputRef}
          type="file"
          accept=".zip,application/zip,application/x-zip-compressed"
          style={{ display: 'none' }}
          onChange={handleFileInputChange}
        />

        <button
          type="button"
          tabIndex={0}
          className={cx(classes.dropArea, { [classes.dropAreaActive]: isDragActive })}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          onClick={() => !isLimitReached && inputRef.current?.click()}
          onKeyDown={(event) => {
            if (!isLimitReached && (event.key === 'Enter' || event.key === ' ')) {
              event.preventDefault();
              inputRef.current?.click();
            }
          }}
          disabled={isLimitReached}
        >
          <div className={classes.dropAreaContent}>
            <CloudUploadOutlined color="primary" />
            <Typography variant="body1">{t('Drag and drop files to import')}</Typography>
            <Typography variant="body2" className={classes.fileName}>
              {t('Threat Arsenal supports .zip file import format')}
              {maxFiles && (
                <>
                  <br />
                  {t('Maximum files allowed:')}
                  {' '}
                  {maxFiles}
                </>
              )}
            </Typography>
            <div className={classes.actionRow}>
              <Button
                variant="contained"
                disabled={isLimitReached}
                onClick={(event) => {
                  event.stopPropagation();
                  inputRef.current?.click();
                }}
              >
                {t('Browse files')}
              </Button>
            </div>
          </div>
        </button>

        {selectedFiles.length > 0 && (
          <div className={classes.filesList}>
            <div className={classes.filesHeader}>
              <Typography variant="subtitle2">{t('Files')}</Typography>
            </div>
            {selectedFiles.map((file, index) => (
              <div
                key={`${file.name}-${file.lastModified}-${file.size}`}
                className={classes.fileRow}
              >
                <div className={classes.fileEntry}>
                  <CloudUploadOutlined color="primary" fontSize="small" />
                  <Typography variant="body2">{file.name}</Typography>
                </div>
                <IconButton
                  aria-label={t('Remove file')}
                  size="small"
                  onClick={() => removeFile(index)}
                >
                  <DeleteOutline color="primary" fontSize="small" />
                </IconButton>
              </div>
            ))}
          </div>
        )}

        <div className={classes.footerButtons}>
          <Button onClick={handleClose} disabled={uploading}>
            {t('Cancel')}
          </Button>
          <Button
            color="primary"
            variant="contained"
            onClick={handleSubmit}
            disabled={selectedFiles.length === 0 || uploading}
          >
            {t('Import')}
          </Button>
        </div>
      </Box>
    </Dialog>
  );
};

export default DragAndDropImportDialog;
