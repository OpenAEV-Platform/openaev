import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type InjectTarget } from '../../../../utils/api-types';
import { expectationResultColor, expectationTypeIcon } from '../../common/ExpectationIconByType';
import { type ExpectationResultType, expectationTypeLabelKey } from '../../common/injects/expectations/Expectation';

const useStyles = makeStyles()(() => ({
  inline: {
    display: 'flex',
    alignItems: 'center',
    padding: 0,
    justifyContent: 'flex-end',
  },
}));

interface Props { target: InjectTarget }

type TargetStatusField = Extract<keyof InjectTarget, `target_${string}_status`>;

// Each expectation status column of a target, in display order.
const TARGET_STATUS_FIELDS: {
  type: ExpectationResultType;
  field: TargetStatusField;
}[] = [
  {
    type: 'PREVENTION',
    field: 'target_prevention_status',
  },
  {
    type: 'DETECTION',
    field: 'target_detection_status',
  },
  {
    type: 'VULNERABILITY',
    field: 'target_vulnerability_status',
  },
  {
    type: 'HUMAN_RESPONSE',
    field: 'target_human_response_status',
  },
];

const NewAtomicTestingResult: FunctionComponent<Props> = ({ target }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { classes } = useStyles();

  return (
    <div className={classes.inline}>
      {TARGET_STATUS_FIELDS.filter(({ field }) => field in target).map(({ type, field }) => {
        const Icon = expectationTypeIcon(type);
        return (
          <Tooltip key={type}>
            <TooltipTrigger asChild>
              <Icon
                style={{
                  color: expectationResultColor(target[field]),
                  marginRight: theme.spacing(2),
                  fontSize: 22,
                }}
              />
            </TooltipTrigger>
            <TooltipContent>{t(expectationTypeLabelKey(type) ?? type)}</TooltipContent>
          </Tooltip>
        );
      })}
    </div>
  );
};

export default NewAtomicTestingResult;
