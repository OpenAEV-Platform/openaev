import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type InjectTarget } from '../../../../utils/api-types';
import { expectationResultColor, expectationTypeIcon } from '../../common/ExpectationIconByType';

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

  return (
    <div className={classes.inline}>
      {'target_prevention_status' in target && (
        <Tooltip>
          <TooltipTrigger asChild>
            <PreventionIcon
              style={{
                color: expectationResultColor(target.target_prevention_status),
                marginRight: theme.spacing(2),
                fontSize: 22,
              }}
            />
          </TooltipTrigger>
          <TooltipContent>{t('Prevention')}</TooltipContent>
        </Tooltip>
      )}
      {'target_detection_status' in target && (
        <Tooltip>
          <TooltipTrigger asChild>
            <DetectionIcon
              style={{
                color: expectationResultColor(target.target_detection_status),
                marginRight: theme.spacing(2),
                fontSize: 22,
              }}
            />
          </TooltipTrigger>
          <TooltipContent>{t('Detection')}</TooltipContent>
        </Tooltip>
      )}
      {'target_vulnerability_status' in target && (
        <Tooltip>
          <TooltipTrigger asChild>
            <VulnerabilityIcon
              style={{
                color: expectationResultColor(target.target_vulnerability_status),
                marginRight: theme.spacing(2),
                fontSize: 22,
              }}
            />
          </TooltipTrigger>
          <TooltipContent>{t('Vulnerability')}</TooltipContent>
        </Tooltip>
      )}
      {'target_human_response_status' in target && (
        <Tooltip>
          <TooltipTrigger asChild>
            <HumanResponseIcon
              style={{
                color: expectationResultColor(target.target_human_response_status),
                marginRight: theme.spacing(2),
                fontSize: 22,
              }}
            />
          </TooltipTrigger>
          <TooltipContent>{t('Human Response')}</TooltipContent>
        </Tooltip>
      )}
    </div>
  );
};

export default NewAtomicTestingResult;
