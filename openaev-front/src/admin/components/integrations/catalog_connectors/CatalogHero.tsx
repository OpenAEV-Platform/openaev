import { Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';

interface Props {
  title?: string;
  subtitle?: string;
}

/**
 * Simple marketplace hero: title and subtitle over a decorative glow. Aligned
 * with the OpenCTI integrations hero (which dropped its stat chips); OpenAEV
 * has no ingestion pipeline, so there is no live-metrics ribbon either.
 */
const CatalogHero = ({ title, subtitle }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

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
      <div style={{ position: 'relative' }}>
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
      </div>
    </header>
  );
};

export default CatalogHero;
