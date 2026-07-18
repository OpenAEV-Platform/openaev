import { ExtensionOutlined, RocketLaunchOutlined } from '@mui/icons-material';
import { Chip, SvgIcon, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LogoFiligranIcon } from 'filigran-icon';

import { useFormatter } from '../../../../components/i18n';
import { type ConnectorItem } from './catalog-facets';

interface Props {
  connectors: ConnectorItem[];
  title?: string;
  subtitle?: string;
}

const CatalogHero = ({ connectors, title, subtitle }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const totalCount = connectors.length;
  // Support semantics (same as OpenCTI): verified = supported by Filigran.
  const filigranSupportedCount = connectors.filter(c => c.verified).length;
  const deployedInstancesCount = connectors.reduce((acc, c) => acc + c.deployedCount, 0);

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
          {title ?? t('Connector catalog')}
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {subtitle ?? t('Browse, filter and deploy collectors, injectors and executors from the XTM ecosystem.')}
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
          color="primary"
          sx={chipSx}
          icon={<SvgIcon component={LogoFiligranIcon} inheritViewBox sx={{ fontSize: 12 }} />}
          label={filigranSupportedCount === 1
            ? t('1 supported by Filigran')
            : t('{count} supported by Filigran', { count: filigranSupportedCount })}
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
