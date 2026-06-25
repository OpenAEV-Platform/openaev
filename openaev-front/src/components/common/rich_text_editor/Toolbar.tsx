import {
  Abc as AbcIcon,
  ArrowDropDown as ArrowDropDownIcon,
  BorderColor as BorderColorIcon,
  Checklist as ChecklistIcon,
  Code as CodeIcon,
  DataObject as DataObjectIcon,
  EmojiSymbols as EmojiSymbolsIcon,
  FormatAlignCenter as FormatAlignCenterIcon,
  FormatAlignJustify as FormatAlignJustifyIcon,
  FormatAlignLeft as FormatAlignLeftIcon,
  FormatAlignRight as FormatAlignRightIcon,
  FormatBold as FormatBoldIcon,
  FormatClear as FormatClearIcon,
  FormatColorFill as FormatColorFillIcon,
  FormatColorText as FormatColorTextIcon,
  FormatIndentDecrease as FormatIndentDecreaseIcon,
  FormatIndentIncrease as FormatIndentIncreaseIcon,
  FormatItalic as FormatItalicIcon,
  FormatListBulleted as FormatListBulletedIcon,
  FormatListNumbered as FormatListNumberedIcon,
  FormatQuote as FormatQuoteIcon,
  FormatSize as FormatSizeIcon,
  FormatUnderlined as FormatUnderlinedIcon,
  HorizontalRule as HorizontalRuleIcon,
  Html as HtmlIcon,
  Image as ImageIcon,
  Link as LinkIcon,
  MoreHoriz as MoreHorizIcon,
  Redo as RedoIcon,
  StrikethroughS as StrikethroughSIcon,
  Subscript as SubscriptIcon,
  Superscript as SuperscriptIcon,
  TableChart as TableChartIcon,
  Undo as UndoIcon,
} from '@mui/icons-material';
import Box from '@mui/material/Box';
import MenuItem from '@mui/material/MenuItem';
import Popover from '@mui/material/Popover';
import Select from '@mui/material/Select';
import { type FC, type MouseEvent as ReactMouseEvent, type ReactElement, useCallback, useRef, useState } from 'react';

import { useFormatter } from '../../i18n';
import { COLOR_PALETTE, FONT_FAMILIES, FONT_SIZES, HEADING_LABELS, HIGHLIGHT_PALETTE, TEXT_ALIGNMENTS } from './RichTextEditor.constants';
import { Sep, ToolbarBtn, ToolbarDropdownBtn } from './ToolbarBtn';
import {
  CodeBlockPopover,
  ColorPickerPopover,
  type EditorInstance,
  ImagePopover,
  LinkPopover,
  rteChain,
  SourcePopover,
  SpecialCharsPopover,
  TablePickerPopover,
} from './ToolbarPopovers';

// ── Toolbar ────────────────────────────────────────────────────────────────
interface ToolbarProps {
  editor: EditorInstance;
  disabled: boolean;
}

const Toolbar: FC<ToolbarProps> = ({ editor, disabled }) => {
  const { t } = useFormatter();
  const [linkAnchor, setLinkAnchor] = useState<HTMLElement | null>(null);
  const [linkUrl, setLinkUrl] = useState('https://');
  const [colorAnchor, setColorAnchor] = useState<HTMLElement | null>(null);
  const [bgColorAnchor, setBgColorAnchor] = useState<HTMLElement | null>(null);
  const [highlightAnchor, setHighlightAnchor] = useState<HTMLElement | null>(null);
  const [imageAnchor, setImageAnchor] = useState<HTMLElement | null>(null);
  const [imageUrl, setImageUrl] = useState('https://');
  const [imageTab, setImageTab] = useState<0 | 1>(0);
  const [codeBlockAnchor, setCodeBlockAnchor] = useState<HTMLElement | null>(null);
  const [tableAnchor, setTableAnchor] = useState<HTMLElement | null>(null);
  const [tableHover, setTableHover] = useState<{
    rows: number;
    cols: number;
  }>({
    rows: 0,
    cols: 0,
  });
  const [specialCharAnchor, setSpecialCharAnchor] = useState<HTMLElement | null>(null);
  const [specialCharSearch, setSpecialCharSearch] = useState('');
  const [sourceAnchor, setSourceAnchor] = useState<HTMLElement | null>(null);
  const [sourceHtml, setSourceHtml] = useState('');
  const [moreAnchor, setMoreAnchor] = useState<HTMLElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
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

  const activeAlign = TEXT_ALIGNMENTS.find(a => editor?.isActive({ textAlign: a.value }))?.value ?? 'left';
  const alignIconMap: Record<string, ReactElement> = {
    left: <FormatAlignLeftIcon sx={{ fontSize: '1.25rem' }} />,
    center: <FormatAlignCenterIcon sx={{ fontSize: '1.25rem' }} />,
    right: <FormatAlignRightIcon sx={{ fontSize: '1.25rem' }} />,
    justify: <FormatAlignJustifyIcon sx={{ fontSize: '1.25rem' }} />,
  };
  const alignOptions = TEXT_ALIGNMENTS.map(a => ({
    ...a,
    icon: alignIconMap[a.value],
  }));

  const headingLevel = ([1, 2, 3, 4, 5, 6] as const).find(l => editor?.isActive('heading', { level: l }));
  const headingValue = headingLevel ? String(headingLevel) : '0';

  return (
    <Box
      sx={theme => ({
        display: 'flex',
        flexWrap: 'nowrap',
        alignItems: 'center',
        borderBottom: '1px solid',
        borderColor: theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.08)',
        backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)',
      })}
    >
      {/* ── Clipped area: primary buttons ── */}
      <Box sx={{
        flex: '1 1 0',
        minWidth: 0,
        overflow: 'hidden',
        display: 'flex',
        flexWrap: 'nowrap',
        alignItems: 'center',
        gap: 0.25,
        p: '3px 6px',
      }}
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
          renderValue={v => (
            <span style={{
              fontSize: '0.75rem',
              fontWeight: 500,
            }}
            >
              {HEADING_LABELS[v as string] ?? t('Paragraph')}
            </span>
          )}
          sx={{
            'height': 26,
            'minWidth': 96,
            'maxWidth': 96,
            'flexShrink': 0,
            '& .MuiOutlinedInput-notchedOutline': { border: 'none' },
            '&:hover .MuiOutlinedInput-notchedOutline': { border: 'none' },
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': { border: 'none' },
            'bgcolor': 'action.hover',
            'borderRadius': 0.5,
          }}
        >
          {Object.entries(HEADING_LABELS).map(([value, label]) => (
            <MenuItem key={value} value={value} sx={{ fontSize: '0.8rem' }}>{label}</MenuItem>
          ))}
        </Select>

        {/* Font Family */}
        <Box sx={{ ml: 1 }}>
          <ToolbarDropdownBtn
            tooltip={t('Font Family')}
            label={FONT_FAMILIES.find(f => f.value === (editor?.getAttributes('textStyle').fontFamily ?? ''))?.label ?? t('Font')}
            icon={<AbcIcon sx={{ fontSize: '1.5rem' }} />}
            options={FONT_FAMILIES}
            value={editor?.getAttributes('textStyle').fontFamily ?? ''}
            onSelect={v => v ? rteChain(editor)?.focus().setFontFamily(v).run() : rteChain(editor)?.focus().unsetFontFamily().run()}
            disabled={off}
            minWidth={44}
          />
        </Box>

        {/* Font Size */}
        <Box sx={{ ml: 1 }}>
          <ToolbarDropdownBtn
            tooltip={t('Font Size')}
            label={FONT_SIZES.find(s => s.value === (editor?.getAttributes('textStyle').fontSize ?? ''))?.label ?? t('Size')}
            icon={<FormatSizeIcon />}
            options={FONT_SIZES}
            value={editor?.getAttributes('textStyle').fontSize ?? ''}
            onSelect={v => v ? rteChain(editor)?.focus().setFontSize(v).run() : rteChain(editor)?.focus().unsetFontSize().run()}
            disabled={off}
            minWidth={44}
          />
        </Box>

        {/* Text Alignment */}
        <Box sx={{ ml: 1 }}>
          <ToolbarDropdownBtn
            tooltip={t('Text Alignment')}
            label={TEXT_ALIGNMENTS.find(a => a.value === activeAlign)?.label ?? t('Align Left')}
            icon={alignIconMap[activeAlign]}
            options={alignOptions}
            value={activeAlign}
            onSelect={v => rteChain(editor)?.focus().setTextAlign(v as 'left' | 'center' | 'right' | 'justify').run()}
            disabled={off}
            minWidth={44}
            menuMinWidth={40}
          />
        </Box>

        <Sep />

        <ToolbarBtn title={t('Bold (Ctrl+B)')} onClick={() => rteChain(editor)?.focus().toggleBold().run()} active={editor?.isActive('bold') ?? false} disabled={off} icon={<FormatBoldIcon />} />
        <ToolbarBtn title={t('Italic (Ctrl+I)')} onClick={() => rteChain(editor)?.focus().toggleItalic().run()} active={editor?.isActive('italic') ?? false} disabled={off} icon={<FormatItalicIcon />} />
        <ToolbarBtn title={t('Underline (Ctrl+U)')} onClick={() => rteChain(editor)?.focus().toggleUnderline().run()} active={editor?.isActive('underline') ?? false} disabled={off} icon={<FormatUnderlinedIcon />} />
        <ToolbarBtn title={t('Strikethrough')} onClick={() => rteChain(editor)?.focus().toggleStrike().run()} active={editor?.isActive('strike') ?? false} disabled={off} icon={<StrikethroughSIcon />} />
        <ToolbarBtn title={t('Link')} onClick={openLink} active={editor?.isActive('link') ?? false} disabled={off} icon={<LinkIcon />} />

        {/* Font Color */}
        <ToolbarBtn
          title={t('Font Color')}
          onClick={e => setColorAnchor(e.currentTarget)}
          disabled={off}
          icon={(
            <FormatColorTextIcon sx={{
              fontSize: '1rem',
              borderBottom: `3px solid ${editor?.getAttributes('textStyle').color ?? 'currentColor'}`,
              pb: '1px',
            }}
            />
          )}
        />

        {/* Background Color */}
        <ToolbarBtn
          title={t('Background Color')}
          onClick={e => setBgColorAnchor(e.currentTarget)}
          disabled={off}
          icon={(
            <FormatColorFillIcon sx={{
              fontSize: '1rem',
              borderBottom: `3px solid ${editor?.getAttributes('highlight').color ?? 'transparent'}`,
              pb: '1px',
            }}
            />
          )}
        />

        {/* Highlight */}
        <ToolbarBtn
          title={t('Highlight')}
          onClick={e => setHighlightAnchor(e.currentTarget)}
          active={editor?.isActive('highlight') ?? false}
          disabled={off}
          icon={(
            <BorderColorIcon sx={{
              fontSize: '1.25rem',
              borderBottom: `3px solid ${editor?.isActive('highlight') ? (editor?.getAttributes('highlight').color ?? '#FFFF00') : 'transparent'}`,
              pb: '1px',
            }}
            />
          )}
        />

        <Sep />

        <ToolbarBtn title={t('Bullet List')} onClick={() => rteChain(editor)?.focus().toggleBulletList().run()} active={editor?.isActive('bulletList') ?? false} disabled={off} icon={<FormatListBulletedIcon />} />
        <ToolbarBtn title={t('Numbered List')} onClick={() => rteChain(editor)?.focus().toggleOrderedList().run()} active={editor?.isActive('orderedList') ?? false} disabled={off} icon={<FormatListNumberedIcon />} />
        <ToolbarBtn title={t('Decrease Indent')} onClick={() => rteChain(editor)?.focus().outdent().run()} disabled={off} icon={<FormatIndentDecreaseIcon />} />
        <ToolbarBtn title={t('Increase Indent')} onClick={() => rteChain(editor)?.focus().indent().run()} disabled={off} icon={<FormatIndentIncreaseIcon />} />
        <ToolbarBtn title={t('Todo List')} onClick={() => rteChain(editor)?.focus().toggleTaskList().run()} active={editor?.isActive('taskList') ?? false} disabled={off} icon={<ChecklistIcon />} />

        <Sep />

        <ToolbarBtn title={t('Insert Image')} onClick={(e) => { setImageUrl('https://'); setImageTab(0); setImageAnchor(e.currentTarget); }} disabled={off} icon={<ImageIcon />} />
        <ToolbarBtn title={t('Blockquote')} onClick={() => rteChain(editor)?.focus().toggleBlockquote().run()} active={editor?.isActive('blockquote') ?? false} disabled={off} icon={<FormatQuoteIcon />} />
        <ToolbarBtn title={t('Inline Code')} onClick={() => rteChain(editor)?.focus().toggleCode().run()} active={editor?.isActive('code') ?? false} disabled={off} icon={<CodeIcon />} />

        {/* Code Block */}
        <ToolbarBtn
          title={t('Code Block')}
          onClick={e => setCodeBlockAnchor(e.currentTarget)}
          active={editor?.isActive('codeBlock') ?? false}
          disabled={off}
          icon={(
            <>
              <DataObjectIcon />
              <ArrowDropDownIcon sx={{
                fontSize: '0.75rem !important',
                ml: '-2px',
              }}
              />
            </>
          )}
        />

        {/* Insert Table */}
        <ToolbarBtn
          title={t('Insert Table')}
          onClick={(e) => {
            setTableHover({
              rows: 0,
              cols: 0,
            }); setTableAnchor(e.currentTarget);
          }}
          active={editor?.isActive('table') ?? false}
          disabled={off}
          icon={(
            <>
              <TableChartIcon />
              <ArrowDropDownIcon sx={{
                fontSize: '0.75rem !important',
                ml: '-2px',
              }}
              />
            </>
          )}
        />

      </Box>

      <Sep />

      {/* ── More button — always visible, outside the clipped area ── */}
      <Box sx={{
        flexShrink: 0,
        pr: '3px',
      }}
      >
        <ToolbarBtn title={t('More options')} onClick={e => setMoreAnchor(e.currentTarget)} disabled={off} icon={<MoreHorizIcon />} />
      </Box>

      {/* ── Extracted popovers ── */}
      <LinkPopover anchor={linkAnchor} onClose={() => setLinkAnchor(null)} linkUrl={linkUrl} onUrlChange={setLinkUrl} onConfirm={confirmLink} />

      <ColorPickerPopover
        anchor={colorAnchor}
        onClose={() => setColorAnchor(null)}
        currentColor={editor?.getAttributes('textStyle').color ?? ''}
        defaultColor="#000000"
        palette={COLOR_PALETTE.filter(c => c.value)}
        onColorChange={color => rteChain(editor)?.focus().setColor(color).run()}
        onRemove={() => rteChain(editor)?.focus().unsetColor().run()}
      />

      <ColorPickerPopover
        anchor={bgColorAnchor}
        onClose={() => setBgColorAnchor(null)}
        currentColor={editor?.getAttributes('highlight').color ?? ''}
        defaultColor="#FFFF00"
        palette={COLOR_PALETTE.filter(c => c.value)}
        onColorChange={color => rteChain(editor)?.focus().setHighlight({ color }).run()}
        onRemove={() => rteChain(editor)?.focus().unsetHighlight().run()}
      />

      <ColorPickerPopover
        anchor={highlightAnchor}
        onClose={() => setHighlightAnchor(null)}
        currentColor={editor?.isActive('highlight') ? (editor?.getAttributes('highlight').color ?? '') : ''}
        defaultColor="#FFFF00"
        palette={HIGHLIGHT_PALETTE}
        onColorChange={color => rteChain(editor)?.focus().setHighlight({ color }).run()}
        onRemove={() => rteChain(editor)?.focus().unsetHighlight().run()}
        removeLabel={t('Remove highlight')}
      />

      <ImagePopover
        anchor={imageAnchor}
        onClose={() => setImageAnchor(null)}
        imageUrl={imageUrl}
        imageTab={imageTab}
        onTabChange={setImageTab}
        onUrlChange={setImageUrl}
        onInsertUrl={() => { rteChain(editor)?.focus().setImage({ src: imageUrl }).run(); setImageAnchor(null); }}
        fileInputRef={fileInputRef}
        onFileSelected={(file) => {
          const reader = new FileReader();
          reader.onload = (ev) => {
            const src = ev.target?.result as string;
            if (src) {
              rteChain(editor)?.focus().setImage({
                src,
                alt: file.name,
              }).run(); setImageAnchor(null);
            }
          };
          reader.readAsDataURL(file);
        }}
      />

      <CodeBlockPopover
        anchor={codeBlockAnchor}
        onClose={() => setCodeBlockAnchor(null)}
        isActive={editor?.isActive('codeBlock') ?? false}
        currentLang={editor?.getAttributes('codeBlock').language ?? ''}
        onSelect={(lang) => {
          if (editor?.isActive('codeBlock')) rteChain(editor)?.focus().updateAttributes('codeBlock', { language: lang }).run();
          else rteChain(editor)?.focus().setCodeBlock({ language: lang }).run();
        }}
        onRemove={() => rteChain(editor)?.focus().toggleCodeBlock().run()}
      />

      <TablePickerPopover
        anchor={tableAnchor}
        onClose={() => setTableAnchor(null)}
        hover={tableHover}
        onHover={setTableHover}
        onInsert={(rows, cols) => rteChain(editor)?.focus().insertTable({
          rows,
          cols,
          withHeaderRow: true,
        }).run()}
      />

      <SpecialCharsPopover
        anchor={specialCharAnchor}
        onClose={() => setSpecialCharAnchor(null)}
        search={specialCharSearch}
        onSearchChange={setSpecialCharSearch}
        onInsert={char => rteChain(editor)?.focus().insertContent(char).run()}
      />

      <SourcePopover
        anchor={sourceAnchor}
        onClose={() => setSourceAnchor(null)}
        sourceHtml={sourceHtml}
        onChange={setSourceHtml}
        onApply={() => { editor?.commands.setContent(sourceHtml); setSourceAnchor(null); }}
      />

      {/* ── More popover ── */}
      <Popover
        open={Boolean(moreAnchor)}
        anchorEl={moreAnchor}
        onClose={() => setMoreAnchor(null)}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'right',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
      >
        <Box sx={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          gap: 0.25,
          p: '6px 8px',
          maxWidth: 360,
        }}
        >
          <ToolbarBtn title={t('Inline Code')} onClick={() => rteChain(editor)?.focus().toggleCode().run()} active={editor?.isActive('code') ?? false} disabled={off} icon={<CodeIcon />} />
          <ToolbarBtn title={t('Special Characters')} onClick={(e) => { setSpecialCharSearch(''); setSpecialCharAnchor(e.currentTarget); }} disabled={off} icon={<EmojiSymbolsIcon />} />
          <Sep />
          <ToolbarBtn title={t('Subscript')} onClick={() => rteChain(editor)?.focus().toggleSubscript().run()} active={editor?.isActive('subscript') ?? false} disabled={off} icon={<SubscriptIcon />} />
          <ToolbarBtn title={t('Superscript')} onClick={() => rteChain(editor)?.focus().toggleSuperscript().run()} active={editor?.isActive('superscript') ?? false} disabled={off} icon={<SuperscriptIcon />} />
          <ToolbarBtn title={t('Horizontal Line')} onClick={() => rteChain(editor)?.focus().setHorizontalRule().run()} disabled={off} icon={<HorizontalRuleIcon />} />
          <Sep />
          <ToolbarBtn title={t('View Source')} onClick={(e) => { setSourceHtml(editor?.getHTML() ?? ''); setSourceAnchor(e.currentTarget); }} disabled={off} icon={<HtmlIcon />} />
          <Sep />
          <ToolbarBtn title={t('Remove Formatting')} onClick={() => rteChain(editor)?.focus().clearNodes().unsetAllMarks().run()} disabled={off} icon={<FormatClearIcon />} />
          <Sep />
          <ToolbarBtn title={t('Undo (Ctrl+Z)')} onClick={() => rteChain(editor)?.focus().undo().run()} disabled={off || !editor?.can().undo()} icon={<UndoIcon />} />
          <ToolbarBtn title={t('Redo (Ctrl+Y)')} onClick={() => rteChain(editor)?.focus().redo().run()} disabled={off || !editor?.can().redo()} icon={<RedoIcon />} />
        </Box>
      </Popover>
    </Box>
  );
};

export default Toolbar;
