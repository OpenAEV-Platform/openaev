import { Icon } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type EsSeries, type EsSeriesData } from '../../../../utils/api-types';
import { capitalize } from '../../../../utils/String';
import { calcPercentage, formatPercentage } from '../../workspaces/custom_dashboards/widgets/viz/domains/SecurityDomainsWidgetUtils';
import { colorByAverage, colorByLabel } from '../ColorByResult';
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
  results: EsSeries[] | undefined;
  inline?: boolean;
}

const ExpectationResultByType: FunctionComponent<Props> = ({ results, inline }) => {
  const { classes } = useStyles();
  const theme = useTheme();

  return (
    inline
      ? (
          results?.map((result: EsSeries) => {
            let successValue = 0;
            result.data?.map((d: EsSeriesData) => {
              if (d.key === 'success') {
                successValue = d.value ?? 0;
              }
            });
            const successRate = result.value ? calcPercentage(successValue, result.value) : -1;
            return (
              <div
                key={result.label}
                style={{ height: theme.spacing(3) }}
              >
                <div className={classes.inline}>
                  <Icon
                    key={result.label}
                    sx={{
                      color: colorByAverage(successRate),
                      height: theme.spacing(4),
                    }}
                  >
                    {expectationIconByType(result.label)}
                  </Icon>
                  {result.label && <span style={{ fontSize: theme.typography.body2.fontSize }}>{capitalize(result.label)}</span>}
                  {result.data?.map((d: EsSeriesData) => {
                    return (
                      <div className={classes.inline} key={d.key}>
                        {
                          d.label && d.value && result.value && (
                            <span style={{
                              color: colorByLabel(d.label),
                              fontSize: theme.typography.h4.fontSize,
                            }}
                            >
                              {formatPercentage(calcPercentage(d.value, result.value), 1)}
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
            {results?.map((result: EsSeries) => {
              let successValue = 0;
              result.data?.map((d) => {
                if (d.key === 'success') {
                  successValue = d.value ? d.value : 0;
                }
              });
              const successRate = result.value ? calcPercentage(successValue, result.value) : -1;
              return (
                <div key={result.label}>
                  <Icon
                    key={result.label}
                    sx={{
                      color: colorByAverage(successRate),
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
