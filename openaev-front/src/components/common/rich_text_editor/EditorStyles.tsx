import GlobalStyles from '@mui/material/GlobalStyles';
import { useTheme } from '@mui/material/styles';
import { type FC } from 'react';

// ── Editor content styles ──────────────────────────────────────────────────
const EditorStyles: FC = () => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  return (
    <GlobalStyles
      styles={{
        '.rte-content .ProseMirror': {
          minHeight: 120,
          padding: '10px 14px',
          outline: 'none',
          fontFamily: theme.typography.body1.fontFamily,
          fontSize: theme.typography.body1.fontSize,
          lineHeight: theme.typography.body1.lineHeight,
          color: theme.palette.text.primary,
          '& > * + *': { marginTop: 6 },
          '& ul, & ol': { paddingLeft: '1.5rem' },
          '& li': { marginTop: 2 },
          '& strong': { fontWeight: 600 },
          '& code': {
            backgroundColor: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.07)',
            borderRadius: 3,
            padding: '1px 5px',
            fontFamily: 'monospace',
            fontSize: '0.9em',
          },
          '& a': {
            color: theme.palette.primary.main,
            textDecoration: 'underline',
            cursor: 'pointer',
          },
        },
      }}
    />
  );
};

export default EditorStyles;

