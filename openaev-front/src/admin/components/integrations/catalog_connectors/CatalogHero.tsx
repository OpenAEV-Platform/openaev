import { ExtensionOutlined, RocketLaunchOutlined } from '@mui/icons-material';
import { SvgIcon, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { LogoFiligranIcon } from 'filigran-icon';
import { type ReactNode } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ConnectorItem } from './catalog-facets';

interface HeroStatChipProps {
  icon: ReactNode;
  value: number;
  label: string;
}

// Small stat pill of the hero (same design as the OpenCTI marketplace hero).
const HeroStatChip = ({ icon, value, label }: HeroStatChipProps) => {
  const theme = useTheme();
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(0.75),
        padding: theme.spacing(0.5, 1.25),
        borderRadius: theme.shape.borderRadius,
        border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
        backgroundColor: alpha(theme.palette.text.primary, 0.04),
      }}
    >
      {icon}
      <Typography sx={{
        fontSize: 13,
        fontWeight: 600,
        fontVariantNumeric: 'tabular-nums',
      }}
      >
        {value}
      </Typography>
      <Typography sx={{
        fontSize: 13,
        color: 'text.secondary',
      }}
      >
        {label}
      </Typography>
    </div>
  );
};

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

  return (
    <header
      style={{
        position: 'relative',
        overflow: 'hidden',
        borderRadius: theme.shape.borderRadius,
        border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        backgroundColor: theme.palette.background.paper,
        padding: theme.spacing(3),
      }}
    >
      {/* Decorative glow, purely visual. */}
      <div
        aria-hidden
        style={{
          pointerEvents: 'none',
          position: 'absolute',
          top: -100,
          right: -60,
          width: 260,
          height: 260,
          borderRadius: '50%',
          background: alpha(theme.palette.primary.main, 0.08),
          filter: 'blur(60px)',
        }}
      />
      <div style={{
        position: 'relative',
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: theme.spacing(2),
        flexWrap: 'wrap',
      }}
      >
        <div>
          <Typography
            variant="h1"
            sx={{
              fontWeight: 700,
              fontSize: 22,
              marginBottom: 0.5,
            }}
          >
            {title ?? t('Connector catalog')}
          </Typography>
          <Typography
            variant="body2"
            sx={{
              color: 'text.secondary',
              maxWidth: 640,
            }}
          >
            {subtitle ?? t('Browse, filter and deploy collectors, injectors and executors from the XTM ecosystem.')}
          </Typography>
          <div style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: theme.spacing(1),
            marginTop: theme.spacing(2),
          }}
          >
            <HeroStatChip
              icon={(
                <RocketLaunchOutlined sx={{
                  fontSize: 16,
                  color: 'primary.main',
                }}
                />
              )}
              value={deployedInstancesCount}
              label={t('Deployed')}
            />
            <HeroStatChip
              icon={(
                <ExtensionOutlined sx={{
                  fontSize: 16,
                  color: 'primary.main',
                }}
                />
              )}
              value={totalCount}
              label={t('Available connectors')}
            />
            <HeroStatChip
              icon={(
                <SvgIcon
                  component={LogoFiligranIcon}
                  inheritViewBox
                  sx={{
                    fontSize: 16,
                    color: 'primary.main',
                  }}
                />
              )}
              value={filigranSupportedCount}
              label={t('Supported by Filigran')}
            />
          </div>
        </div>
      </div>
    </header>
  );
};

export default CatalogHero;
