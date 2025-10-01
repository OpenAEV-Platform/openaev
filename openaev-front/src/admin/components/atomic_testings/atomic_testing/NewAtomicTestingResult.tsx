import { BugReportOutlined, SensorOccupiedOutlined, ShieldOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type InjectTarget } from '../../../../utils/api-types';

const useStyles = makeStyles()(() => ({
  inline: {
    display: 'flex',
    alignItems: 'center',
    padding: 0,
    justifyContent: 'flex-end',
  },
}));

interface Props { target: InjectTarget }

const NewAtomicTestingResult: FunctionComponent<Props> = ({ target }) => {
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

  return (
    <div className={classes.inline}>
      {'target_prevention_status' in target && (
        <Tooltip title={t('Prevention')}>
          <ShieldOutlined
            style={{
              color: getColor(target.target_prevention_status),
              marginRight: theme.spacing(2),
              fontSize: 22,
            }}
          />
        </Tooltip>
      )}
      {'target_detection_status' in target && (
        <Tooltip title={t('Detection')}>
          <TrackChangesOutlined
            style={{
              color: getColor(target.target_detection_status),
              marginRight: theme.spacing(2),
              fontSize: 22,
            }}
          />
        </Tooltip>
      )}
      {'target_vulnerability_status' in target && (
        <Tooltip title={t('Vulnerability')}>
          <BugReportOutlined
            style={{
              color: getColor(target.target_vulnerability_status),
              marginRight: theme.spacing(2),
              fontSize: 22,
            }}
          />
        </Tooltip>
      )}
      {'target_human_response_status' in target && (
        <Tooltip title={t('Human Response')}>
          <SensorOccupiedOutlined
            style={{
              color: getColor(target.target_human_response_status),
              marginRight: theme.spacing(2),
              fontSize: 22,
            }}
          />
        </Tooltip>
      )}
    </div>
  );
};

export default NewAtomicTestingResult;
