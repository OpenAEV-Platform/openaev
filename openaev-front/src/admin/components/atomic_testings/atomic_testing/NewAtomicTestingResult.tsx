import { Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type InjectTarget } from '../../../../utils/api-types';
import { expectationTypeIcon } from '../../common/ExpectationIconByType';

const useStyles = makeStyles()(() => ({
  inline: {
    display: 'flex',
    alignItems: 'center',
    padding: 0,
    justifyContent: 'flex-end',
  },
}));

interface Props { target: InjectTarget }

const PreventionIcon = expectationTypeIcon('PREVENTION');
const DetectionIcon = expectationTypeIcon('DETECTION');
const VulnerabilityIcon = expectationTypeIcon('VULNERABILITY');
const HumanResponseIcon = expectationTypeIcon('HUMAN_RESPONSE');

const NewAtomicTestingResult: FunctionComponent<Props> = ({ target }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { classes } = useStyles();

  const getColor = (result: string | undefined): string => {
    const colorMap: Record<string, string> = {
      SUCCESS: 'rgb(107, 235, 112)',
      PENDING: 'rgb(128,128,128)',
      FAILED: 'rgb(220, 81, 72)',
      UNKNOWN: 'rgba(128,127,127,0.37)',
    };
    return colorMap[result ?? ''] ?? 'rgba(128,127,127,0.37)';
  };

  return (
    <div className={classes.inline}>
      {'target_prevention_status' in target && (
        <Tooltip title={t('Prevention')}>
          <PreventionIcon
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
          <DetectionIcon
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
          <VulnerabilityIcon
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
          <HumanResponseIcon
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
