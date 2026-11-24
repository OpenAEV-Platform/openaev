import { Chip, Text } from '@filigran/design-system';
import { Divider } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

interface Props {
  icon: ReactNode;
  label: string;
  count: number;
}

const MarketplaceSectionHeader: FunctionComponent<Props> = ({ icon, label, count }) => {
  const theme = useTheme();
  return (
    <header style={{
      display: 'flex',
      alignItems: 'center',
      gap: theme.spacing(1.25),
    }}
    >
      <div style={{
        display: 'flex',
        padding: theme.spacing(0.5),
        borderRadius: 'var(--radius-sm)',
        border: '1px solid var(--border-elevation-default-layer-1)',
        backgroundColor: 'var(--bg-elevation-default-layer-2)',
        color: theme.palette.primary.main,
      }}
      >
        {icon}
      </div>
      <Text as="h2" variant="title-sm">{label}</Text>
      <Chip label={String(count)} />
      <Divider sx={{ flex: 1 }} />
    </header>
  );
};

export default MarketplaceSectionHeader;
