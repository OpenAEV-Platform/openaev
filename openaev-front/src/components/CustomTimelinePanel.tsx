import { useTheme } from '@mui/material/styles';
import { type BackgroundProps, Panel, type ReactFlowState, useStore, type Viewport } from '@xyflow/react';
import moment from 'moment-timezone';
import { type CSSProperties, memo, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';
import { shallow } from 'zustand/shallow';

import { useFormatter } from './i18n';

const selector = (s: ReactFlowState) => ({
  transform: s.transform,
  patternId: `pattern-${s.rfId}`,
});

// @ts-expect-error pointer events is important and is mandatory
const useStyles = makeStyles()(() => ({
  panel: {
    pointerEvents: 'none !important',
    width: '100%',
    height: '100%',
    margin: '0px 0px 0px 5px !important',
  },
  dateLabel: { textAnchor: 'middle' },
}));

interface Props extends BackgroundProps {
  minutesPerGap: number;
  gap?: number | [number, number];
  viewportData?: Viewport;
  startDate: string | undefined;
}

interface TimelineDates {
  parsedDate: string;
  dateIndex: number;
}

/**
 * Panel used to display the date for each vertical lines
 * @param style the style of the panel
 * @param gap the size of the gaps
 * @param minutesPerGap the number of minutes per gap
 * @param viewportData viewport datas
 * @param startDate the start date
 * @constructor
 */
function BackgroundComponent({
  style,
  gap = 125,
  minutesPerGap = 5,
  viewportData,
  startDate = undefined,
}: Props) {
  const theme = useTheme();
  const { classes } = useStyles();
  const { ft, fld, vnsdt } = useFormatter();

  const { transform } = useStore(selector, shallow);
  const [parsedDates, setParsedDates] = useState<TimelineDates[]>([]);

  const gapXY: [number, number] = Array.isArray(gap) ? gap : [gap, gap];
  const scaledGap: [number, number] = [gapXY[0] * transform[2] || 1, gapXY[1] * transform[2] || 1];
  const desiredFontSize = 12;
  const numberOfIntervals = 6 / transform[2];

  useEffect(() => {
    const horizontalGap = scaledGap[0] * 3;
    const offset = Math.floor(Math.abs(transform[0]) / horizontalGap) * minutesPerGap * 3;
    const newParsedDates = [];

    for (let i = 0; i < numberOfIntervals; i += 1) {
      if (startDate === undefined) {
        const date = moment.utc(moment.duration(0, 'd').add((minutesPerGap * 3 * i) + offset, 'm').asMilliseconds());
        newParsedDates.push({
          parsedDate: `${date.dayOfYear() - 1} d, ${date.hour()} h, ${date.minute()} m`,
          dateIndex: Math.round(date.unix() / (minutesPerGap * 3 * 60)),
        });
      } else {
        const beginningDate = moment.utc(startDate);
        const date = moment.utc(beginningDate)
          .add((minutesPerGap * 3 * i) + offset, 'm');

        newParsedDates.push({
          parsedDate: viewportData === undefined || viewportData?.zoom > 0.5
            ? `${fld(date.toDate())} - ${ft(date.toDate())}`
            : `${vnsdt(date.toDate())}`,
          dateIndex: Math.round((date.unix() - beginningDate.unix()) / (minutesPerGap * 3 * 60)),
        });
      }
    }
    setParsedDates(newParsedDates);
  }, [viewportData, minutesPerGap, startDate]);

  return (
    <Panel className={classes.panel}>
      <svg
        style={
          {
            ...style,
            width: '100%',
            height: '100%',
            top: 0,
            left: 0,
          } as CSSProperties
        }
      >
        {parsedDates.map((parsedDate, index) => (
          <text key={`date_label_${index}`} fill={theme.palette.mode === 'dark' ? '#ffffff' : '#000000'} className={classes.dateLabel} fontSize={desiredFontSize} fontFamily="Verdana" x={transform[0] + (parsedDate.dateIndex * 3 * scaledGap[1])} y={desiredFontSize}>
            {parsedDate.parsedDate}
          </text>
        ))}
      </svg>
    </Panel>
  );
}

BackgroundComponent.displayName = 'CustomTimelinePanel';

const CustomTimelinePanel = memo(BackgroundComponent);
export default CustomTimelinePanel;
