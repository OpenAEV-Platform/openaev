import { Chip, Text } from '@filigran/design-system';
import { Box, Divider } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

interface Props {
  icon: ReactNode;
  label: string;
  count: number;
}

const MarketplaceSectionHeader: FunctionComponent<Props> = ({ icon, label, count }) => (
  <Box
    component="header"
    sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 1.25,
    }}
  >
    <Box sx={{
      display: 'flex',
      padding: 0.5,
      borderRadius: 'var(--radius-sm)',
      border: '1px solid var(--border-elevation-default-layer-1)',
      backgroundColor: 'var(--bg-elevation-default-layer-2)',
      color: 'primary.main',
    }}
    >
      {icon}
    </Box>
    <Text as="h2" variant="title-sm">{label}</Text>
    <Chip label={String(count)} />
    <Divider sx={{ flex: 1 }} />
  </Box>
);

export default MarketplaceSectionHeader;
