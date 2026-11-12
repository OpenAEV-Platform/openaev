import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type ExpectationResultsByType, type InjectResultOutput } from '../../../../utils/api-types';
import { expectationResultColor, expectationTypeIcon } from '../../common/ExpectationIconByType';
import { expectationResultTypes, expectationTypeLabelKey } from '../../common/injects/expectations/Expectation';

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
  if (!expectations || expectations.length === 0) {
    return (
      <div className={classes.inline} id={`inject_expectations_${injectId}`}>
        -
      </div>
    );
  }

  return (
    <div className={classes.inline} id={`inject_expectations_${injectId}`}>
      {expectations.sort((a, b) => expectationResultTypes.indexOf(a.type) - expectationResultTypes.indexOf(b.type)).map((expectation, index) => {
        const color = expectationResultColor(expectation.avgResult);
        const IconComponent = expectationTypeIcon(expectation.type);
        const tooltipLabel = t(expectationTypeLabelKey(expectation.type) ?? expectation.type);

        return (
          <Tooltip key={index}>
            <TooltipTrigger asChild>
              <IconComponent
                style={{
                  color,
                  marginRight: theme.spacing(1),
                  fontSize: 22,
                }}
              />
            </TooltipTrigger>
            {tooltipLabel && <TooltipContent>{tooltipLabel}</TooltipContent>}
          </Tooltip>
        );
      })}
    </div>
  );
};

export default AtomicTestingResult;
