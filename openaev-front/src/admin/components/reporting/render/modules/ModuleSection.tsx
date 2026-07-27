import { ErrorOutlineOutlined, InboxOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, type ReactNode } from 'react';

import { useFormatter } from '../../../../../components/i18n';

/**
 * Shared print-ready section shell: accent bar + title (+ optional subtitle),
 * kept on a single page through `break-inside: avoid`. Also hosts the shared
 * inline error / empty blocks plus the print table / chip primitives so every
 * module renders (and degrades) identically.
 */

interface SectionProps {
  /** Anchor id targeted by the table of contents links. */
  id?: string;
  title: string;
  subtitle?: string;
  children: ReactNode;
}

export const ModuleSection: FunctionComponent<SectionProps> = ({ id, title, subtitle, children }) => {
  const theme = useTheme();
  return (
    <Box
      component="section"
      id={id}
      className="reporting-module"
      sx={{
        breakInside: 'avoid',
        marginBottom: 6,
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'stretch',
        gap: 1.5,
        marginBottom: 2.5,
      }}
      >
        <Box sx={{
          width: 4,
          borderRadius: 2,
          flexShrink: 0,
          background: `linear-gradient(180deg, ${theme.palette.primary.main} 0%, ${theme.palette.secondary.main} 100%)`,
        }}
        />
        <Box>
          <Typography sx={{
            fontFamily: '"Geologica", sans-serif',
            fontWeight: 600,
            fontSize: 18,
            lineHeight: 1.25,
          }}
          >
            {title}
          </Typography>
          {subtitle && (
            <Typography sx={{
              fontSize: 11,
              color: 'text.secondary',
              letterSpacing: '0.06em',
              textTransform: 'uppercase',
              marginTop: 0.25,
            }}
            >
              {subtitle}
            </Typography>
          )}
        </Box>
      </Box>
      {children}
    </Box>
  );
};

// -- Print table primitives ---------------------------------------------------------
//
// A NATIVE table (not MUI Table): the app theme restyles MuiTableCell head
// cells, which shifted printed headers out of alignment with their columns.
// Header and body cells share the exact same horizontal padding here, so
// columns can never drift apart in either the PDF or the HTML flavor.

export interface ReportColumn {
  label: string;
  width?: number | string;
  align?: 'left' | 'right';
}

const CELL_GUTTER = '16px';

export const ReportTable: FunctionComponent<{
  columns: ReportColumn[];
  children: ReactNode;
}> = ({ columns, children }) => {
  const theme = useTheme();
  return (
    <Box
      component="table"
      sx={{
        width: '100%',
        borderCollapse: 'collapse',
        tableLayout: 'auto',
      }}
    >
      <Box component="thead">
        <Box component="tr">
          {columns.map(column => (
            <Box
              key={column.label}
              component="th"
              sx={{
                'width': column.width,
                'textAlign': column.align ?? 'left',
                'padding': `0 ${CELL_GUTTER} 10px 0`,
                '&:last-of-type': { paddingRight: 0 },
                'fontSize': 10,
                'fontWeight': 700,
                'letterSpacing': '0.1em',
                'textTransform': 'uppercase',
                'whiteSpace': 'nowrap',
                'color': 'text.secondary',
                'borderBottom': `2px solid ${alpha(theme.palette.text.primary, 0.2)}`,
              }}
            >
              {column.label}
            </Box>
          ))}
        </Box>
      </Box>
      <Box component="tbody">
        {children}
      </Box>
    </Box>
  );
};

export const ReportRow: FunctionComponent<{ children: ReactNode }> = ({ children }) => (
  <Box component="tr">{children}</Box>
);

export const ReportCell: FunctionComponent<{
  align?: 'left' | 'right';
  children: ReactNode;
}> = ({ align = 'left', children }) => {
  const theme = useTheme();
  return (
    <Box
      component="td"
      sx={{
        'textAlign': align,
        'verticalAlign': 'middle',
        'padding': `14px ${CELL_GUTTER} 14px 0`,
        '&:last-of-type': { paddingRight: 0 },
        'fontSize': 12,
        'borderBottom': `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
      }}
    >
      {children}
    </Box>
  );
};

/** Compact tinted tag used for categorical values across print modules. */
export const PrintChip: FunctionComponent<{
  label: string;
  color?: string;
  icon?: ReactElement;
  mono?: boolean;
}> = ({ label, color, icon, mono = false }) => {
  const theme = useTheme();
  const resolved = color ?? theme.palette.primary.main;
  return (
    <Box sx={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: 0.5,
      height: 22,
      paddingX: 1,
      borderRadius: 0.5,
      backgroundColor: alpha(resolved, 0.1),
      border: `1px solid ${alpha(resolved, 0.3)}`,
      color: resolved,
    }}
    >
      {icon}
      <Typography
        component="span"
        sx={{
          fontSize: mono ? 10 : 10.5,
          fontWeight: 600,
          lineHeight: 1,
          whiteSpace: 'nowrap',
          ...(mono && { fontFamily: '"Roboto Mono", monospace' }),
        }}
      >
        {label}
      </Typography>
    </Box>
  );
};

/** Inline error block: a failed module never crashes the report. */
export const ModuleError: FunctionComponent<{ message?: string }> = ({ message }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  return (
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 1.5,
      padding: 2,
      borderRadius: 1,
      border: `1px solid ${alpha(theme.palette.error.main, 0.4)}`,
      backgroundColor: alpha(theme.palette.error.main, 0.06),
    }}
    >
      <ErrorOutlineOutlined sx={{
        color: 'error.main',
        fontSize: 20,
      }}
      />
      <Typography sx={{
        fontSize: 12,
        color: 'text.secondary',
      }}
      >
        {message ?? t('This section could not be loaded. The rest of the report is unaffected.')}
      </Typography>
    </Box>
  );
};

/** Tasteful empty state used by every module without data. */
export const ModuleEmpty: FunctionComponent<{ message?: string }> = ({ message }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: 1,
      padding: 3,
      borderRadius: 1,
      border: `1px dashed ${alpha(theme.palette.text.primary, 0.15)}`,
    }}
    >
      <InboxOutlined sx={{
        fontSize: 22,
        color: 'text.disabled',
      }}
      />
      <Typography sx={{
        fontSize: 12,
        color: 'text.secondary',
      }}
      >
        {message ?? t('No data for this section over the selected time range.')}
      </Typography>
    </Box>
  );
};
