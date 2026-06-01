import { RefreshOutlined } from '@mui/icons-material';
import { LoadingButton } from '@mui/lab';
import { Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, IconButton, InputLabel, MenuItem, Select, TextField } from '@mui/material';
// As we can ask AI after and follow up, there is a dependency lifecycle here that can be accepted
// TODO: Cleanup a bit in upcoming version
// eslint-disable-next-line import/no-cycle
import MDEditor, { commands } from '@uiw/react-md-editor/nohighlight';
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore
import { type FunctionComponent, useEffect, useRef, useState } from 'react';

// eslint-disable-next-line import/no-cycle
import TextFieldAskAI, { type AgentAction } from '../../admin/components/common/form/TextFieldAskAI';
import CKEditor from '../../components/CKEditor';
import { useFormatter } from '../../components/i18n';
import { isNotEmptyField } from '../utils';
import { type AgentOption, fetchAgentsForIntent } from './agentApi';
import AgentSelector from './AgentSelector';
import useAgentStream from './useAgentStream';

export interface AgentMode {
  intent: string;
  action: AgentAction;
  inputContent: string;
  format: string;
}

// region types
interface ResponseDialogProps {
  isOpen: boolean;
  isDisabled: boolean;
  handleClose: () => void;
  handleAccept: (content: string) => void;
  handleFollowUp: () => void;
  content: string;
  setContent: (content: string) => void;
  format: 'text' | 'html' | 'markdown' | 'json';
  isAcceptable?: boolean;
  followUpActions: {
    key: string;
    label: string;
  }[];
  agentMode?: AgentMode | null;
}

const buildPrompt = (
  action: AgentAction,
  inputContent: string,
  format: string,
  tone?: string,
): string => {
  switch (action) {
    case 'spelling':
      return `Fix the spelling and grammar of the following content. Keep the same ${format} format. Return only the corrected result, no explanation.\n\n${inputContent}`;
    case 'shorter':
      return `Make the following content shorter. Keep the same ${format} format. Return only the result, no explanation.\n\n${inputContent}`;
    case 'longer':
      return `Make the following content longer and more detailed. Keep the same ${format} format. Return only the result, no explanation.\n\n${inputContent}`;
    case 'tone':
      return `Change the tone of the following content to be more ${tone ?? 'formal'}. Keep the same ${format} format. Return only the result, no explanation.\n\n${inputContent}`;
    case 'summarize':
      return `Summarize the following content. Keep the same ${format} format. Return only the summary, no explanation.\n\n${inputContent}`;
    case 'explain':
      return `Explain the following content in simple terms. Return only the explanation.\n\n${inputContent}`;
    default:
      return inputContent;
  }
};

const ResponseDialog: FunctionComponent<ResponseDialogProps> = ({
  isOpen,
  isDisabled,
  handleClose,
  setContent,
  handleAccept,
  format,
  isAcceptable = true,
  content,
  agentMode = null,
}) => {
  const textFieldRef = useRef<HTMLTextAreaElement>(null);
  const markdownFieldRef = useRef<HTMLTextAreaElement>(null);
  const { t } = useFormatter();
  const isLegacyMode = !agentMode;

  // Agent mode state
  const [agentOptions, setAgentOptions] = useState<AgentOption[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<AgentOption | null>(null);
  const [loadingAgents, setLoadingAgents] = useState(false);
  const [agentExecuted, setAgentExecuted] = useState(false);
  const [tone, setTone] = useState<string>('formal');

  // Agent streaming hook
  const { content: streamContent, loading: agentLoading, error: agentError, execute: executeStream, abort: abortStream } = useAgentStream();

  // Sync streamed content to parent.
  // We propagate empty strings as well so the parent UI clears immediately when a stream
  // (re-)starts; this keeps the loading spinner condition (`agentLoading && !content`) and the
  // empty-state behaviour correct. We only do this in agent mode to avoid clobbering the
  // controlled `content` prop in the legacy mode (where `streamContent` stays at its initial
  // empty value).
  useEffect(() => {
    if (!agentMode) return;
    setContent(streamContent);
  }, [streamContent, setContent, agentMode]);

  // Load agents when dialog opens in agent mode
  useEffect(() => {
    if (isOpen && agentMode) {
      setAgentExecuted(false);
      abortStream();
      setLoadingAgents(true);
      setSelectedAgent(null);
      setAgentOptions([]);
      fetchAgentsForIntent(agentMode.intent)
        .then((agents) => {
          setAgentOptions(agents);
          if (agents.length > 0) {
            setSelectedAgent(agents[0]);
          }
        })
        .finally(() => setLoadingAgents(false));
    }
    if (!isOpen) {
      setAgentExecuted(false);
      abortStream();
    }
  }, [isOpen, agentMode?.intent, agentMode?.action]);

  const executeAgentCall = () => {
    if (!selectedAgent || !agentMode) return;
    setAgentExecuted(true);
    const prompt = buildPrompt(agentMode.action, agentMode.inputContent, agentMode.format, tone);
    executeStream(selectedAgent.slug, prompt);
  };

  // Auto-execute when agent is selected
  useEffect(() => {
    if (isOpen && agentMode && selectedAgent && !agentExecuted && !agentLoading) {
      executeAgentCall();
    }
  }, [selectedAgent, isOpen, agentMode, agentExecuted, agentLoading]);

  // Re-execute when tone changes (for change.tone action)
  const toneRef = useRef(tone);
  useEffect(() => {
    if (toneRef.current !== tone && isOpen && agentMode && selectedAgent && agentExecuted) {
      toneRef.current = tone;
      executeAgentCall();
    }
  }, [tone]);

  const handleRefresh = () => {
    if (!selectedAgent || !agentMode) return;
    setContent('');
    setAgentExecuted(false);
  };

  const handleAgentChange = (_event: unknown, newValue: AgentOption | null) => {
    if (!newValue) return;
    // Abort any in-flight stream from the previously selected agent so its chunks can no longer
    // repopulate the cleared content under the newly-selected agent (the AgentSelector is also
    // disabled while `agentLoading` is true as a defensive guard).
    abortStream();
    setSelectedAgent(newValue);
    if (agentMode) {
      setAgentExecuted(false);
      setContent('');
    }
  };

  useEffect(() => {
    if (format === 'text' || format === 'json') {
      if (isNotEmptyField(textFieldRef?.current?.scrollTop)) {
        textFieldRef.current.scrollTop = textFieldRef.current.scrollHeight;
      }
    } else if (format === 'markdown') {
      if (isNotEmptyField(markdownFieldRef?.current?.scrollTop)) {
        markdownFieldRef.current.scrollTop = markdownFieldRef.current.scrollHeight;
      }
    } else if (format === 'html') {
      const elementCkEditor = document.querySelector(
        '.ck-content.ck-editor__editable.ck-editor__editable_inline',
      );
      elementCkEditor?.lastElementChild?.scrollIntoView();
    }
  }, [content]);

  const height = 400;
  const effectiveDisabled = isDisabled || agentLoading;
  const noAgents = agentMode && !loadingAgents && agentOptions.length === 0;

  const dialogTitle = agentMode
    ? (
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          width: '100%',
          gap: 2,
        }}
        >
          <span>{t('Ask AI')}</span>
          <AgentSelector
            options={agentOptions}
            value={selectedAgent}
            onChange={newValue => handleAgentChange(null, newValue)}
            loading={loadingAgents}
            disabled={agentLoading}
          />
        </Box>
      )
    : t('Ask AI');

  const renderContentEditors = () => (
    <>
      {(format === 'text' || format === 'json') && (
        <TextField
          inputRef={textFieldRef}
          disabled={effectiveDisabled}
          rows={Math.round(height / 23)}
          value={content}
          multiline={true}
          onChange={event => setContent(event.target.value)}
          fullWidth={true}
          InputProps={isLegacyMode ? {
            endAdornment: (
              <TextFieldAskAI
                currentValue={content}
                setFieldValue={(val: string) => {
                  setContent(val);
                }}
                format="text"
                variant="text"
                disabled={isDisabled}
              />
            ),
          } : undefined}
        />
      )}
      {format === 'html' && (
        <CKEditor
          id="response-dialog-editor"
          data={content}
          onChange={(_, editor) => {
            // eslint-disable-next-line @typescript-eslint/ban-ts-comment
            // @ts-ignore
            setContent(editor.getData());
          }}
          disabled={effectiveDisabled}
          disableWatchdog={true}
        />
      )}
      { format === 'markdown' && (
        <MDEditor
          value={content}
          textareaProps={{ disabled: effectiveDisabled }}
          preview="edit"
          onChange={data => setContent(data ?? '')}
          commands={[
            {
              ...commands.title,
              buttonProps: { disabled: effectiveDisabled },
            },
            {
              ...commands.bold,
              buttonProps: { disabled: effectiveDisabled },
            },
            {
              ...commands.italic,
              buttonProps: { disabled: effectiveDisabled },
            },
            {
              ...commands.strikethrough,
              buttonProps: { disabled: effectiveDisabled },
            },
            { ...commands.divider },
            {
              ...commands.link,
              buttonProps: { disabled: effectiveDisabled },
            },
            {
              ...commands.quote,
              buttonProps: { disabled: effectiveDisabled },
            },
            {
              ...commands.code,
              buttonProps: { disabled: effectiveDisabled },
            },
            {
              ...commands.image,
              buttonProps: { disabled: effectiveDisabled },
            },
            {
              ...commands.divider,
              buttonProps: { disabled: effectiveDisabled },
            },
            {
              ...commands.unorderedListCommand,
              buttonProps: { disabled: effectiveDisabled },
            },
            {
              ...commands.orderedListCommand,
              buttonProps: { disabled: effectiveDisabled },
            },
            {
              ...commands.checkedListCommand,
              buttonProps: { disabled: effectiveDisabled },
            },
          ]}
          extraCommands={[]}
        />
      )}
      {isLegacyMode && (format === 'markdown' || format === 'html') && (
        <TextFieldAskAI
          currentValue={content ?? ''}
          setFieldValue={(val) => {
            setContent(val);
          }}
          format={format}
          variant={format}
          disabled={isDisabled}
          style={format === 'html' ? {
            position: 'absolute',
            top: 40,
            right: 18,
          } : undefined}
        />
      )}
    </>
  );

  return (
    <>
      <Dialog
        PaperProps={{ elevation: 1 }}
        open={isOpen}
        onClose={() => {
          setContent('');
          handleClose();
        }}
        fullWidth={true}
        maxWidth="lg"
      >
        <DialogTitle>{dialogTitle}</DialogTitle>
        <DialogContent>
          {/* Agent mode: tone selector */}
          {agentMode?.action === 'tone' && (
            <Box sx={{ mb: 2 }}>
              <FormControl size="small" fullWidth>
                <InputLabel id="tone-label">{t('Tone')}</InputLabel>
                <Select
                  labelId="tone-label"
                  label={t('Tone')}
                  value={tone}
                  onChange={event => setTone(event.target.value)}
                  size="small"
                >
                  <MenuItem value="formal">{t('Formal')}</MenuItem>
                  <MenuItem value="informal">{t('Informal')}</MenuItem>
                  <MenuItem value="authoritative">{t('Authoritative')}</MenuItem>
                  <MenuItem value="assertive">{t('Assertive')}</MenuItem>
                  <MenuItem value="critical">{t('Critical')}</MenuItem>
                </Select>
              </FormControl>
            </Box>
          )}

          <div style={{
            width: '100%',
            minHeight: height,
            height,
            position: 'relative',
          }}
          >
            {agentMode && (
              <>
                {/* Refresh button */}
                <IconButton
                  size="small"
                  onClick={handleRefresh}
                  disabled={agentLoading || !selectedAgent}
                  sx={{
                    position: 'absolute',
                    top: 2,
                    right: 2,
                    zIndex: 1,
                  }}
                >
                  <RefreshOutlined fontSize="small" />
                </IconButton>

                {((agentLoading && !content) || loadingAgents) && (
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    height: '100%',
                  }}
                  >
                    <CircularProgress size={40} />
                  </Box>
                )}

                {noAgents && !agentLoading && (
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    height: '100%',
                  }}
                  >
                    <Alert severity="info" variant="outlined">
                      {t('No agent available for this action. Ask your administrator to configure XTM One.')}
                    </Alert>
                  </Box>
                )}

                {agentError && !agentLoading && (
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    height: '100%',
                  }}
                  >
                    <Alert severity="error" variant="outlined">
                      {agentError}
                    </Alert>
                  </Box>
                )}

                {(!agentLoading || content) && !loadingAgents && !noAgents && !agentError && renderContentEditors()}
              </>
            )}

            {/* Legacy mode: always show content editors */}
            {isLegacyMode && renderContentEditors()}
          </div>
          <div className="clearfix" />
          {isLegacyMode && (
            <Alert severity="warning" variant="outlined" style={format === 'html' ? { marginTop: 30 } : {}}>
              {t('Generative AI is a beta feature as we are currently fine-tuning our models. Consider checking important information.')}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>
            {t('Close')}
          </Button>
          {isAcceptable && (
            <LoadingButton loading={effectiveDisabled} color="secondary" disabled={!!agentError} onClick={() => handleAccept(content)}>
              {t('Accept')}
            </LoadingButton>
          )}
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ResponseDialog;
