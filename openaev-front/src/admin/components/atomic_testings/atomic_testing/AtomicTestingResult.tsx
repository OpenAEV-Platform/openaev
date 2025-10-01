import { BugReportOutlined, SensorOccupiedOutlined, ShieldOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type ExpectationResultsByType, type InjectResultOutput } from '../../../../utils/api-types';
import { expectationResultTypes } from '../../common/injects/expectations/Expectation';

const useStyles = makeStyles()(() => ({
  inline: {
    display: 'flex',
    alignItems: 'center',
    padding: 0,
  },
}));

interface Props {
  expectations: ExpectationResultsByType[] | undefined;
  injectId?: InjectResultOutput['inject_id'];
}

const AtomicTestingResult: FunctionComponent<Props> = ({ expectations, injectId }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { classes } = useStyles();
  const getColor = (result: string | undefined): string => {
    const colorMap: Record<string, string> = {
      SUCCESS: 'rgb(107, 235, 112)',
      PARTIAL: 'rgb(245, 166, 35)',
      PENDING: 'rgb(128,128,128)',
      FAILED: 'rgb(220, 81, 72)',
      UNKNOWN: 'rgba(128,127,127,0.37)',
    };
    return colorMap[result ?? ''] ?? 'rgb(245, 166, 35)';
  };

  if (!expectations || expectations.length === 0) {
    return <div className={classes.inline} id={`inject_expectations_${injectId}`}><p>&nbsp;</p></div>;
  }

  return (
    <div className={classes.inline} id={`inject_expectations_${injectId}`}>
      {expectations.sort((a, b) => expectationResultTypes.indexOf(a.type) - expectationResultTypes.indexOf(b.type)).map((expectation, index) => {
        const color = getColor(expectation.avgResult);
        let IconComponent;
        let tooltipLabel = '';

        switch (expectation.type) {
          case 'PREVENTION':
            tooltipLabel = t('Prevention');
            IconComponent = ShieldOutlined;
            break;
          case 'DETECTION':
            tooltipLabel = t('Detection');
            IconComponent = TrackChangesOutlined;
            break;
          case 'VULNERABILITY':
            tooltipLabel = t('Vulnerability');
            IconComponent = BugReportOutlined;
            break;
          case 'HUMAN_RESPONSE':
          default:
            tooltipLabel = t('Human Response');
            IconComponent = SensorOccupiedOutlined;
            break;
        }

        return (
          <Tooltip key={index} title={tooltipLabel}>
            <IconComponent
              style={{
                color,
                marginRight: theme.spacing(1),
                fontSize: 22,
              }}
            />
          </Tooltip>
        );
      })}
    </div>
  );
};

export default AtomicTestingResult;
