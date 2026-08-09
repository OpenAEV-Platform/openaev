import { ContentCopyOutlined } from '@mui/icons-material';
import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { a11yDark, coy } from 'react-syntax-highlighter/dist/esm/styles/prism';

import { copyToClipboard } from '../../utils/utils';
import { useFormatter } from '../i18n';

interface Props {
  value: string;
  onChange?: (value: string) => void;
  /** Prism language id (e.g. "html", "css"). */
  language: string;
  /** Overline label rendered in the field header (e.g. "HTML content"). */
  label?: string;
  /** Short uppercase language chip in the header (e.g. "HTML"). Defaults to the language. */
  badge?: string;
  placeholder?: string;
  disabled?: boolean;
  readOnly?: boolean;
  error?: boolean;
  helperText?: string;
  /** Minimum body height in px. */
  minHeight?: number;
  /** Right-aligned node in the header (before the copy button). */
  headerAction?: React.ReactNode;
}

// Monospace metrics shared 1:1 by the transparent <textarea> and the Prism
// highlight layer beneath it. Any divergence (font, size, line-height,
// wrapping, padding) would drift the caret away from the rendered glyphs, so
// both layers MUST read from this single source of truth.
const EDITOR_PADDING = 12;
const FONT_STACK = 'Consolas, Monaco, "Courier New", monospace';
const FONT_SIZE = 12.5;
const LINE_HEIGHT = 1.6;

const wrapStyle = {
  whiteSpace: 'pre-wrap' as const,
  wordBreak: 'break-word' as const,
  overflowWrap: 'anywhere' as const,
};

/**
 * A real code editor with true syntax highlighting. A transparent, editable
 * <textarea> is overlaid pixel-for-pixel on a Prism-highlighted render of the
 * same text: the user types in the textarea (native caret, selection, undo),
 * while the colors come from the highlight layer showing through. No heavy
 * editor dependency, and the metrics live in one place so the two layers never
 * drift apart.
 */
const CodeEditor: FunctionComponent<Props> = ({
  value,
  onChange,
  language,
  label,
  badge,
  placeholder,
  disabled = false,
  readOnly = false,
  error = false,
  helperText,
  minHeight = 200,
  headerAction,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const isDark = theme.palette.mode === 'dark';
  const editable = !disabled && !readOnly;

  // Prism trims a single trailing newline, which would hide the caret sitting on
  // a fresh last line; append one so the highlight layer always matches the
  // textarea's height.
  const highlighted = `${value ?? ''}\n`;

  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (event.key !== 'Tab' || !onChange) return;
      event.preventDefault();
      const target = event.currentTarget;
      const { selectionStart, selectionEnd } = target;
      const next = `${value.slice(0, selectionStart)}  ${value.slice(selectionEnd)}`;
      onChange(next);
      // Restore the caret after React re-renders the controlled value.
      requestAnimationFrame(() => {
        target.selectionStart = selectionStart + 2;
        target.selectionEnd = selectionStart + 2;
      });
    },
    [value, onChange],
  );

  const borderColor = error
    ? theme.palette.error.main
    : alpha(theme.palette.text.primary, 0.12);

  return (
    <div>
      <Box
        sx={{
          'border': `1px solid ${borderColor}`,
          'borderRadius': 1,
          'overflow': 'hidden',
          'backgroundColor': theme.palette.background.code ?? theme.palette.background.paper,
          'transition': 'border-color 120ms',
          '&:focus-within': { borderColor: error ? theme.palette.error.main : theme.palette.primary.main },
        }}
      >
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            paddingInline: 1.5,
            paddingBlock: 0.75,
            borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
            backgroundColor: alpha(theme.palette.background.paper, isDark ? 0.4 : 0.6),
          }}
        >
          {label && (
            <Typography
              sx={{
                fontFamily: '"Geologica", sans-serif',
                fontWeight: 600,
                fontSize: 11,
                letterSpacing: '0.1em',
                textTransform: 'uppercase',
                color: 'text.secondary',
              }}
            >
              {label}
            </Typography>
          )}
          <Box
            sx={{
              fontSize: 10,
              fontWeight: 700,
              letterSpacing: '0.08em',
              color: 'primary.main',
              backgroundColor: alpha(theme.palette.primary.main, 0.12),
              borderRadius: 0.5,
              paddingInline: 0.75,
              paddingBlock: '1px',
            }}
          >
            {badge ?? language.toUpperCase()}
          </Box>
          <div style={{ flex: 1 }} />
          {headerAction}
          <Tooltip title={t('Copy to clipboard')}>
            <Box
              component="button"
              type="button"
              aria-label={t('Copy to clipboard')}
              onClick={() => copyToClipboard(t, value ?? '')}
              sx={{
                'display': 'flex',
                'alignItems': 'center',
                'justifyContent': 'center',
                'width': 24,
                'height': 24,
                'padding': 0,
                'border': 0,
                'borderRadius': 0.5,
                'cursor': 'pointer',
                'color': 'text.secondary',
                'backgroundColor': 'transparent',
                '&:hover': {
                  color: 'primary.main',
                  backgroundColor: alpha(theme.palette.primary.main, 0.12),
                },
              }}
            >
              <ContentCopyOutlined sx={{ fontSize: 15 }} />
            </Box>
          </Tooltip>
        </Box>

        <Box sx={{
          position: 'relative',
          minHeight,
        }}
        >
          <SyntaxHighlighter
            language={language}
            style={isDark ? a11yDark : coy}
            customStyle={{
              margin: 0,
              padding: EDITOR_PADDING,
              minHeight,
              background: 'transparent',
              fontFamily: FONT_STACK,
              fontSize: FONT_SIZE,
              lineHeight: LINE_HEIGHT,
              ...wrapStyle,
            }}
            codeTagProps={{
              style: {
                fontFamily: FONT_STACK,
                fontSize: FONT_SIZE,
                lineHeight: LINE_HEIGHT,
                ...wrapStyle,
              },
            }}
          >
            {highlighted}
          </SyntaxHighlighter>

          <Box
            component="textarea"
            value={value ?? ''}
            spellCheck={false}
            autoCapitalize="off"
            autoCorrect="off"
            placeholder={placeholder}
            disabled={disabled}
            readOnly={readOnly}
            onChange={editable && onChange ? event => onChange(event.target.value) : undefined}
            onKeyDown={editable ? handleKeyDown : undefined}
            sx={{
              'position': 'absolute',
              'inset': 0,
              'width': '100%',
              'height': '100%',
              'margin': 0,
              'padding': `${EDITOR_PADDING}px`,
              'border': 0,
              'outline': 0,
              'resize': 'none',
              'overflow': 'hidden',
              'display': 'block',
              'boxSizing': 'border-box',
              'fontFamily': FONT_STACK,
              'fontSize': FONT_SIZE,
              'lineHeight': LINE_HEIGHT,
              'color': 'transparent',
              'caretColor': theme.palette.text.primary,
              'background': 'transparent',
              'tabSize': 2,
              ...wrapStyle,
              '&::placeholder': {
                color: theme.palette.text.disabled,
                opacity: 1,
              },
              '&::selection': { backgroundColor: alpha(theme.palette.primary.main, 0.35) },
              'cursor': editable ? 'text' : 'default',
            }}
          />
        </Box>
      </Box>
      {helperText && (
        <Typography
          variant="caption"
          sx={{
            display: 'block',
            marginTop: 0.5,
            marginInline: 1.75,
            color: error ? 'error.main' : 'text.secondary',
          }}
        >
          {helperText}
        </Typography>
      )}
    </div>
  );
};

export default CodeEditor;
