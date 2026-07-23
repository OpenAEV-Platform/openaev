import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Fragment, type FunctionComponent, useMemo } from 'react';

import { type InjectStore } from '../../../../../actions/injects/Inject';
import { useFormatter } from '../../../../../components/i18n';
import { isNotEmptyField } from '../../../../../utils/utils';
import InjectIcon from '../../../common/injects/InjectIcon';
import SamplePreview from '../../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import { sampleFlowInjects } from './executionSampleData';

// Maximum icons stacked per time bucket before collapsing into a "+N" badge.
const MAX_ICONS_PER_GROUP = 3;
// Horizontal padding (in %) so the first / last markers never touch the edges.
const EDGE_PADDING = 6;
// Bucket granularity (in % of the axis) used to group near-simultaneous sends.
const BUCKET_SIZE = 2.5;

interface SentInject {
  inject: InjectStore;
  sentAt: number;
}

const toSentInjects = (injects: InjectStore[]): SentInject[] => injects
  .filter(inject => inject.inject_status?.tracking_sent_date)
  .map(inject => ({
    inject,
    sentAt: new Date(inject.inject_status!.tracking_sent_date!).getTime(),
  }))
  .sort((a, b) => a.sentAt - b.sentAt);

interface Props { injects: InjectStore[] }

// Chronological "execution flow" strip: every sent inject is plotted on a
// single time axis at the moment it was fired (grouped when near-simultaneous).
// Unlike the previous cumulative area chart, the strip reads naturally with a
// single inject (one milestone on the axis) as well as with hundreds (dense
// stacked groups), so it works for short and long simulations alike. While
// nothing has been sent yet it previews greyed sample data ("Sample" chip),
// like every widget of the platform.
const ExecutionFlowStrip: FunctionComponent<Props> = ({ injects }) => {
  const theme = useTheme();
  const { fndt } = useFormatter();

  const realSent = useMemo(() => toSentInjects(injects), [injects]);
  const isSample = realSent.length === 0;
  const sent = useMemo(
    () => (isSample ? toSentInjects(sampleFlowInjects()) : realSent),
    [isSample, realSent],
  );

  const start = sent[0].sentAt;
  const end = sent[sent.length - 1].sentAt;
  const span = end - start;
  const positionFor = (timestamp: number) => (span === 0
    ? 50
    : EDGE_PADDING + ((timestamp - start) / span) * (100 - 2 * EDGE_PADDING));

  // Group injects sent (almost) at the same time into a single stacked marker.
  const groups = useMemo(() => {
    const map = new Map<number, SentInject[]>();
    sent.forEach((sentInject) => {
      const bucket = Math.round(positionFor(sentInject.sentAt) / BUCKET_SIZE) * BUCKET_SIZE;
      map.set(bucket, [...(map.get(bucket) ?? []), sentInject]);
    });
    return [...map.entries()];
  }, [sent]);

  const axisColor = alpha(theme.palette.text.primary, 0.15);
  const labelSx = {
    position: 'absolute' as const,
    bottom: 0,
    transform: 'translateX(-50%)',
    fontFamily: 'Consolas, monaco, monospace',
    fontSize: 11,
    color: 'text.secondary',
    whiteSpace: 'nowrap' as const,
  };
  // Only render the middle label when it will not repeat the edge labels.
  const showMidLabel = span >= 10 * 60_000;

  return (
    <SamplePreview active={isSample}>
      <Box sx={{
        position: 'relative',
        height: 132,
      }}
      >
        {/* Time axis */}
        <Box sx={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: 30,
          height: '1px',
          backgroundColor: axisColor,
        }}
        />
        {/* Sent inject markers */}
        {groups.map(([position, groupInjects]) => {
          const visible = groupInjects.slice(0, MAX_ICONS_PER_GROUP);
          const overflow = groupInjects.length - visible.length;
          const hasError = groupInjects.some(({ inject }) => inject.inject_status?.status_name === 'ERROR');
          return (
            <Box
              key={position}
              sx={{
                position: 'absolute',
                bottom: 26,
                left: `${position}%`,
                transform: 'translateX(-50%)',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '3px',
              }}
            >
              {overflow > 0 && (
                <Typography sx={{
                  fontSize: 10,
                  fontWeight: 600,
                  color: 'text.secondary',
                }}
                >
                  {`+${overflow}`}
                </Typography>
              )}
              {visible.map(({ inject, sentAt }) => (
                <InjectIcon
                  key={inject.inject_id}
                  isPayload={isNotEmptyField(inject.inject_injector_contract?.injector_contract_payload)}
                  type={
                    inject.inject_injector_contract?.injector_contract_payload
                      ? inject.inject_injector_contract.injector_contract_payload?.payload_collector_type
                      || inject.inject_injector_contract.injector_contract_payload?.payload_type
                      : inject.inject_type
                  }
                  done={inject.inject_status?.status_name !== 'ERROR'}
                  size="small"
                  variant="timeline"
                  tooltip={(
                    <Fragment>
                      {inject.inject_title}
                      <br />
                      <span style={{
                        display: 'block',
                        textAlign: 'center',
                        fontWeight: 'bold',
                      }}
                      >
                        {fndt(new Date(sentAt))}
                      </span>
                    </Fragment>
                  )}
                />
              ))}
              {/* Tick crossing the axis, red when the group contains an error */}
              <Box sx={{
                width: '2px',
                height: 10,
                borderRadius: 1,
                backgroundColor: hasError ? theme.palette.error.main : theme.palette.primary.main,
              }}
              />
            </Box>
          );
        })}
        {/* Time labels */}
        {span === 0 ? (
          <Typography sx={{
            ...labelSx,
            left: '50%',
          }}
          >
            {fndt(new Date(start))}
          </Typography>
        ) : (
          <>
            <Typography sx={{
              ...labelSx,
              left: `${EDGE_PADDING}%`,
            }}
            >
              {fndt(new Date(start))}
            </Typography>
            {showMidLabel && (
              <Typography sx={{
                ...labelSx,
                left: '50%',
              }}
              >
                {fndt(new Date(start + span / 2))}
              </Typography>
            )}
            <Typography sx={{
              ...labelSx,
              left: `${100 - EDGE_PADDING}%`,
            }}
            >
              {fndt(new Date(end))}
            </Typography>
          </>
        )}
      </Box>
    </SamplePreview>
  );
};

export default ExecutionFlowStrip;
