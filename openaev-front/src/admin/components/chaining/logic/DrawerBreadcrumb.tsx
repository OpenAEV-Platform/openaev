import { ArrowBack } from '@mui/icons-material';
import { Box, IconButton, Typography } from '@mui/material';

interface DrawerBreadcrumbProps {
  parentLabel: string;
  currentLabel: string;
  onBack: () => void;
}

const DrawerBreadcrumb = ({ parentLabel, currentLabel, onBack }: DrawerBreadcrumbProps) => {
  return (
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 2,
      mb: 2,
    }}
    >
      <IconButton onClick={onBack} size="small" color="primary">
        <ArrowBack />
      </IconButton>
      <Typography
        sx={{
          cursor: 'pointer',
          color: 'primary.main',
        }}
        onClick={onBack}
      >
        {parentLabel}
      </Typography>
      <Typography color="text.secondary">
        /
      </Typography>
      <Typography>
        {currentLabel}
      </Typography>
    </Box>
  );
};

export default DrawerBreadcrumb;
