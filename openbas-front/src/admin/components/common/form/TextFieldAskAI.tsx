import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  Menu,
  MenuItem,
  Select,
  SvgIcon,
  TextField,
  Tooltip,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useState } from 'react';

import { aiChangeTone, aiExplain, aiFixSpelling, aiGenMedia, aiGenMessage, aiGenSubject, aiMakeLonger, aiMakeShorter, aiSummarize } from '../../../../actions/AskAI';
// eslint-disable-next-line import/no-cycle
import SimpleRichTextField from '../../../../components/fields/SimpleRichTextField';
import { useFormatter } from '../../../../components/i18n';
// eslint-disable-next-line import/no-cycle
import ResponseDialog from '../../../../utils/ai/ResponseDialog';
import useAI from '../../../../utils/hooks/useAI';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import EETooltip from '../entreprise_edition/EETooltip';

// region types
interface TextFieldAskAiProps {
  currentValue: string;
  setFieldValue: (value: string) => void;
  format: 'text' | 'html' | 'markdown';
  variant: 'markdown' | 'html' | 'ckeditor' | 'text' | null;
  disabled?: boolean;
  style?: object;
  inInject?: boolean;
  context?: string;
  inArticle?: boolean;
}

const TextFieldAskAI: FunctionComponent<TextFieldAskAiProps> = ({
  currentValue,
  setFieldValue,
  variant,
  format = 'text',
  disabled,
  style,
  inInject,
  context,
  inArticle,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const { enabled, configured } = useAI();
  const [content, setContent] = useState('');
  const [disableResponse, setDisableResponse] = useState(false);
  const [openToneOptions, setOpenToneOptions] = useState(false);
  const [openGenMessageOptions, setOpenGenMessageOptions] = useState(false);
  const [openGenMediaOptions, setOpenGenMediaOptions] = useState(false);
  const [messageContext, setMessageContext] = useState<string>(context ?? '');
  const [messageInput, setMessageInput] = useState<string>(currentValue);
  const [messageParagraphs, setMessageParagraphs] = useState<number>(5);
  const [messageTone, setMessageTone] = useState<'informal' | 'formal' | 'assertive' | 'sarcastic' | 'authoritative' | 'bitter' | 'critical' | 'arrogant' | 'aggressive'>('formal');
  const [messageSender, setMessageSender] = useState<string>('');
  const [messageRecipient, setMessageRecipient] = useState<string>('');
  const [isAcceptable, setIsAcceptable] = useState(true);
  const [menuOpen, setMenuOpen] = useState<{
    open: boolean;
    anchorEl: HTMLButtonElement | null;
  }>({
    open: false,
    anchorEl: null,
  });
  const [displayAskAI, setDisplayAskAI] = useState(false);
  const handleOpenMenu = (event: ReactMouseEvent<HTMLButtonElement, MouseEvent>) => {
    if (isEnterpriseEdition) {
      event.preventDefault();
      setMenuOpen({
        open: true,
        anchorEl: event.currentTarget,
      });
    }
  };
  const handleCloseMenu = () => {
    setMenuOpen({
      open: false,
      anchorEl: null,
    });
  };
  const handleOpenToneOptions = () => {
    handleCloseMenu();
    setOpenToneOptions(true);
  };
  const handleCloseToneOptions = () => setOpenToneOptions(false);
  const handleOpenGenMessageOptions = () => {
    handleCloseMenu();
    setOpenGenMessageOptions(true);
    setMessageInput(currentValue);
  };
  const handleOpenGenMediaOptions = () => {
    handleCloseMenu();
    setOpenGenMediaOptions(true);
    setMessageInput(currentValue);
  };
  const handleCloseGenMessageOptions = () => setOpenGenMessageOptions(false);
  const handleCloseGenMediaOptions = () => setOpenGenMediaOptions(false);
  const handleOpenAskAI = () => setDisplayAskAI(true);
  const handleCloseAskAI = () => setDisplayAskAI(false);
  const askFixSpelling = async () => aiFixSpelling(currentValue, format, (data: string) => setContent(data));
  const askMakeShorter = async () => aiMakeShorter(currentValue, format, (data: string) => setContent(data));
  const askMakeLonger = async () => aiMakeLonger(currentValue, format, (data: string) => setContent(data));
  const askChangeTone = async () => aiChangeTone(currentValue, messageTone, format, (data: string) => setContent(data));
  const askSummarize = async () => aiSummarize(currentValue, format, (data: string) => setContent(data));
  const askExplain = async () => aiExplain(currentValue, (data: string) => setContent(data));
  const askGenMessage = async () => aiGenMessage(
    messageContext,
    messageInput,
    messageParagraphs,
    messageTone,
    messageSender,
    messageRecipient,
    format,
    (data: string) => setContent(data),
  );
  const askGenSubject = async () => aiGenSubject(
    messageContext,
    messageInput,
    messageParagraphs,
    messageTone,
    messageSender,
    messageRecipient,
    format,
    (data: string) => setContent(data),
  );
  const askGenMedia = async () => aiGenMedia(
    messageContext,
    messageInput,
    messageParagraphs,
    messageTone,
    '',
    format,
    (data: string) => setContent(data),
  );

  const handleAskAi = async (action: string, canBeAccepted = true) => {
    setDisableResponse(true);
    handleCloseMenu();
    setIsAcceptable(canBeAccepted);
    handleOpenAskAI();
    switch (action) {
      case 'spelling':
        await askFixSpelling();
        break;
      case 'shorter':
        await askMakeShorter();
        break;
      case 'longer':
        await askMakeLonger();
        break;
      case 'tone':
        await askChangeTone();
        break;
      case 'summarize':
        await askSummarize();
        break;
      case 'explain':
        await askExplain();
        break;
      case 'genMessage':
        await askGenMessage();
        break;
      case 'genSubject':
        await askGenSubject();
        break;
      case 'genMedia':
        await askGenMedia();
        break;
      default:
        // do nothing
        break;
    }
    setDisableResponse(false);
  };

  const isContentEmpty = () => {
    return currentValue.length === 0;
  };

  const renderButton = () => {
    const isAvailable = isEnterpriseEdition && enabled && configured;
    return (
      <>
        <EETooltip
          forAi
          title={`${t('Ask AI')}${!isAvailable ? ' (EE)' : ''}`}
        >
          <span>
            <IconButton
              size="medium"
              onClick={event =>
                (isAvailable ? handleOpenMenu(event) : null)}
              disabled={disabled || !isAvailable}
              style={{
                marginTop: -4,
                color: isAvailable
                  ? theme.palette.ai.main
                  : theme.palette.action.disabled,
              }}
            >
              <SvgIcon component={LogoXtmOneIcon} fontSize="small" inheritViewBox />
            </IconButton>
          </span>
        </EETooltip>
        <Menu
          id="menu-appbar"
          anchorEl={menuOpen.anchorEl}
          open={menuOpen.open}
          onClose={handleCloseMenu}
        >
          {inInject && (
            <MenuItem onClick={handleOpenGenMessageOptions}>
              {t('Generate a message')}
            </MenuItem>
          )}
          {inArticle && (
            <MenuItem onClick={handleOpenGenMediaOptions}>
              {t('Generate an article')}
            </MenuItem>
          )}
          <Tooltip title={isContentEmpty() ? t('Content should not be empty') : ''} placement="left">
            <div>
              <MenuItem onClick={() => handleAskAi('spelling')} disabled={isContentEmpty()}>
                {t('Fix spelling & grammar')}
              </MenuItem>
            </div>
          </Tooltip>
          <Tooltip title={isContentEmpty() ? t('Content should not be empty') : ''} placement="left">
            <div>
              <MenuItem onClick={() => handleAskAi('shorter')} disabled={isContentEmpty()}>
                {t('Make it shorter')}
              </MenuItem>
            </div>
          </Tooltip>
          <Tooltip title={isContentEmpty() ? t('Content should not be empty') : ''} placement="left">
            <div>
              <MenuItem onClick={() => handleAskAi('longer')} disabled={isContentEmpty()}>
                {t('Make it longer')}
              </MenuItem>
            </div>
          </Tooltip>
          <Tooltip title={isContentEmpty() ? t('Content should not be empty') : ''} placement="left">
            <div>
              <MenuItem onClick={handleOpenToneOptions} disabled={isContentEmpty()}>
                {t('Change tone')}
              </MenuItem>
            </div>
          </Tooltip>
          <Tooltip title={isContentEmpty() ? t('Content should not be empty') : ''} placement="left">
            <div>
              <MenuItem onClick={() => handleAskAi('summarize')} disabled={isContentEmpty()}>
                {t('Summarize')}
              </MenuItem>
            </div>
          </Tooltip>
          <Tooltip title={isContentEmpty() ? t('Content should not be empty') : ''} placement="left">
            <div>
              <MenuItem onClick={() => handleAskAi('explain', false)} disabled={isContentEmpty()}>
                {t('Explain')}
              </MenuItem>
            </div>
          </Tooltip>
        </Menu>
        <ResponseDialog
          isDisabled={disableResponse}
          isOpen={displayAskAI}
          handleClose={handleCloseAskAI}
          content={content}
          setContent={setContent}
          handleAccept={(value) => {
            setFieldValue(value);
            handleCloseAskAI();
          }}
          handleFollowUp={handleCloseAskAI}
          followUpActions={[{
            key: 'retry',
            label: t('Retry'),
          }]}
          format={format}
          isAcceptable={isAcceptable}
        />
        <Dialog
          PaperProps={{ elevation: 1 }}
          open={openGenMessageOptions}
          onClose={handleCloseGenMessageOptions}
          fullWidth={true}
          maxWidth="xs"
        >
          <DialogTitle>{t('Select options')}</DialogTitle>
          <DialogContent>
            <SimpleRichTextField
              label={t('Input (describe what you want)')}
              value={messageInput}
              onChange={(value: string) => setMessageInput(value)}
              style={{ height: 200 }}
            />
            <FormControl style={{
              width: '100%',
              marginTop: 20,
            }}
            >
              <InputLabel id="messageTone">{t('Tone')}</InputLabel>
              <Select
                labelId="messageTone"
                value={messageTone}
                onChange={event => setMessageTone(event.target.value as unknown as 'informal' | 'formal' | 'assertive' | 'sarcastic' | 'authoritative' | 'bitter' | 'critical' | 'arrogant' | 'aggressive')}
                fullWidth={true}
              >
                <MenuItem value="formal">{t('Formal')}</MenuItem>
                <MenuItem value="informal">{t('Informal')}</MenuItem>
                <MenuItem value="authoritative">{t('Authoritative')}</MenuItem>
                <MenuItem value="assertive">{t('Assertive')}</MenuItem>
                <MenuItem value="bitter">{t('Bitter')}</MenuItem>
                <MenuItem value="critical">{t('Critical')}</MenuItem>
                <MenuItem value="arrogant">{t('Arrogant')}</MenuItem>
                <MenuItem value="aggressive">{t('Aggressive')}</MenuItem>
                <MenuItem value="sarcastic">{t('Sarcastic')}</MenuItem>
              </Select>
            </FormControl>
            <TextField
              label={t('Who is sending?')}
              fullWidth={true}
              value={messageSender}
              onChange={event => setMessageSender(event.target.value)}
              style={{ marginTop: 20 }}
            />
            <TextField
              label={t('Who is receiving?')}
              fullWidth={true}
              value={messageRecipient}
              onChange={event => setMessageRecipient(event.target.value)}
              style={{ marginTop: 20 }}
            />
            <TextField
              label={t('Number of paragraphs')}
              fullWidth={true}
              type="number"
              value={messageParagraphs}
              onChange={event => setMessageParagraphs(Number.isNaN(parseInt(event.target.value, 10)) ? 1 : parseInt(event.target.value, 10))}
              style={{ marginTop: 20 }}
            />
            <TextField
              style={{ marginTop: 20 }}
              label={t('Context')}
              fullWidth={true}
              multiline={true}
              value={messageContext}
              rows={5}
              onChange={event => setMessageContext(event.target.value)}
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={handleCloseGenMessageOptions}>
              {t('Cancel')}
            </Button>
            <Button
              disabled={messageInput.length === 0} // Disable button if messageInput is empty
              onClick={() => {
                handleCloseGenMessageOptions();
                handleAskAi('genMessage');
              }}
              color="secondary"
            >
              {t('Generate')}
            </Button>
          </DialogActions>
        </Dialog>
        <Dialog
          PaperProps={{ elevation: 1 }}
          open={openGenMediaOptions}
          onClose={handleCloseGenMediaOptions}
          fullWidth={true}
          maxWidth="xs"
        >
          <DialogTitle>{t('Select options')}</DialogTitle>
          <DialogContent>
            <SimpleRichTextField
              label={t('Input (describe what you want)')}
              value={messageInput}
              onChange={(value: string) => setMessageInput(value)}
              style={{ height: 200 }}
            />
            <FormControl style={{
              width: '100%',
              marginTop: 20,
            }}
            >
              <InputLabel id="messageTone">{t('Tone')}</InputLabel>
              <Select
                labelId="messageTone"
                value={messageTone}
                onChange={event => setMessageTone(event.target.value as unknown as 'informal' | 'formal' | 'assertive' | 'sarcastic' | 'authoritative' | 'bitter' | 'critical' | 'arrogant' | 'aggressive')}
                fullWidth={true}
              >
                <MenuItem value="formal">{t('Formal')}</MenuItem>
                <MenuItem value="informal">{t('Informal')}</MenuItem>
                <MenuItem value="authoritative">{t('Authoritative')}</MenuItem>
                <MenuItem value="assertive">{t('Assertive')}</MenuItem>
                <MenuItem value="bitter">{t('Bitter')}</MenuItem>
                <MenuItem value="critical">{t('Critical')}</MenuItem>
                <MenuItem value="arrogant">{t('Arrogant')}</MenuItem>
                <MenuItem value="aggressive">{t('Aggressive')}</MenuItem>
                <MenuItem value="sarcastic">{t('Sarcastic')}</MenuItem>
              </Select>
            </FormControl>
            <TextField
              label={t('Author')}
              fullWidth={true}
              value={messageSender}
              onChange={event => setMessageSender(event.target.value)}
              style={{ marginTop: 20 }}
            />
            <TextField
              label={t('Number of paragraphs')}
              fullWidth={true}
              type="number"
              value={messageParagraphs}
              onChange={event => setMessageParagraphs(Number.isNaN(parseInt(event.target.value, 10)) ? 1 : parseInt(event.target.value, 10))}
              style={{ marginTop: 20 }}
            />
            <TextField
              style={{ marginTop: 20 }}
              label={t('Context')}
              fullWidth={true}
              multiline={true}
              value={messageContext}
              rows={5}
              onChange={event => setMessageContext(event.target.value)}
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={handleCloseGenMediaOptions}>
              {t('Cancel')}
            </Button>
            <Button
              disabled={messageInput.length === 0}
              onClick={() => {
                handleCloseGenMediaOptions();
                handleAskAi('genMedia');
              }}
              color="secondary"
            >
              {t('Generate')}
            </Button>
          </DialogActions>
        </Dialog>
        <Dialog
          PaperProps={{ elevation: 1 }}
          open={openToneOptions}
          onClose={handleCloseToneOptions}
          fullWidth={true}
          maxWidth="xs"
        >
          <DialogTitle>{t('Select options')}</DialogTitle>
          <DialogContent>
            <FormControl style={{ width: '100%' }}>
              <InputLabel id="tone">{t('Tone')}</InputLabel>
              <Select
                labelId="tone"
                value={messageTone}
                onChange={event => setMessageTone(event.target.value as unknown as 'informal' | 'formal' | 'assertive' | 'sarcastic' | 'authoritative' | 'bitter' | 'critical' | 'arrogant' | 'aggressive')}
                fullWidth={true}
              >
                <MenuItem value="informal">{t('Informal')}</MenuItem>
                <MenuItem value="formal">{t('Formal')}</MenuItem>
                <MenuItem value="assertive">{t('Assertive')}</MenuItem>
                <MenuItem value="sarcastic">{t('Sarcastic')}</MenuItem>
                <MenuItem value="authoritative">{t('Authoritative')}</MenuItem>
                <MenuItem value="bitter">{t('Bitter')}</MenuItem>
                <MenuItem value="critical">{t('Critical')}</MenuItem>
                <MenuItem value="arrogant">{t('Arrogant')}</MenuItem>
                <MenuItem value="aggressive">{t('Aggressive')}</MenuItem>
              </Select>
            </FormControl>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleCloseToneOptions}>
              {t('Cancel')}
            </Button>
            <Button
              disabled={isContentEmpty()}
              onClick={() => {
                handleCloseToneOptions();
                handleAskAi('tone');
              }}
              color="secondary"
            >
              {t('Generate')}
            </Button>
          </DialogActions>
        </Dialog>
      </>
    );
  };
  if (variant === 'markdown') {
    return (
      <div style={style || {
        position: 'absolute',
        top: 15,
        right: 0,
      }}
      >
        {renderButton()}
      </div>
    );
  }
  if (variant === 'html') {
    return (
      <div style={style || {
        position: 'absolute',
        top: 15,
        right: 5,
      }}
      >
        {renderButton()}
      </div>
    );
  }
  if (variant === 'ckeditor') {
    return (
      <div style={style || {
        position: 'absolute',
        top: -10,
        right: 0,
      }}
      >
        {renderButton()}
      </div>
    );
  }
  return (
    <InputAdornment
      position="end"
      style={{
        position: 'absolute',
        top: 5,
        right: 0,
      }}
    >
      {renderButton()}
    </InputAdornment>
  );
};

export default TextFieldAskAI;
