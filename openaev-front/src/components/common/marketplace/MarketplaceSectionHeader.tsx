import { Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

interface Props {
  icon: ReactNode;
  label: string;
  count: number;
}

// Title of a group of marketplace results (connector type, action domain).
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
        width: 28,
        height: 28,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: theme.shape.borderRadius,
        border: `1px solid ${alpha(theme.palette.primary.main, 0.2)}`,
        backgroundColor: alpha(theme.palette.primary.main, 0.1),
        color: theme.palette.primary.main,
        fontSize: 16,
      }}
      >
        {icon}
      </div>
      <Typography
        component="h2"
        sx={{
          fontFamily: theme.typography.h1.fontFamily,
          fontSize: 16,
          fontWeight: 600,
          margin: 0,
        }}
      >
        {label}
      </Typography>
      <span style={{
        padding: '1px 6px',
        borderRadius: 2,
        backgroundColor: alpha(theme.palette.text.primary, 0.06),
        fontSize: 11,
        fontWeight: 500,
        fontVariantNumeric: 'tabular-nums',
        color: theme.palette.text.secondary,
      }}
      >
        {count}
      </span>
      <div style={{
        flex: 1,
        height: 1,
        backgroundColor: alpha(theme.palette.text.primary, 0.05),
      }}
      />
    </header>
  );
};

export default MarketplaceSectionHeader;
