import { ExtensionOutlined, RocketLaunchOutlined, VerifiedOutlined } from '@mui/icons-material';
import { Chip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';

interface Props { connectors: CatalogConnectorOutput[] }

const CatalogHero = ({ connectors }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const totalCount = connectors.length;
  const verifiedCount = connectors.filter(c => c.catalog_connector_verified === true).length;
  const deployedInstancesCount = connectors.reduce((acc, c) => acc + (c.instance_deployed_count ?? 0), 0);

  const chipSx = {
    borderRadius: 1,
    height: 24,
  };

  return (
    <header
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: theme.spacing(2),
        flexWrap: 'wrap',
      }}
    >
      <div>
        <Typography variant="h1" sx={{ margin: 0 }}>
          {t('Connector catalog')}
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {t('Browse, filter and deploy collectors, injectors and executors from the XTM ecosystem.')}
        </Typography>
      </div>
      <div style={{
        display: 'flex',
        gap: theme.spacing(1),
        flexWrap: 'wrap',
      }}
      >
        <Chip
          variant="outlined"
          size="small"
          sx={chipSx}
          icon={<ExtensionOutlined sx={{ fontSize: 14 }} />}
          label={totalCount === 1 ? t('1 connector') : t('{count} connectors', { count: totalCount })}
        />
        <Chip
          variant="outlined"
          size="small"
          color="success"
          sx={chipSx}
          icon={<VerifiedOutlined sx={{ fontSize: 14 }} />}
          label={verifiedCount === 1 ? t('1 verified') : t('{count} verified', { count: verifiedCount })}
        />
        <Chip
          variant="outlined"
          size="small"
          color="primary"
          sx={chipSx}
          icon={<RocketLaunchOutlined sx={{ fontSize: 14 }} />}
          label={deployedInstancesCount === 1 ? t('1 deployed instance') : t('{count} deployed instances', { count: deployedInstancesCount })}
        />
      </div>
    </header>
  );
};

export default CatalogHero;
