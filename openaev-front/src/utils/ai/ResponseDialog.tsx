import { LoadingButton } from '@mui/lab';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material';
// As we can ask AI after and follow up, there is a dependency lifecycle here that can be accepted
// TODO: Cleanup a bit in upcoming version
// eslint-disable-next-line import/no-cycle
import MDEditor, { commands } from '@uiw/react-md-editor/nohighlight';
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore
import { type FunctionComponent, useEffect, useRef } from 'react';

// eslint-disable-next-line import/no-cycle
import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';
import CKEditor from '../../components/CKEditor';
import { useFormatter } from '../../components/i18n';
import { isNotEmptyField } from '../utils';

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
}

const ResponseDialog: FunctionComponent<ResponseDialogProps> = ({
  isOpen,
  isDisabled,
  handleClose,
  setContent,
  handleAccept,
  format,
  isAcceptable = true,
  content,

}) => {
  const textFieldRef = useRef<HTMLTextAreaElement>(null);
  const markdownFieldRef = useRef<HTMLTextAreaElement>(null);
  const { t } = useFormatter();
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
        <DialogTitle>{t('Ask AI')}</DialogTitle>
        <DialogContent>
          <div style={{
            width: '100%',
            minHeight: height,
            height,
            position: 'relative',
          }}
          >
            {(format === 'text' || format === 'json') && (
              <TextField
                inputRef={textFieldRef}
                disabled={isDisabled}
                rows={Math.round(height / 23)}
                value={content}
                multiline={true}
                onChange={event => setContent(event.target.value)}
                fullWidth={true}
                InputProps={{
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
                }}
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
                disabled={isDisabled}
                disableWatchdog={true}
              />
            )}
            { format === 'markdown' && (
              <MDEditor
                value={content}
                textareaProps={{ disabled: isDisabled }}
                preview="edit"
                onChange={data => setContent(data ?? '')}
                commands={[
                  {
                    ...commands.title,
                    buttonProps: { disabled: isDisabled },
                  },
                  {
                    ...commands.bold,
                    buttonProps: { disabled: isDisabled },
                  },
                  {
                    ...commands.italic,
                    buttonProps: { disabled: isDisabled },
                  },
                  {
                    ...commands.strikethrough,
                    buttonProps: { disabled: isDisabled },
                  },
                  { ...commands.divider },
                  {
                    ...commands.link,
                    buttonProps: { disabled: isDisabled },
                  },
                  {
                    ...commands.quote,
                    buttonProps: { disabled: isDisabled },
                  },
                  {
                    ...commands.code,
                    buttonProps: { disabled: isDisabled },
                  },
                  {
                    ...commands.image,
                    buttonProps: { disabled: isDisabled },
                  },
                  {
                    ...commands.divider,
                    buttonProps: { disabled: isDisabled },
                  },
                  {
                    ...commands.unorderedListCommand,
                    buttonProps: { disabled: isDisabled },
                  },
                  {
                    ...commands.orderedListCommand,
                    buttonProps: { disabled: isDisabled },
                  },
                  {
                    ...commands.checkedListCommand,
                    buttonProps: { disabled: isDisabled },
                  },
                ]}
                extraCommands={[]}
              />
            )}
            {(format === 'markdown' || format === 'html') && (
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
          </div>
          <div className="clearfix" />
          <Alert severity="warning" variant="outlined" style={format === 'html' ? { marginTop: 30 } : {}}>
            {t('Generative AI is a beta feature as we are currently fine-tuning our models. Consider checking important information.')}
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>
            {t('Close')}
          </Button>
          {isAcceptable && (
            <LoadingButton loading={isDisabled} color="secondary" onClick={() => handleAccept(content)}>
              {t('Accept')}
            </LoadingButton>
          )}
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ResponseDialog;
