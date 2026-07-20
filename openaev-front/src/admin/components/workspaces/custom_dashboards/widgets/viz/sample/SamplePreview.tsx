import { Box, Chip } from '@mui/material';
import { type ReactNode } from 'react';

import { useFormatter } from '../../../../../../../components/i18n';

interface Props {
  active: boolean;
  children: ReactNode;
  /**
   * 'full' greys the content entirely (grayscale + dim); 'subtle' only
   * desaturates it partially, for hero widgets where motion and color are
   * part of the message even in preview mode.
   */
  variant?: 'full' | 'subtle';
}

/**
 * Wraps a widget visualization. When `active`, the content is rendered greyed
 * out (grayscale + reduced opacity, non-interactive) with a small "Sample"
 * chip, so users can preview the final look of the widget before any real
 * data exists.
 */
const SamplePreview = ({ active, children, variant = 'full' }: Props) => {
  const { t } = useFormatter();

  if (!active) {
    return <>{children}</>;
  }

  return (
    <Box
      sx={{
        position: 'relative',
        height: '100%',
        width: '100%',
      }}
    >
      <Box
        sx={{
          height: '100%',
          width: '100%',
          filter: variant === 'full' ? 'grayscale(1)' : 'grayscale(0.6)',
          opacity: variant === 'full' ? 0.45 : 0.8,
          pointerEvents: 'none',
          userSelect: 'none',
        }}
      >
        {children}
      </Box>
      <Chip
        label={t('Sample')}
        size="small"
        variant="outlined"
        sx={{
          position: 'absolute',
          top: 0,
          right: 0,
          height: 18,
          fontSize: 9,
          fontWeight: 600,
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
          color: 'text.disabled',
          borderColor: 'divider',
          backgroundColor: 'background.paper',
        }}
      />
    </Box>
  );
};

export default SamplePreview;
