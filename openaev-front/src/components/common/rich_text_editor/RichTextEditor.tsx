import Box from '@mui/material/Box';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { type FC, useEffect, useRef } from 'react';
import EditorStyles from './EditorStyles';
import Toolbar from './Toolbar';
import { type RichTextEditorProps } from './RichTextEditor.types';

// ── RichTextEditor ─────────────────────────────────────────────────────────
const RichTextEditor: FC<RichTextEditorProps> = ({ value, onChange, onBlur, disabled = false, onReady }) => {
  const onChangeRef = useRef(onChange);
  const onBlurRef = useRef(onBlur);
  const onReadyRef = useRef(onReady);
  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);
  useEffect(() => {
    onBlurRef.current = onBlur;
  }, [onBlur]);
  useEffect(() => {
    onReadyRef.current = onReady;
  }, [onReady]);

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        link: { openOnClick: false, autolink: true, defaultProtocol: 'https' },
      }),
    ],
    content: value || '',
    editable: !disabled,
    onUpdate: ({ editor: e }) => {
      if (e.isDestroyed) return;
      onChangeRef.current?.(e.getHTML());
    },
    onCreate: ({ editor: e }) => {
      onReadyRef.current?.(e);
    },
    onBlur: () => onBlurRef.current?.(),
  });

  // Sync editable state
  useEffect(() => {
    if (!editor || editor.isDestroyed) return;
    editor.setEditable(!disabled);
  }, [disabled, editor]);

  // Sync controlled value
  useEffect(() => {
    if (!editor || editor.isDestroyed) return;
    const next = value ?? '';
    if (editor.getHTML() !== next) {
      editor.commands.setContent(next);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  return (
    <>
      <EditorStyles />
      <Box
        sx={(theme) => ({
          border: '1px solid',
          borderColor: theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.23)' : 'rgba(0,0,0,0.23)',
          borderRadius: `${theme.shape.borderRadius}px`,
          overflow: 'hidden',
          transition: 'border-color 150ms cubic-bezier(0.4, 0, 0.2, 1)',
          opacity: disabled ? 0.38 : 1,
          '&:hover': !disabled
            ? {
                borderColor:
                  theme.palette.mode === 'dark' ? theme.palette.common.white : theme.palette.text.primary,
              }
            : undefined,
          '&:focus-within': {
            borderColor: theme.palette.primary.main,
            borderWidth: 2,
          },
        })}
      >
        <Toolbar editor={editor} disabled={disabled} />
        <Box className="rte-content">
          <EditorContent editor={editor} />
        </Box>
      </Box>
    </>
  );
};

export default RichTextEditor;
export type { RichTextEditorProps } from './RichTextEditor.types';
