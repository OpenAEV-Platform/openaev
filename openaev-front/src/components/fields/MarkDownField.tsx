import { Typography } from '@mui/material';
import { type ICommand } from '@uiw/react-md-editor';
import MDEditor, { commands } from '@uiw/react-md-editor/nohighlight';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../i18n';

interface Props {
  disabled?: boolean;
  onChange: (value: string) => void;
  onBlur?: () => void;
  initialValue: string;
}

const MarkDownField: FunctionComponent<Props> = ({
  disabled = false,
  onChange,
  onBlur = () => {},
  initialValue,
}) => {
  const { t } = useFormatter();
  const [isEdit, setIsEdit] = useState<boolean>(true);

  // Commands
  const buttonStyle = {
    border: '1px solid',
    borderRadius: 4,
    padding: '4px',
    backgroundColor: 'transparent',
    cursor: 'pointer',
  };
  const writeCommand: ICommand = {
    name: 'edit',
    keyCommand: 'preview',
    buttonProps: {
      'aria-label': 'write',
      'style': { backgroundColor: 'transparent' },
    },
    icon: (
      <div
        style={{
          ...buttonStyle,
          border: isEdit ? '1px solid' : '',
        }}
      >
        <Typography>{t('Write')}</Typography>
      </div>
    ),
    execute: () => setIsEdit(true),
  };
  const previewCommand: ICommand = {
    name: 'preview',
    keyCommand: 'preview',
    buttonProps: {
      'aria-label': 'preview',
      'style': { backgroundColor: 'transparent' },
    },
    icon: (
      <div
        style={{
          ...buttonStyle,
          border: !isEdit ? '1px solid' : '',
        }}
      >
        <Typography>{t('Preview')}</Typography>
      </div>
    ),
    execute: () => setIsEdit(false),
  };

  return (
    <MDEditor
      value={initialValue}
      textareaProps={{ disabled }}
      preview={isEdit ? 'edit' : 'preview'}
      onChange={val => onChange(val || '')}
      onBlur={onBlur}
      commands={[
        writeCommand,
        previewCommand,
        ...(isEdit
          ? [
              {
                ...commands.title,
                buttonProps: { disabled },
              },
              {
                ...commands.bold,
                buttonProps: { disabled },
              },
              {
                ...commands.italic,
                buttonProps: { disabled },
              },
              {
                ...commands.strikethrough,
                buttonProps: { disabled },
              },
              { ...commands.divider },
              {
                ...commands.link,
                buttonProps: { disabled },
              },
              {
                ...commands.quote,
                buttonProps: { disabled },
              },
              {
                ...commands.code,
                buttonProps: { disabled },
              },
              {
                ...commands.image,
                buttonProps: { disabled },
              },
              {
                ...commands.divider,
                buttonProps: { disabled },
              },
              {
                ...commands.unorderedListCommand,
                buttonProps: { disabled },
              },
              {
                ...commands.orderedListCommand,
                buttonProps: { disabled },
              },
              {
                ...commands.checkedListCommand,
                buttonProps: { disabled },
              },
            ]
          : []),
      ]}
      extraCommands={[]}
    />
  );
};

export default MarkDownField;
