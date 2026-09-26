import { Button, IconButton, Spinner, Text, Textarea } from '@filigran/design-system';
import { Clear } from '@mui/icons-material';
import { Box, Divider, List, ListItem, ListItemText } from '@mui/material';
import { useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { searchAttackPatternsWithAIWebservice } from '../../../../../actions/AttackPattern';
import Dialog from '../../../../../components/common/dialog/Dialog';
import ImportUploader from '../../../../../components/common/ImportUploader';
import { useFormatter } from '../../../../../components/i18n';
import { type AgentOption, fetchAgentsForIntent } from '../../../../../utils/ai/agentApi';
import AgentSelector from '../../../../../utils/ai/AgentSelector';
import useAI from '../../../../../utils/hooks/useAI';

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
    alignItems: 'center',
    minHeight: 36,
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
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: theme.spacing(1),
    minHeight: 200,
  },
  loaderText: {},
}));

interface Props {
  open: boolean;
  onClose: () => void;
  onAttackPatternIdsFind: (ids: string[]) => void;
}

const AttackPatternAIAssistantDialog = ({ open, onClose, onAttackPatternIdsFind }: Props) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const { xtmOneConfigured } = useAI();
  const maxFilesNumber = 5;
  // State hooks
  const [isLoading, setIsLoading] = useState(false);
  const [files, setFiles] = useState<File[]>([]);
  const [text, setText] = useState<string>('');

  // Agent selector state (only used when XTM One is configured)
  const [agentOptions, setAgentOptions] = useState<AgentOption[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<AgentOption | null>(null);
  const [loadingAgents, setLoadingAgents] = useState(false);

  useEffect(() => {
    if (!open || !xtmOneConfigured) return;
    setLoadingAgents(true);
    setSelectedAgent(null);
    setAgentOptions([]);
    fetchAgentsForIntent('cti.ttp_harvester')
      .then((agents) => {
        setAgentOptions(agents);
        if (agents.length > 0) setSelectedAgent(agents[0]);
      })
      .finally(() => setLoadingAgents(false));
  }, [open, xtmOneConfigured]);

  const onResetAndClose = () => {
    setFiles([]);
    setText('');
    onClose();
  };

  const onSubmit = async () => {
    setIsLoading(true);
    try {
      // Backend handles routing to XTM One or legacy AI webservice.
      const response = await searchAttackPatternsWithAIWebservice(
        files ?? [],
        text,
        xtmOneConfigured ? selectedAgent?.slug : undefined,
      );
      onAttackPatternIdsFind(response.data);
    } finally {
      setIsLoading(false);
      onResetAndClose();
    }
  };

  const addFile = (_: FormData, file: File) => {
    if (!files.find(f => f.name === file.name)) {
      setFiles(prevState => [...prevState ?? [], file]);
    }
  };

  const dialogTitle = xtmOneConfigured
    ? (
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          width: '100%',
          gap: 2,
        }}
        >
          <span>{t('XTM One - AI Assistant')}</span>
          <AgentSelector
            options={agentOptions}
            value={selectedAgent}
            onChange={setSelectedAgent}
            loading={loadingAgents}
            disabled={isLoading}
          />
        </Box>
      )
    : t('XTM One - AI Assistant');

  return (
    <Dialog
      open={open}
      handleClose={onResetAndClose}
      title={dialogTitle}
      maxWidth="md"
    >
      <div className={classes.modalContainer}>
        <Text variant="content-base" className={classes.allWidth}>
          {t('Let our XTM One AI assistant analyse a context to generate relevant TTP for your scenario.')}
        </Text>
        {!isLoading && (
          <>
            <Textarea
              label={t('Paste text to analyse for selected TTPs.')}
              value={text}
              onChange={e => setText(e.target.value)}
              rows={8}
            />
            <span className={classes.fileListContainer}>
              <span className={classes.filesLabel}>
                <Text variant="content-base">{t('And/or import documents (.txt .pdf)')}</Text>
                <ImportUploader
                  title="Import files"
                  handleUpload={addFile}
                  isIconButton={false}
                  fileAccepted=".pdf, .txt"
                  disabled={files.length >= maxFilesNumber}
                  allowReUpload
                />
              </span>
              <List>
                {files.map(file => (
                  <span key={file.name}>
                    <ListItem
                      dense
                      secondaryAction={(
                        <IconButton
                          icon={<Clear />}
                          aria-label="remove-file"
                          onClick={() => setFiles(files.filter(f => f.name !== file.name))}
                          priority="tertiary"
                          size="md"
                        />
                      )}
                    >
                      <ListItemText primary={file.name} />
                    </ListItem>
                    <Divider />
                  </span>
                ))}
              </List>
              {files.length > 0 && (
                <Text variant="content-compact" className="text-default-secondary" style={{ marginLeft: 'auto' }}>
                  {`${t('Files imported :')} ${files.length ?? 0} - ${maxFilesNumber}`}
                </Text>
              )}
            </span>
          </>
        )}
        {
          isLoading && (
            <div className={`${classes.allWidth} ${classes.loaderContainer}`}>
              <Spinner size="xl" />
              <Text
                variant="content-caption"
                as="div"
                className={`${classes.loaderText} text-default-secondary`}
              >
                {t('Loading AI assistant, please wait...')}
              </Text>
            </div>
          )
        }
        <div className={`${classes.buttonContainer} ${classes.allWidth}`}>
          <Button type="button" priority="secondary" onClick={onResetAndClose} disabled={isLoading} style={{ marginLeft: 'auto' }}>
            {t('Cancel')}
          </Button>
          <Button type="button" onClick={onSubmit} disabled={isLoading || (files.length === 0 && text.trim() === '')}>
            {t('Generate TTP')}
          </Button>
        </div>
      </div>
    </Dialog>
  );
};

export default AttackPatternAIAssistantDialog;
