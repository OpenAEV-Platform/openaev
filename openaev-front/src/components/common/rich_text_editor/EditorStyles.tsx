import GlobalStyles from '@mui/material/GlobalStyles';
import { useTheme } from '@mui/material/styles';
import { type FC } from 'react';

// ── Editor content styles ──────────────────────────────────────────────────
const EditorStyles: FC = () => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const base = '.rte-content .ProseMirror';

  return (
    <GlobalStyles
      styles={{
        [base]: {
          'minHeight': 120,
          'padding': '10px 14px',
          'outline': 'none',
          'fontFamily': theme.typography.body1.fontFamily,
          'fontSize': theme.typography.body1.fontSize,
          'lineHeight': theme.typography.body1.lineHeight,
          'color': theme.palette.text.primary,
          '& > * + *': { marginTop: 6 },
          '& ul, & ol': { paddingLeft: '1.5rem' },
          '& li': { marginTop: 2 },
          '& strong': { fontWeight: 600 },
          '& code:not(pre code)': {
            backgroundColor: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.07)',
            borderRadius: 3,
            padding: '1px 5px',
            fontFamily: 'monospace',
            fontSize: '0.9em',
          },
          '& pre': {
            'position': 'relative',
            'backgroundColor': isDark ? '#1e1e1e' : '#f5f5f5',
            'borderRadius': '6px',
            'padding': '0.75rem 1rem',
            'overflowX': 'auto',
            'margin': '0.5rem 0',
            'border': `1px solid ${isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)'}`,
            '& code': {
              background: 'none',
              color: isDark ? '#d4d4d4' : '#333',
              fontSize: '0.85em',
              fontFamily: '"Fira Code", "Fira Mono", "Roboto Mono", monospace',
              padding: 0,
              borderRadius: 0,
              whiteSpace: 'pre',
            },
          },
          '& a': {
            color: theme.palette.primary.main,
            textDecoration: 'underline',
            cursor: 'pointer',
          },
          '& blockquote': {
            'borderLeft': `4px solid ${theme.palette.primary.main}`,
            'backgroundColor': isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.03)',
            'margin': '0.5rem 0',
            'padding': '0.4rem 1rem',
            'borderRadius': '0 4px 4px 0',
            'color': isDark ? theme.palette.text.secondary : theme.palette.text.primary,
            'fontStyle': 'italic',
            '& p': { margin: 0 },
          },
        },
        // ── Task list — flat selectors for maximum specificity ─────────────
        [`${base} ul[data-type="taskList"]`]: {
          listStyle: 'none',
          padding: '0',
          margin: 0,
        },
        [`${base} ul[data-type="taskList"] li`]: {
          display: 'flex !important',
          flexDirection: 'row !important' as 'row',
          alignItems: 'flex-start',
          gap: '6px',
        },
        [`${base} ul[data-type="taskList"] li > label`]: {
          display: 'inline-flex !important',
          alignItems: 'center',
          flexShrink: 0,
          paddingTop: '3px',
        },
        [`${base} ul[data-type="taskList"] li > label input[type="checkbox"]`]: { margin: 0 },
        [`${base} ul[data-type="taskList"] li > div`]: {
          flex: '1 1 auto',
          minWidth: 0,
        },
        [`${base} ul[data-type="taskList"] li > div > p`]: { margin: '0' },
        // ── Table ──────────────────────────────────────────────────────────────
        [`${base} table`]: {
          borderCollapse: 'collapse',
          tableLayout: 'fixed',
          width: '100%',
          margin: '0.5rem 0',
          overflowX: 'auto',
          display: 'block',
        },
        [`${base} table td, ${base} table th`]: {
          'border': `1px solid ${isDark ? 'rgba(255,255,255,0.2)' : 'rgba(0,0,0,0.15)'}`,
          'padding': '6px 10px',
          'verticalAlign': 'top',
          'position': 'relative',
          'minWidth': '60px',
          '& p': { margin: 0 },
        },
        [`${base} table th`]: {
          backgroundColor: isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.05)',
          fontWeight: 600,
          textAlign: 'left',
        },
        [`${base} table .selectedCell:after`]: {
          background: isDark ? 'rgba(100,140,255,0.25)' : 'rgba(60,100,220,0.12)',
          content: '""',
          left: 0,
          right: 0,
          top: 0,
          bottom: 0,
          pointerEvents: 'none',
          position: 'absolute',
          zIndex: 2,
        },
        [`${base} .tableWrapper`]: { overflowX: 'auto' },
      }}
    />
  );
};

export default EditorStyles;
