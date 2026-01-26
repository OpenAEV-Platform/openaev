import { Icon } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { capitalize } from '../../../../utils/String';
import {
  colorByLabel,
  type EsExpectationDataExtended,
  type EsExpectationExtended,
  formatPercentage,
} from '../../workspaces/custom_dashboards/widgets/viz/domains/SecurityDomainsWidgetUtils';
import expectationIconByType from '../ExpectationIconByType';

const useStyles = makeStyles()({
  contained: {
    display: 'flex',
    gap: 2,
    justifyContent: 'center',
  },
  inline: {
    display: 'flex',
    gap: 4,
    alignItems: 'center',
  },
});

interface Props {
  results: EsExpectationExtended[] | undefined;
  inline?: boolean;
}

const ExpectationResultByType: FunctionComponent<Props> = ({ results, inline }) => {
  const { classes } = useStyles();
  const theme = useTheme();

  return (
    inline
      ? (
          results?.map((result: EsExpectationExtended) => {
            return (
              <div
                key={result.label}
                style={{ height: theme.spacing(3) }}
              >
                <div className={classes.inline}>
                  <Icon
                    key={result.label}
                    sx={{
                      color: result.color,
                      height: theme.spacing(4),
                    }}
                  >
                    {expectationIconByType(result.label)}
                  </Icon>
                  {result.label && <span style={{ fontSize: theme.typography.body2.fontSize }}>{capitalize(result.label)}</span>}
                  {result.data?.map((d: EsExpectationDataExtended) => {
                    return (
                      <div className={classes.inline} key={d.key}>
                        {
                          d.label && d.value && result.value && (
                            <span style={{
                              color: colorByLabel(d.label, theme),
                              fontSize: theme.typography.h4.fontSize,
                            }}
                            >
                              {formatPercentage(d.percentage ?? 0, 1)}
                            </span>
                          )
                        }

                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })
        )
      : (
          <div className={classes.contained}>
            {results?.map((result: EsExpectationExtended) => {
              return (
                <div key={result.label}>
                  <Icon
                    key={result.label}
                    sx={{
                      color: result.color,
                      height: theme.spacing(4),
                    }}
                  >
                    {expectationIconByType(result.label)}
                  </Icon>
                </div>
              );
            })}
          </div>
        )
  );
};

export default ExpectationResultByType;
