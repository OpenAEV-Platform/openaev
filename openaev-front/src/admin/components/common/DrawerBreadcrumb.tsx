import { IconButton } from '@filigran/design-system';
import { ArrowBack } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';

import { useFormatter } from '../../../components/i18n';

interface DrawerBreadcrumbProps {
  parentLabel: string;
  currentLabel: string;
  onBack: () => void;
  grandParentLabel?: string;
  onBackToGrandParent?: () => void;
}

const DrawerBreadcrumb = ({
  parentLabel,
  currentLabel,
  onBack,
  grandParentLabel,
  onBackToGrandParent,
}: DrawerBreadcrumbProps) => {
  const { t } = useFormatter();
  return (
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 1,
      mb: 2,
    }}
    >
      <IconButton icon={<ArrowBack />} aria-label={t('Back')} onClick={onBack} priority="tertiary" size="md" />
      {grandParentLabel && onBackToGrandParent && (
        <>
          <Typography
            sx={{
              cursor: 'pointer',
              color: 'primary.main',
            }}
            onClick={onBackToGrandParent}
          >
            {grandParentLabel}
          </Typography>
          <Typography color="text.secondary">
            /
          </Typography>
        </>
      )}
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
