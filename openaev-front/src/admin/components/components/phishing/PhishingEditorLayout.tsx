import { Box } from '@mui/material';
import { type ReactNode } from 'react';

import Breadcrumbs from '../../../../components/Breadcrumbs';

interface Props {
  breadcrumbs: {
    label: string;
    link?: string;
    current?: boolean;
  }[];
  /** Hero header (title + Cancel / Save actions). */
  header: ReactNode;
  /** Editor form (fields + code editors). */
  left: ReactNode;
  /** Live preview pane. Sticks alongside the form on wide screens. */
  right: ReactNode;
}

/**
 * Two-pane full-page editor shell shared by the phishing landing page and lure
 * email editors: breadcrumbs + hero header on top, then the editable form on
 * the left and a live preview on the right. The preview is sticky and fills the
 * viewport height on wide screens so operators edit and see the recipient
 * experience side by side, instead of the old edit / close / preview / reopen
 * drawer loop.
 */
const PhishingEditorLayout = ({ breadcrumbs, header, left, right }: Props) => (
  <div style={{
    display: 'flex',
    flexDirection: 'column',
    gap: 16,
  }}
  >
    <Breadcrumbs variant="object" elements={breadcrumbs} />
    {header}
    <Box sx={{
      display: 'grid',
      gridTemplateColumns: {
        xs: '1fr',
        lg: 'minmax(0, 1fr) minmax(0, 1fr)',
      },
      gap: 2,
      alignItems: 'start',
    }}
    >
      <Box sx={{ minWidth: 0 }}>{left}</Box>
      <Box sx={{
        minWidth: 0,
        position: { lg: 'sticky' },
        top: { lg: 16 },
        height: {
          xs: 520,
          lg: 'calc(100vh - 200px)',
        },
        display: 'flex',
      }}
      >
        {right}
      </Box>
    </Box>
  </div>
);

export default PhishingEditorLayout;
