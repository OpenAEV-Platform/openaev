import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Popover from '@mui/material/Popover';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import MuiTextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import { type useEditor } from '@tiptap/react';
import { type ChangeEvent, type FC, type RefObject } from 'react';

import { useFormatter } from '../../i18n';
import { CODE_LANGUAGES, SPECIAL_CHARS } from './RichTextEditor.constants';

// ── RteChain & helpers ──────────────────────────────────────────────────────
// TypeScript-Go resolves module augmentations per-file, so tiptap extension
// commands declared via `declare module '@tiptap/core'` in extension packages
// are not visible in files that only receive an Editor instance.
// This local interface + single cast gives full type safety without relying on
// cross-file augmentation.
export interface RteChain {
  focus(): RteChain;
  run(): boolean;
  // ── paragraph / heading ────────────────────────────────────────────────
  setParagraph(): RteChain;
  setHeading(attrs: { level: 1 | 2 | 3 | 4 | 5 | 6 }): RteChain;
  // ── inline marks ──────────────────────────────────────────────────────
  toggleBold(): RteChain;
  toggleItalic(): RteChain;
  toggleUnderline(): RteChain;
  toggleStrike(): RteChain;
  toggleCode(): RteChain;
  // ── lists ─────────────────────────────────────────────────────────────
  toggleBulletList(): RteChain;
  toggleOrderedList(): RteChain;
  toggleTaskList(): RteChain;
  indent(): RteChain;
  outdent(): RteChain;
  // ── link ──────────────────────────────────────────────────────────────
  setLink(attrs: {
    href: string;
    target?: string | null;
    rel?: string | null;
  }): RteChain;
  unsetLink(): RteChain;
  extendMarkRange(type: string): RteChain;
  // ── clearing ──────────────────────────────────────────────────────────
  clearNodes(): RteChain;
  unsetAllMarks(): RteChain;
  // ── text alignment ────────────────────────────────────────────────────
  setTextAlign(align: 'left' | 'center' | 'right' | 'justify'): RteChain;
  // ── font ──────────────────────────────────────────────────────────────
  setFontFamily(family: string): RteChain;
  unsetFontFamily(): RteChain;
  setFontSize(size: string): RteChain;
  unsetFontSize(): RteChain;
  // ── color ─────────────────────────────────────────────────────────────
  setColor(color: string): RteChain;
  unsetColor(): RteChain;
  // ── blockquote ────────────────────────────────────────────────────────
  toggleBlockquote(): RteChain;
  // ── code block ────────────────────────────────────────────────────────
  setCodeBlock(attrs?: { language?: string | null }): RteChain;
  toggleCodeBlock(attrs?: { language?: string | null }): RteChain;
  updateAttributes(type: string, attrs: Record<string, unknown>): RteChain;
  // ── image ─────────────────────────────────────────────────────────────
  setImage(attrs: {
    src: string;
    alt?: string;
    title?: string;
  }): RteChain;
  setHighlight(attrs: { color: string }): RteChain;
  unsetHighlight(): RteChain;
  // ── content ───────────────────────────────────────────────────────────
  insertContent(content: string): RteChain;
  // ── subscript / superscript ───────────────────────────────────────────
  toggleSubscript(): RteChain;
  toggleSuperscript(): RteChain;
  // ── horizontal rule ───────────────────────────────────────────────────
  setHorizontalRule(): RteChain;
  // ── history ───────────────────────────────────────────────────────────
  undo(): RteChain;
  redo(): RteChain;
  // ── table ─────────────────────────────────────────────────────────────
  insertTable(attrs: {
    rows: number;
    cols: number;
    withHeaderRow?: boolean;
  }): RteChain;
  deleteTable(): RteChain;
  addRowBefore(): RteChain;
  addRowAfter(): RteChain;
  deleteRow(): RteChain;
  addColumnBefore(): RteChain;
  addColumnAfter(): RteChain;
  deleteColumn(): RteChain;
  mergeCells(): RteChain;
  splitCell(): RteChain;
  mergeOrSplit(): RteChain;
  toggleHeaderRow(): RteChain;
  toggleHeaderColumn(): RteChain;
}

export type EditorInstance = ReturnType<typeof useEditor>;

/** Single cast boundary: converts the tiptap chain to our typed interface. */
export const rteChain = (ed: EditorInstance): RteChain | undefined =>
  ed ? (ed.chain() as unknown as RteChain) : undefined;

// ── ColorPickerPopover ──────────────────────────────────────────────────────

interface ColorPickerPopoverProps {
  anchor: HTMLElement | null;
  onClose: () => void;
  currentColor: string;
  defaultColor: string;
  palette: {
    label: string;
    value: string;
  }[];
  onColorChange: (color: string) => void;
  onRemove: () => void;
  removeLabel?: string;
}

export const ColorPickerPopover: FC<ColorPickerPopoverProps> = ({ anchor, onClose, currentColor, defaultColor, palette, onColorChange, onRemove, removeLabel }) => {
  const { t } = useFormatter();
  return (
    <Popover
      open={Boolean(anchor)}
      anchorEl={anchor}
      onClose={onClose}
      anchorOrigin={{
        vertical: 'bottom',
        horizontal: 'left',
      }}
    >
      <Box sx={{
        p: 1.5,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        minWidth: 180,
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
        >
          <Box
            component="input"
            type="color"
            value={currentColor || defaultColor}
            onChange={(e: ChangeEvent<HTMLInputElement>) => onColorChange(e.target.value)}
            sx={{
              width: 32,
              height: 32,
              p: '2px',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: '4px',
              cursor: 'pointer',
              bgcolor: 'transparent',
              flexShrink: 0,
            }}
          />
          <Box
            component="span"
            sx={{
              fontSize: '0.75rem',
              color: 'text.secondary',
              fontFamily: 'monospace',
            }}
          >
            {currentColor || defaultColor}
          </Box>
        </Box>
        <Box sx={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 0.5,
          maxWidth: 180,
        }}
        >
          {palette.map(({ label, value }) => (
            <Tooltip key={value} title={label} placement="top">
              <Box
                component="button"
                onClick={() => { onColorChange(value); onClose(); }}
                sx={{
                  'width': 20,
                  'height': 20,
                  'borderRadius': '3px',
                  'border': '1px solid',
                  'borderColor': 'divider',
                  'bgcolor': value,
                  'cursor': 'pointer',
                  'p': 0,
                  'outline': currentColor === value ? '2px solid' : 'none',
                  'outlineColor': 'primary.main',
                  '&:hover': { transform: 'scale(1.15)' },
                }}
              />
            </Tooltip>
          ))}
        </Box>
        <Button
          size="small"
          variant="outlined"
          color="inherit"
          onClick={() => { onRemove(); onClose(); }}
          sx={{
            fontSize: '0.7rem',
            py: 0.25,
          }}
        >
          {removeLabel ?? t('Remove color')}
        </Button>
      </Box>
    </Popover>
  );
};

// ── LinkPopover ──────────────────────────────────────────────────────────────

interface LinkPopoverProps {
  anchor: HTMLElement | null;
  onClose: () => void;
  linkUrl: string;
  onUrlChange: (url: string) => void;
  onConfirm: () => void;
}

export const LinkPopover: FC<LinkPopoverProps> = ({ anchor, onClose, linkUrl, onUrlChange, onConfirm }) => {
  const { t } = useFormatter();
  return (
    <Popover
      open={Boolean(anchor)}
      anchorEl={anchor}
      onClose={onClose}
      anchorOrigin={{
        vertical: 'bottom',
        horizontal: 'left',
      }}
    >
      <Box sx={{
        p: 1.5,
        display: 'flex',
        gap: 1,
        alignItems: 'center',
        minWidth: 280,
      }}
      >
        <MuiTextField
          size="small"
          label={t('URL')}
          value={linkUrl}
          onChange={e => onUrlChange(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && onConfirm()}
          fullWidth
          autoFocus
        />
        <Button size="small" variant="contained" onClick={onConfirm}>{t('OK')}</Button>
      </Box>
    </Popover>
  );
};

// ── ImagePopover ─────────────────────────────────────────────────────────────

interface ImagePopoverProps {
  anchor: HTMLElement | null;
  onClose: () => void;
  imageUrl: string;
  imageTab: 0 | 1;
  onTabChange: (tab: 0 | 1) => void;
  onUrlChange: (url: string) => void;
  onInsertUrl: () => void;
  fileInputRef: RefObject<HTMLInputElement | null>;
  onFileSelected: (file: File) => void;
}

export const ImagePopover: FC<ImagePopoverProps> = ({ anchor, onClose, imageUrl, imageTab, onTabChange, onUrlChange, onInsertUrl, fileInputRef, onFileSelected }) => {
  const { t } = useFormatter();
  return (
    <Popover
      open={Boolean(anchor)}
      anchorEl={anchor}
      onClose={onClose}
      anchorOrigin={{
        vertical: 'bottom',
        horizontal: 'left',
      }}
    >
      <Box sx={{ minWidth: 320 }}>
        <Tabs
          value={imageTab}
          onChange={(_, v) => onTabChange(v as 0 | 1)}
          variant="fullWidth"
          sx={{
            'borderBottom': 1,
            'borderColor': 'divider',
            'minHeight': 36,
            '& .MuiTab-root': {
              minHeight: 36,
              py: 0.5,
              fontSize: '0.75rem',
            },
          }}
        >
          <Tab label={t('Upload')} />
          <Tab label={t('URL')} />
        </Tabs>
        {imageTab === 0 && (
          <Box sx={{
            p: 1.5,
            display: 'flex',
            flexDirection: 'column',
            gap: 1.5,
            alignItems: 'center',
          }}
          >
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={(e) => { const f = e.target.files?.[0]; if (f) onFileSelected(f); }}
            />
            <Button variant="outlined" size="small" fullWidth onClick={() => fileInputRef.current?.click()}>
              {t('Choose file')}
            </Button>
          </Box>
        )}
        {imageTab === 1 && (
          <Box sx={{
            p: 1.5,
            display: 'flex',
            gap: 1,
            alignItems: 'center',
          }}
          >
            <MuiTextField
              size="small"
              label={t('Image URL')}
              value={imageUrl}
              onChange={e => onUrlChange(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && imageUrl && imageUrl !== 'https://') onInsertUrl(); }}
              fullWidth
              autoFocus
            />
            <Button size="small" variant="contained" disabled={!imageUrl || imageUrl === 'https://'} onClick={onInsertUrl}>
              {t('Insert')}
            </Button>
          </Box>
        )}
      </Box>
    </Popover>
  );
};

// ── SourcePopover ─────────────────────────────────────────────────────────────

interface SourcePopoverProps {
  anchor: HTMLElement | null;
  onClose: () => void;
  sourceHtml: string;
  onChange: (html: string) => void;
  onApply: () => void;
}

export const SourcePopover: FC<SourcePopoverProps> = ({ anchor, onClose, sourceHtml, onChange, onApply }) => {
  const { t } = useFormatter();
  return (
    <Popover
      open={Boolean(anchor)}
      anchorEl={anchor}
      onClose={onClose}
      anchorOrigin={{
        vertical: 'bottom',
        horizontal: 'left',
      }}
    >
      <Box sx={{
        width: 520,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        p: 1.5,
      }}
      >
        <MuiTextField
          multiline
          minRows={8}
          maxRows={20}
          size="small"
          value={sourceHtml}
          onChange={e => onChange(e.target.value)}
          fullWidth
          slotProps={{
            input: {
              sx: {
                fontFamily: '"Fira Code", "Fira Mono", "Roboto Mono", monospace',
                fontSize: '0.78rem',
                lineHeight: 1.5,
              },
            },
          }}
        />
        <Box sx={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: 1,
        }}
        >
          <Button size="small" variant="outlined" color="inherit" onClick={onClose}>{t('Cancel')}</Button>
          <Button size="small" variant="contained" onClick={onApply}>{t('Apply')}</Button>
        </Box>
      </Box>
    </Popover>
  );
};

// ── CodeBlockPopover ─────────────────────────────────────────────────────────

interface CodeBlockPopoverProps {
  anchor: HTMLElement | null;
  onClose: () => void;
  isActive: boolean;
  currentLang: string;
  onSelect: (lang: string | null) => void;
  onRemove: () => void;
}

export const CodeBlockPopover: FC<CodeBlockPopoverProps> = ({ anchor, onClose, isActive, currentLang, onSelect, onRemove }) => {
  const { t } = useFormatter();
  return (
    <Popover
      open={Boolean(anchor)}
      anchorEl={anchor}
      onClose={onClose}
      anchorOrigin={{
        vertical: 'bottom',
        horizontal: 'left',
      }}
    >
      <Box sx={{
        py: 0.5,
        minWidth: 160,
        maxHeight: 300,
        overflowY: 'auto',
      }}
      >
        {CODE_LANGUAGES.map(({ label, value }) => (
          <Box
            key={value || '__plain__'}
            component="button"
            onClick={() => { onSelect(value || null); onClose(); }}
            sx={{
              'display': 'block',
              'width': '100%',
              'textAlign': 'left',
              'px': 2,
              'py': 0.75,
              'cursor': 'pointer',
              'border': 'none',
              'bgcolor': isActive && currentLang === value ? 'action.selected' : 'transparent',
              'fontSize': '0.8rem',
              'fontFamily': value ? 'monospace' : 'inherit',
              'color': 'text.primary',
              '&:hover': { bgcolor: 'action.hover' },
            }}
          >
            {label}
          </Box>
        ))}
        {isActive && (
          <Box
            component="button"
            onClick={() => { onRemove(); onClose(); }}
            sx={{
              'display': 'block',
              'width': '100%',
              'textAlign': 'left',
              'px': 2,
              'py': 0.75,
              'cursor': 'pointer',
              'border': 'none',
              'borderTop': '1px solid',
              'borderColor': 'divider',
              'mt': 0.5,
              'bgcolor': 'transparent',
              'fontSize': '0.75rem',
              'color': 'error.main',
              '&:hover': { bgcolor: 'action.hover' },
            }}
          >
            {t('Remove code block')}
          </Box>
        )}
      </Box>
    </Popover>
  );
};

// ── TablePickerPopover ───────────────────────────────────────────────────────

interface TablePickerPopoverProps {
  anchor: HTMLElement | null;
  onClose: () => void;
  hover: {
    rows: number;
    cols: number;
  };
  onHover: (pos: {
    rows: number;
    cols: number;
  }) => void;
  maxRows?: number;
  maxCols?: number;
  onInsert: (rows: number, cols: number) => void;
}

export const TablePickerPopover: FC<TablePickerPopoverProps> = ({ anchor, onClose, hover, onHover, maxRows = 8, maxCols = 8, onInsert }) => {
  const { t } = useFormatter();
  return (
    <Popover
      open={Boolean(anchor)}
      anchorEl={anchor}
      onClose={onClose}
      anchorOrigin={{
        vertical: 'bottom',
        horizontal: 'left',
      }}
    >
      <Box sx={{
        p: 1.5,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        minWidth: 180,
      }}
      >
        <Box sx={{
          fontSize: '0.75rem',
          color: 'text.secondary',
          textAlign: 'center',
          minHeight: '1em',
        }}
        >
          {hover.rows > 0 && hover.cols > 0 ? `${hover.rows} × ${hover.cols}` : t('Select table size')}
        </Box>
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: '3px',
        }}
        >
          {Array.from({ length: maxRows }, (_, rowIdx) => (
            <Box
              key={rowIdx}
              sx={{
                display: 'flex',
                gap: '3px',
              }}
            >
              {Array.from({ length: maxCols }, (_, colIdx) => {
                const r = rowIdx + 1;
                const c = colIdx + 1;
                const highlighted = r <= hover.rows && c <= hover.cols;
                return (
                  <Box
                    key={colIdx}
                    component="button"
                    onMouseEnter={() => onHover({
                      rows: r,
                      cols: c,
                    })}
                    onMouseLeave={() => onHover({
                      rows: 0,
                      cols: 0,
                    })}
                    onClick={() => { onInsert(r, c); onClose(); }}
                    sx={{
                      'width': 18,
                      'height': 18,
                      'border': '1px solid',
                      'borderColor': highlighted ? 'primary.main' : 'divider',
                      'borderRadius': '2px',
                      'bgcolor': highlighted ? 'primary.light' : 'action.hover',
                      'cursor': 'pointer',
                      'p': 0,
                      'transition': 'background-color 80ms, border-color 80ms',
                      '&:hover': { borderColor: 'primary.main' },
                    }}
                  />
                );
              })}
            </Box>
          ))}
        </Box>
      </Box>
    </Popover>
  );
};

// ── SpecialCharsPopover ──────────────────────────────────────────────────────

interface SpecialCharsPopoverProps {
  anchor: HTMLElement | null;
  onClose: () => void;
  search: string;
  onSearchChange: (q: string) => void;
  onInsert: (char: string) => void;
}

export const SpecialCharsPopover: FC<SpecialCharsPopoverProps> = ({ anchor, onClose, search, onSearchChange, onInsert }) => {
  const { t } = useFormatter();
  return (
    <Popover
      open={Boolean(anchor)}
      anchorEl={anchor}
      onClose={onClose}
      anchorOrigin={{
        vertical: 'bottom',
        horizontal: 'left',
      }}
    >
      <Box sx={{
        width: 340,
        display: 'flex',
        flexDirection: 'column',
      }}
      >
        <Box sx={{
          p: '8px 12px',
          borderBottom: '1px solid',
          borderColor: 'divider',
        }}
        >
          <MuiTextField
            size="small"
            placeholder={t('Search…')}
            value={search}
            onChange={e => onSearchChange(e.target.value)}
            fullWidth
            autoFocus
            slotProps={{ input: { sx: { fontSize: '0.8rem' } } }}
          />
        </Box>
        <Box sx={{
          maxHeight: 320,
          overflowY: 'auto',
          p: '6px 12px 12px',
        }}
        >
          {SPECIAL_CHARS.map(({ category, chars }) => {
            const filtered = search
              ? chars.filter(c => c.label.toLowerCase().includes(search.toLowerCase()) || c.char === search)
              : chars;
            if (filtered.length === 0) return null;
            return (
              <Box key={category} sx={{ mb: 1.5 }}>
                <Box sx={{
                  fontSize: '0.62rem',
                  fontWeight: 700,
                  color: 'text.disabled',
                  textTransform: 'uppercase',
                  letterSpacing: '0.08em',
                  mb: 0.5,
                }}
                >
                  {category}
                </Box>
                <Box sx={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: '3px',
                }}
                >
                  {filtered.map(({ char, label }) => (
                    <Tooltip key={label} title={label} placement="top" arrow>
                      <Box
                        component="button"
                        onClick={() => { onInsert(char); onClose(); }}
                        sx={{
                          'width': 30,
                          'height': 30,
                          'fontSize': '1rem',
                          'lineHeight': 1,
                          'display': 'flex',
                          'alignItems': 'center',
                          'justifyContent': 'center',
                          'border': '1px solid',
                          'borderColor': 'divider',
                          'borderRadius': '4px',
                          'bgcolor': 'action.hover',
                          'cursor': 'pointer',
                          'p': 0,
                          'color': 'text.primary',
                          '&:hover': {
                            bgcolor: 'primary.light',
                            borderColor: 'primary.main',
                          },
                        }}
                      >
                        {char}
                      </Box>
                    </Tooltip>
                  ))}
                </Box>
              </Box>
            );
          })}
        </Box>
      </Box>
    </Popover>
  );
};
