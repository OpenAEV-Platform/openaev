import { CloudUploadOutlined, DeleteOutline } from '@mui/icons-material';
import { Box, Button, IconButton, ToggleButton, Tooltip, Typography } from '@mui/material';
import { type ChangeEvent, type ClipboardEvent, type DragEvent, type FunctionComponent, useRef, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { importPayload } from '../../../actions/payloads/payload-actions';
import Dialog from '../../../components/common/dialog/Dialog';
import { useFormatter } from '../../../components/i18n';
import { type InjectorContractActionOutput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';

const ACCEPTED_MIME_TYPES = new Set(['application/zip', 'application/x-zip-compressed']);

const useStyles = makeStyles()(theme => ({
  container: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
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
    borderTop: `1px solid ${theme.palette.divider}`,
  },
  fileEntry: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
  },
}));

interface Props { onImport?: (results: InjectorContractActionOutput[]) => void }

function isZipFile(file: File): boolean {
  const lowerName = file.name.toLowerCase();
  return ACCEPTED_MIME_TYPES.has(file.type) || lowerName.endsWith('.zip');
}

const ImportUploaderThreatArsenal: FunctionComponent<Props> = ({ onImport }) => {
  const { t } = useFormatter();
  const { classes, cx } = useStyles();
  const dispatch = useAppDispatch();

  const [open, setOpen] = useState(false);
  const [isDragActive, setIsDragActive] = useState(false);
  const [pasteMode, setPasteMode] = useState(false);
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const handleOpen = () => setOpen(true);
  const handleClose = () => {
    setOpen(false);
    setSelectedFiles([]);
    setPasteMode(false);
    setIsDragActive(false);
    setUploading(false);
  };

  const setFileIfValid = (file: File | null | undefined) => {
    if (!file || !isZipFile(file)) {
      return;
    }
    const fileKey = `${file.name}-${file.lastModified}-${file.size}`;
    setSelectedFiles((prev) => {
      const hasSameFile = prev.some(f => `${f.name}-${f.lastModified}-${f.size}` === fileKey);
      if (hasSameFile) {
        return prev;
      }
      return [...prev, file];
    });
  };

  const removeFile = (indexToRemove: number) => {
    setSelectedFiles(prev => prev.filter((_, index) => index !== indexToRemove));
  };

  const handleFileInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    setFileIfValid(event.target.files?.[0]);
    event.target.value = '';
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
    setFileIfValid(event.dataTransfer.files?.[0]);
  };

  const handlePaste = (event: ClipboardEvent<HTMLButtonElement>) => {
    if (!pasteMode) {
      return;
    }
    const pastedFile = Array.from(event.clipboardData.items)
      .map(item => item.getAsFile())
      .find((file): file is File => file !== null);
    setFileIfValid(pastedFile);
  };

  const handleSubmit = async () => {
    if (selectedFiles.length === 0 || uploading) {
      return;
    }
    setUploading(true);

    const importedResults: InjectorContractActionOutput[] = [];
    for (const file of selectedFiles) {
      const formData = new FormData();
      formData.append('file', file);
      const result = await dispatch(importPayload(formData)) as { data?: InjectorContractActionOutput[] };
      if (result?.data) {
        importedResults.push(...result.data);
      }
    }

    if (importedResults.length > 0 && onImport) {
      onImport(importedResults);
    }
    handleClose();
  };

  return (
    <>
      <ToggleButton
        value="import"
        aria-label="import payloads"
        size="small"
        onClick={handleOpen}
      >
        <Tooltip title={t('Import payloads')} aria-label="import payloads">
          <CloudUploadOutlined color="primary" fontSize="small" />
        </Tooltip>
      </ToggleButton>

      <Dialog
        open={open}
        handleClose={handleClose}
        title={t('Import data')}
        size="large"
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
            className={cx(classes.dropArea, { [classes.dropAreaActive]: isDragActive || pasteMode })}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            onPaste={handlePaste}
            onClick={() => inputRef.current?.click()}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                inputRef.current?.click();
              }
            }}
          >
            <div className={classes.dropAreaContent}>
              <CloudUploadOutlined color="primary" />
              <Typography variant="body1">{t('Drag and drop files to import')}</Typography>
              <Typography variant="body2" className={classes.fileName}>
                {t('Threat Arsenal supports .zip file import format')}
              </Typography>
              {selectedFiles.length > 0 && (
                <Typography variant="body2" className={classes.fileName}>
                  {selectedFiles.map(file => file.name).join(', ')}
                </Typography>
              )}
              <div className={classes.actionRow}>
                <Button
                  variant="contained"
                  onClick={(event) => {
                    event.stopPropagation();
                    inputRef.current?.click();
                  }}
                >
                  {t('Browse files')}
                </Button>
                <Button
                  variant={pasteMode ? 'contained' : 'outlined'}
                  onClick={(event) => {
                    event.stopPropagation();
                    setPasteMode(prev => !prev);
                  }}
                >
                  {t('Copy/paste mode')}
                </Button>
              </div>
            </div>
          </button>

          {selectedFiles.length > 0 && (
            <div className={classes.filesList}>
              <div className={classes.filesHeader}>
                <Typography variant="subtitle2">{t('Files')}</Typography>
                <DeleteOutline color="primary" fontSize="small" />
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
              {t('Next')}
            </Button>
          </div>
        </Box>
      </Dialog>
    </>
  );
};

export default ImportUploaderThreatArsenal;
