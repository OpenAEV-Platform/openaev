import { Box, Skeleton } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

/**
 * Loading skeletons for the atomic testing / inject overview target panes.
 * Each skeleton mirrors the exact geometry of the loaded content (row height,
 * icon frame, chips, toolbar) so nothing shifts when the data arrives.
 */

/**
 * One row of the target list: framed 32px icon + name + trailing result icons.
 * Mirrors NewTargetListItem (paddingBlock 1, paddingInline 1.5, gap 1.5).
 */
const TargetRowSkeleton: FunctionComponent = () => {
  const theme = useTheme();
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        paddingBlock: 1,
        paddingInline: 1.5,
        gap: 1.5,
        borderLeft: '2px solid transparent',
      }}
    >
      <Skeleton variant="rounded" width={32} height={32} sx={{ flexShrink: 0 }} />
      <Box sx={{
        flex: 1,
        minWidth: 0,
      }}
      >
        <Skeleton variant="text" width="55%" sx={{ fontSize: 13 }} />
      </Box>
      <Skeleton variant="circular" width={22} height={22} sx={{ marginRight: theme.spacing(2) }} />
      <Skeleton variant="circular" width={22} height={22} sx={{ marginRight: theme.spacing(2) }} />
    </Box>
  );
};

/**
 * The bordered target list body (same container as the loaded list in
 * PaginatedTargetTab, including the row dividers).
 */
export const TargetListSkeleton: FunctionComponent<{ rows?: number }> = ({ rows = 4 }) => {
  const theme = useTheme();
  return (
    <Box
      sx={{
        'marginTop': 1,
        'border': `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        'borderRadius': 1,
        'overflow': 'hidden',
        '& > *:not(:first-of-type)': { borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}` },
      }}
    >
      {[...Array(rows)].map((_, index) => (
        <TargetRowSkeleton key={index} />
      ))}
    </Box>
  );
};

/**
 * The whole targets pane while the target-type probes are still answering:
 * tabs bar (right aligned), search + pagination toolbar, then the list body.
 */
export const TargetsPaneSkeleton: FunctionComponent = () => {
  return (
    <>
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'flex-end',
          alignItems: 'center',
          gap: 3,
          height: 48,
          marginBottom: '12px',
        }}
      >
        <Skeleton variant="text" width={64} height={24} />
        <Skeleton variant="text" width={64} height={24} />
      </Box>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          marginTop: '-10px',
          minHeight: 52,
        }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1.25,
        }}
        >
          <Skeleton variant="rounded" width={200} height={30} />
          <Skeleton variant="rounded" width={150} height={30} />
        </Box>
        <Skeleton variant="text" width={140} height={24} />
      </Box>
      <TargetListSkeleton />
    </>
  );
};

/**
 * The "Results by target" pane while targets are still loading: header row
 * (framed icon + name), execution timeline steps, then two expectation
 * sections, mirroring TargetResultsDetail.
 */
export const TargetResultsSkeleton: FunctionComponent = () => {
  return (
    <>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          marginBottom: 3,
        }}
      >
        <Skeleton variant="rounded" width={32} height={32} sx={{ flexShrink: 0 }} />
        <Skeleton variant="text" width="35%" sx={{ fontSize: 15 }} />
      </Box>
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          marginBottom: 4,
          paddingInline: 2,
        }}
      >
        {[...Array(4)].map((_, index) => (
          <Box
            key={index}
            sx={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 1,
            }}
          >
            <Skeleton variant="circular" width={28} height={28} />
            <Skeleton variant="text" width={72} sx={{ fontSize: 11 }} />
          </Box>
        ))}
      </Box>
      {[...Array(2)].map((_, index) => (
        <Box key={index} sx={{ marginBottom: 3 }}>
          <Skeleton variant="text" width={120} sx={{ fontSize: 13 }} />
          <Skeleton variant="rounded" height={76} sx={{ marginTop: 1 }} />
        </Box>
      ))}
    </>
  );
};
