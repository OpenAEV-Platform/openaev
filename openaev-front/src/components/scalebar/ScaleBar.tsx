import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../i18n';
import { type Scale } from './Tick';

const useStyles = makeStyles()(theme => ({
  scaleBar: { position: 'relative' },
  track: {
    display: 'flex',
    width: '100%',
    height: '5px',
    position: 'relative',
    backgroundColor: theme.palette.success.main!,
  },
  redTrack: {
    backgroundColor: theme.palette.error.main,
    height: '100%',
  },
  labels: {
    display: 'flex',
    justifyContent: 'space-between',
    marginTop: '5px',
  },
  expectationScoreValue: {
    fontSize: 12,
    position: 'relative',
    marginLeft: '5px',
    top: '-18px',
  },
  failureLabel: {
    textAlign: 'center',
    marginTop: '5px',
    fontSize: 12,
  },
  successLabel: {
    fontSize: 12,
    textAlign: 'center',
    position: 'relative',
    top: '-14px',
  },
  label: {
    fontSize: 12,
    padding: '0 4px',
  },
}));

interface Props {
  expectationExpectedScore: number;
  expectationType: string;
}

const ScaleBar: FunctionComponent<Props> = ({ expectationExpectedScore, expectationType }) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const theme = useTheme();
  const scale: Scale = {
    min: {
      value: 0,
      backgroundColor: theme.palette.error.main!,
      label: expectationType === 'VULNERABILITY' ? t('Vulnerable') : t('Failure'),
    },
    max: {
      value: 100,
      backgroundColor: 'blue',
      label: '',
    },
    ticks: [
      {
        value: expectationExpectedScore,
        backgroundColor: theme.palette.success.main!,
        label: expectationType === 'VULNERABILITY' ? t('Not vulnerable') : t('Success'),
      },
    ],
  };
  const isScoreReadable = expectationExpectedScore >= 0 && expectationExpectedScore < 95;

  return (
    <div className={classes.scaleBar}>
      <div className={classes.labels}>
        <span className={classes.label}>{scale.min.value}</span>
        <span className={classes.label}>{scale.max.value}</span>
      </div>
      <div className={classes.track}>
        {isScoreReadable && (
          <>
            <div className={classes.redTrack} style={{ width: `${expectationExpectedScore}%` }}>
              <div className={classes.failureLabel}><span>{scale.min.label}</span></div>
            </div>
            <div style={{ width: `${100 - expectationExpectedScore}%` }}>
              <div className={classes.expectationScoreValue}><span>{expectationExpectedScore}</span></div>
              <div className={classes.successLabel}><span>{scale.ticks[0].label}</span></div>
            </div>
          </>
        )}
        {!isScoreReadable && (
          <>
            <div className={classes.redTrack} style={{ width: '95%' }}>
              {expectationExpectedScore < 100 && (
                <div
                  className={classes.expectationScoreValue}
                  style={{
                    display: 'flex',
                    justifyContent: 'flex-end',
                  }}
                >
                  <span>{expectationExpectedScore}</span>
                </div>
              )}
              <div className={classes.failureLabel}><span>{scale.min.label}</span></div>
            </div>
            <div style={{ width: '5%' }}>
              <div
                className={classes.successLabel}
                style={{
                  top: 0,
                  marginTop: '3px',
                }}
              >
                <span>{scale.ticks[0].label}</span>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default ScaleBar;
