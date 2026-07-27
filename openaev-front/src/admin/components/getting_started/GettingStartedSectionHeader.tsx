import { Typography } from '@mui/material';

// Shared section header for the getting-started landing sections: a Geologica
// title with a muted one-line explanation beneath it, so every block of the
// page (scenarios, FAQ, resources) opens with the exact same rhythm.
const GettingStartedSectionHeader = ({ title, subtitle }: {
  title: string;
  subtitle?: string;
}) => (
  <div style={{ marginBottom: 4 }}>
    <Typography sx={{
      fontFamily: '"Geologica", sans-serif',
      fontSize: 18,
      fontWeight: 500,
      lineHeight: 1.2,
      color: 'text.primary',
    }}
    >
      {title}
    </Typography>
    {subtitle && (
      <Typography sx={{
        fontSize: 13,
        color: 'text.secondary',
        marginTop: 0.5,
      }}
      >
        {subtitle}
      </Typography>
    )}
  </div>
);

export default GettingStartedSectionHeader;
