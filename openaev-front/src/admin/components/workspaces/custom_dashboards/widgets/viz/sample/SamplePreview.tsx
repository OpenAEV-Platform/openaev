import { Chip } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type ReactNode, useEffect } from 'react';

import { useFormatter } from '../../../../../../../components/i18n';
import { useReportSample } from './SampleContext';

interface Props {
  active: boolean;
  children: ReactNode;
  /** 'full' greys the content entirely; 'subtle' only desaturates it. */
  variant?: 'full' | 'subtle';
  /**
   * Set when the preview fills a panel from its top edge: the marker reaches 8px
   * back out of the panel's 16px padding. Leave it off when the preview starts
   * below something else, or the marker climbs onto it.
   */
  atPanelEdge?: boolean;
}

/** Wraps a widget visualization, greyed out with a "Sample" chip when `active`. */
const SamplePreview = ({ active, children, variant = 'full', atPanelEdge = false }: Props) => {
  const { t } = useFormatter();
  const reportSample = useReportSample();

  useEffect(() => {
    reportSample?.(active);
    return () => reportSample?.(false);
  }, [active, reportSample]);

  if (!active) {
    return <>{children}</>;
  }

  const content = (
    <Box
      sx={{
        flex: atPanelEdge ? 1 : undefined,
        minHeight: atPanelEdge ? 0 : undefined,
        height: atPanelEdge ? undefined : '100%',
        width: '100%',
        filter: variant === 'full' ? 'grayscale(1)' : 'grayscale(0.6)',
        opacity: variant === 'full' ? 0.45 : 0.8,
        pointerEvents: 'none',
        userSelect: 'none',
      }}
    >
      {children}
    </Box>
  );

  // Inside a widget card the marker is drawn by the title row instead
  // (SampleContext), so it sits in the card's corner level with the title.
  if (reportSample) {
    return (
      <Box sx={{
        height: '100%',
        width: '100%',
      }}
      >
        {content}
      </Box>
    );
  }

  // At a panel's top edge the marker gets a row of its own, so it never lands
  // on the content it labels, and reaches 8px back out of the panel's own 16px
  // padding. Anywhere else it stays in the corner, out of the flow: the preview
  // may be filling a panel of a fixed height, which a reserved row would
  // overflow.
  if (atPanelEdge) {
    return (
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        width: '100%',
      }}
      >
        <Box sx={{
          display: 'flex',
          justifyContent: 'flex-end',
          flexShrink: 0,
          marginTop: -1,
          marginRight: -1,
          paddingBottom: 1,
        }}
        >
          <Chip label={t('Sample')} severity="neutral" />
        </Box>
        {content}
      </Box>
    );
  }

  return (
    <Box
      sx={{
        position: 'relative',
        height: '100%',
        width: '100%',
      }}
    >
      {content}
      <Chip
        label={t('Sample')}
        severity="neutral"
        style={{
          position: 'absolute',
          // Out of the panel's 16px padding by half, so the marker sits 8px off
          // its top and right edges - the same corner the reserved row puts it
          // in, without a row this preview may have no room for.
          top: -8,
          right: -8,
          // Above the preview it labels, always: the faded content below can
          // carry its own positioned children, and a positioned sibling with
          // no z-index paints in DOM order only until one of them raises
          // itself.
          zIndex: 1,
        }}
      />
    </Box>
  );
};

export default SamplePreview;
