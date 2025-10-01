import { Clear } from '@mui/icons-material';
import {
  Button,
  CircularProgress, Divider,
  IconButton,
  List,
  ListItem,
  ListItemText, TextField,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { searchAttackPatternsWithAIWebservice } from '../../../../../actions/AttackPattern';
import Dialog from '../../../../../components/common/dialog/Dialog';
import ImportUploader from '../../../../../components/common/ImportUploader';
import { useFormatter } from '../../../../../components/i18n';

const useStyles = makeStyles()(theme => ({
  modalContainer: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: theme.spacing(1, 2),
  },
  textLabel: { alignSelf: 'end' },
  filesLabel: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'end',
  },
  allWidth: { gridColumn: 'span 2' },
  fileListContainer: {
    display: 'flex',
    flexDirection: 'column',
  },
  buttonContainer: {
    display: 'flex',
    gap: theme.spacing(1),
  },
  loaderContainer: {
    textAlign: 'center',
    position: 'relative',
    overflow: 'hidden',
  },
  loaderText: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
  },
}));

interface Props {
  open: boolean;
  onClose: () => void;
  onAttackPatternIdsFind: (ids: string[]) => void;
}

const AttackPatternAIAssistantDialog = ({ open, onClose, onAttackPatternIdsFind }: Props) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const maxFilesNumber = 5;
  // State hooks
  const [isLoading, setIsLoading] = useState(false);
  const [files, setFiles] = useState<File[]>([]);
  const [text, setText] = useState<string>('');

  const onResetAndClose = () => {
    setFiles([]);
    setText('');
    onClose();
  };

  const onSubmit = () => {
    setIsLoading(true);
    searchAttackPatternsWithAIWebservice(files ?? [], text)
      .then(response => onAttackPatternIdsFind(response.data))
      .finally(() => {
        setIsLoading(false);
        onResetAndClose();
      });
  };

  const addFile = (_: FormData, file: File) => {
    if (!files.find(f => f.name === file.name)) {
      setFiles(prevState => [...prevState ?? [], file]);
    }
  };

  return (
    <Dialog
      open={open}
      handleClose={onResetAndClose}
      title={t('ARIANE - AI Assistant')}
      maxWidth="md"
    >
      <div className={classes.modalContainer}>
        <Typography className={classes.allWidth}>
          {t('Let Ariane our AI assistant analyse a context to generate relevant TTP for your scenario.')}
        </Typography>
        {!isLoading && (
          <>
            <Typography className={classes.textLabel} variant="h3" gutterBottom>{t('Paste text to analyse for selected TTPs.')}</Typography>
            <span className={classes.filesLabel}>
              <Typography variant="h3" gutterBottom>{t('And/or import documents (.txt .pdf)')}</Typography>
              <ImportUploader
                title="Import files"
                handleUpload={addFile}
                isIconButton={false}
                fileAccepted=".pdf, .txt"
                disabled={files.length >= maxFilesNumber}
                allowReUpload
              />
            </span>
            <TextField
              value={text}
              onChange={e => setText(e.target.value)}
              multiline
              variant="outlined"
              rows={8}
            />
            <span className={classes.fileListContainer}>
              <List>
                {files.map(file => (
                  <span key={file.name}>
                    <ListItem
                      dense
                      secondaryAction={(
                        <IconButton
                          size="small"
                          aria-label="remove-file"
                          onClick={() => setFiles(files.filter(f => f.name !== file.name))}
                        >
                          <Clear />
                        </IconButton>
                      )}
                    >
                      <ListItemText primary={file.name} />
                    </ListItem>
                    <Divider />
                  </span>
                ))}
              </List>
              {files.length > 0 && (
                <Typography variant="h3" gutterBottom style={{ marginLeft: 'auto' }}>
                  {`${t('Files imported :')} ${files.length ?? 0} - ${maxFilesNumber}`}
                </Typography>
              )}
            </span>
          </>
        )}
        {
          isLoading && (
            <div className={`${classes.allWidth} ${classes.loaderContainer}`}>
              <CircularProgress size={200} thickness={0.3} />
              <Typography
                variant="caption"
                component="div"
                color="text.secondary"
                className={classes.loaderText}
              >
                {t('Loading AI assistant, please wait...')}
              </Typography>
            </div>
          )
        }
        <div className={`${classes.buttonContainer} ${classes.allWidth}`}>
          <Button
            variant="contained"
            style={{ marginLeft: 'auto' }}
            onClick={onResetAndClose}
            disabled={isLoading}
          >
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="secondary"
            onClick={onSubmit}
            disabled={isLoading || (files.length === 0 && text.trim() === '')}
          >
            {t('Generate TTP')}
          </Button>
        </div>
      </div>
    </Dialog>
  );
};

export default AttackPatternAIAssistantDialog;
