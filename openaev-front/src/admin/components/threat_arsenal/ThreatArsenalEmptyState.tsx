import { ExploreOutlined } from '@mui/icons-material';
import { Box, Button, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';

interface Props {
  hasFilters: boolean;
  onResetFilters: () => void;
}

const ThreatArsenalEmptyState: FunctionComponent<Props> = ({
  hasFilters,
  onResetFilters,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const accent = theme.palette.primary.main;

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        paddingBlock: 8,
        paddingInline: 4,
        borderRadius: 1,
        border: `1px dashed ${theme.palette.divider}`,
        backgroundColor: alpha(theme.palette.background.paper, 0.5),
      }}
    >
      <Box
        sx={{
          width: 64,
          height: 64,
          borderRadius: '50%',
          backgroundColor: alpha(accent, 0.08),
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: 2,
          border: `1px solid ${alpha(accent, 0.2)}`,
        }}
      >
        <ExploreOutlined sx={{
          fontSize: 30,
          color: accent,
        }}
        />
      </Box>
      <Typography
        variant="h6"
        sx={{
          fontWeight: 600,
          marginBottom: 1,
        }}
      >
        {hasFilters ? t('No arsenal items match your filters') : t('Your threat arsenal is empty')}
      </Typography>
      <Typography
        variant="body2"
        sx={{
          color: 'text.secondary',
          maxWidth: 420,
          marginBottom: 3,
        }}
      >
        {hasFilters
          ? t('Try adjusting filters or clearing them to see more arsenal items.')
          : t('Create your first arsenal item or import an existing arsenal to get started.')}
      </Typography>
      {hasFilters && (
        <Button
          variant="outlined"
          color="primary"
          onClick={onResetFilters}
          sx={{
            textTransform: 'none',
            borderRadius: 1,
            paddingInline: 3,
          }}
        >
          {t('Reset filters')}
        </Button>
      )}
    </Box>
  );
};

export default ThreatArsenalEmptyState;
