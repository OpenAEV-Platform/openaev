import { SearchOffOutlined } from '@mui/icons-material';
import { Button, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';

interface Props { onResetFilters: () => void }

const CatalogEmptyState = ({ onResetFilters }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  return (
    <section
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: theme.spacing(1),
        padding: theme.spacing(6, 2),
        border: `1px dashed ${theme.palette.divider}`,
        borderRadius: theme.shape.borderRadius,
        textAlign: 'center',
      }}
    >
      <SearchOffOutlined sx={{
        fontSize: 40,
        color: 'text.secondary',
      }}
      />
      <Typography variant="h6" sx={{ margin: 0 }}>
        {t('No connectors match your filters')}
      </Typography>
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        {t('Try adjusting your search or clearing some filters.')}
      </Typography>
      <Button variant="outlined" size="small" onClick={onResetFilters} sx={{ marginTop: 1 }}>
        {t('Reset filters')}
      </Button>
    </section>
  );
};

export default CatalogEmptyState;
