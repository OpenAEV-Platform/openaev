import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Popover from '@mui/material/Popover';
import Select from '@mui/material/Select';
import MuiTextField from '@mui/material/TextField';
import Box from '@mui/material/Box';
import CodeIcon from '@mui/icons-material/Code';
import FormatBoldIcon from '@mui/icons-material/FormatBold';
import FormatClearIcon from '@mui/icons-material/FormatClear';
import FormatItalicIcon from '@mui/icons-material/FormatItalic';
import FormatListBulletedIcon from '@mui/icons-material/FormatListBulleted';
import FormatListNumberedIcon from '@mui/icons-material/FormatListNumbered';
import FormatUnderlinedIcon from '@mui/icons-material/FormatUnderlined';
import LinkIcon from '@mui/icons-material/Link';
import StrikethroughSIcon from '@mui/icons-material/StrikethroughS';
import { useEditor } from '@tiptap/react';
import { type FC, type MouseEvent as ReactMouseEvent, useCallback, useState } from 'react';
import { HEADING_LABELS } from './RichTextEditor.constants';
import { Sep, ToolbarBtn } from './ToolbarBtn';

// ── Typed chain builder ─────────────────────────────────────────────────────
// TypeScript-Go resolves module augmentations per-file, so tiptap extension
// commands declared via `declare module '@tiptap/core'` in extension packages
// are not visible in files that only receive an Editor instance.
// This local interface + single cast gives full type safety without relying on
// cross-file augmentation.
interface RteChain {
  focus(): RteChain;
  run(): boolean;
  // ── paragraph / heading ──────────────────────────────────────────────────
  setParagraph(): RteChain;
  setHeading(attrs: { level: 1 | 2 | 3 | 4 | 5 | 6 }): RteChain;
  // ── inline marks ────────────────────────────────────────────────────────
  toggleBold(): RteChain;
  toggleItalic(): RteChain;
  toggleUnderline(): RteChain;
  toggleStrike(): RteChain;
  toggleCode(): RteChain;
  // ── lists ────────────────────────────────────────────────────────────────
  toggleBulletList(): RteChain;
  toggleOrderedList(): RteChain;
  // ── link ─────────────────────────────────────────────────────────────────
  setLink(attrs: { href: string; target?: string | null; rel?: string | null }): RteChain;
  unsetLink(): RteChain;
  extendMarkRange(type: string): RteChain;
  // ── clearing ─────────────────────────────────────────────────────────────
  clearNodes(): RteChain;
  unsetAllMarks(): RteChain;
}

type EditorInstance = ReturnType<typeof useEditor>;

/** Single cast boundary: converts the tiptap chain to our typed interface. */
const rteChain = (ed: EditorInstance): RteChain | undefined =>
  ed ? (ed.chain() as unknown as RteChain) : undefined;

// ── Toolbar ────────────────────────────────────────────────────────────────
interface ToolbarProps {
  editor: EditorInstance;
  disabled: boolean;
}

const Toolbar: FC<ToolbarProps> = ({ editor, disabled }) => {
  const [linkAnchor, setLinkAnchor] = useState<HTMLElement | null>(null);
  const [linkUrl, setLinkUrl] = useState('https://');
  const off = disabled || !editor;

  const openLink = useCallback(
    (e: ReactMouseEvent<HTMLButtonElement>) => {
      setLinkUrl(editor?.getAttributes('link').href ?? 'https://');
      setLinkAnchor(e.currentTarget);
    },
    [editor],
  );

  const confirmLink = useCallback(() => {
    if (linkUrl && linkUrl !== 'https://') {
      rteChain(editor)?.focus().extendMarkRange('link').setLink({ href: linkUrl }).run();
    } else {
      rteChain(editor)?.focus().unsetLink().run();
    }
    setLinkAnchor(null);
  }, [editor, linkUrl]);

  const headingLevel = ([1, 2, 3, 4, 5, 6] as const).find((l) => editor?.isActive('heading', { level: l }));
  const headingValue = headingLevel ? String(headingLevel) : '0';

  return (
    <Box
      sx={(theme) => ({
        display: 'flex',
        flexWrap: 'nowrap',
        alignItems: 'center',
        overflowX: 'auto',
        gap: 0.25,
        p: '3px 6px',
        borderBottom: '1px solid',
        borderColor: theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.08)',
        backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)',
        '&::-webkit-scrollbar': { height: 3 },
        '&::-webkit-scrollbar-thumb': { bgcolor: 'divider', borderRadius: 2 },
      })}
    >
      {/* Heading */}
      <Select
        value={headingValue}
        onChange={(e) => {
          const v = e.target.value;
          if (v === '0') rteChain(editor)?.focus().setParagraph().run();
          else rteChain(editor)?.focus().setHeading({ level: Number(v) as 1 | 2 | 3 | 4 | 5 | 6 }).run();
        }}
        disabled={off}
        size="small"
        renderValue={(v) => (
          <span style={{ fontSize: '0.75rem', fontWeight: 500 }}>{HEADING_LABELS[v as string] ?? 'Paragraph'}</span>
        )}
        sx={{
          height: 26,
          minWidth: 96,
          maxWidth: 96,
          flexShrink: 0,
          '& .MuiOutlinedInput-notchedOutline': { border: 'none' },
          '&:hover .MuiOutlinedInput-notchedOutline': { border: 'none' },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': { border: 'none' },
          bgcolor: 'action.hover',
          borderRadius: 0.5,
        }}
      >
        {Object.entries(HEADING_LABELS).map(([value, label]) => (
          <MenuItem key={value} value={value} sx={{ fontSize: '0.8rem' }}>
            {label}
          </MenuItem>
        ))}
      </Select>

      <Sep />

      <ToolbarBtn
        title="Bold (Ctrl+B)"
        onClick={() => rteChain(editor)?.focus().toggleBold().run()}
        active={editor?.isActive('bold') ?? false}
        disabled={off}
        icon={<FormatBoldIcon />}
      />
      <ToolbarBtn
        title="Italic (Ctrl+I)"
        onClick={() => rteChain(editor)?.focus().toggleItalic().run()}
        active={editor?.isActive('italic') ?? false}
        disabled={off}
        icon={<FormatItalicIcon />}
      />
      <ToolbarBtn
        title="Underline (Ctrl+U)"
        onClick={() => rteChain(editor)?.focus().toggleUnderline().run()}
        active={editor?.isActive('underline') ?? false}
        disabled={off}
        icon={<FormatUnderlinedIcon />}
      />
      <ToolbarBtn
        title="Strikethrough"
        onClick={() => rteChain(editor)?.focus().toggleStrike().run()}
        active={editor?.isActive('strike') ?? false}
        disabled={off}
        icon={<StrikethroughSIcon />}
      />

      <ToolbarBtn
        title="Link"
        onClick={openLink as unknown as () => void}
        active={editor?.isActive('link') ?? false}
        disabled={off}
        icon={<LinkIcon />}
      />
      <Popover
        open={Boolean(linkAnchor)}
        anchorEl={linkAnchor}
        onClose={() => setLinkAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      >
        <Box sx={{ p: 1.5, display: 'flex', gap: 1, alignItems: 'center', minWidth: 280 }}>
          <MuiTextField
            size="small"
            label="URL"
            value={linkUrl}
            onChange={(e) => setLinkUrl(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && confirmLink()}
            fullWidth
            autoFocus
          />
          <Button size="small" variant="contained" onClick={confirmLink}>
            OK
          </Button>
        </Box>
      </Popover>

      <Sep />

      <ToolbarBtn
        title="Bullet List"
        onClick={() => rteChain(editor)?.focus().toggleBulletList().run()}
        active={editor?.isActive('bulletList') ?? false}
        disabled={off}
        icon={<FormatListBulletedIcon />}
      />
      <ToolbarBtn
        title="Numbered List"
        onClick={() => rteChain(editor)?.focus().toggleOrderedList().run()}
        active={editor?.isActive('orderedList') ?? false}
        disabled={off}
        icon={<FormatListNumberedIcon />}
      />

      <Sep />

      <ToolbarBtn
        title="Inline Code"
        onClick={() => rteChain(editor)?.focus().toggleCode().run()}
        active={editor?.isActive('code') ?? false}
        disabled={off}
        icon={<CodeIcon />}
      />
      <ToolbarBtn
        title="Remove Formatting"
        onClick={() => rteChain(editor)?.focus().clearNodes().unsetAllMarks().run()}
        disabled={off}
        icon={<FormatClearIcon />}
      />
    </Box>
  );
};

export default Toolbar;
