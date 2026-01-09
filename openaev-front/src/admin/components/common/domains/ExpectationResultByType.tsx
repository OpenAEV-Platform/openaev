import { FunctionComponent } from 'react';
import { EsSeries, EsSeriesData } from '../../../../utils/api-types';
import { makeStyles } from 'tss-react/mui';
import { Icon } from '@mui/material';
import { colorByAverage, colorByLabel } from '../ColorByResult';
import { expectationIconByType } from '../ExpectationIconByType';
import { useTheme } from '@mui/material/styles';
import { calcPercentage, formatPercentage } from '../../workspaces/custom_dashboards/widgets/viz/domains/SecurityDomainsWidgetUtils';

const useStyles = makeStyles()({
  contained: {
    display: 'flex',
    gap: 2,
    padding: 0,
  },
});

interface Props{
  results: EsSeries[] | undefined;
  inline?: boolean;
}

const ExpectationResultByType: FunctionComponent<Props> = ({results, inline}) => {

  const { classes } = useStyles();
  const theme = useTheme();

  const capitalize = (word: string): string => {
    if (!word) return "";
    return word.charAt(0).toUpperCase() + word.slice(1);
  }

  return(
    inline ? (
      results?.map((result: EsSeries) => {
        let successValue = 0;
        result.data?.map((d: EsSeriesData) => {
          if (d.key === "success"){
            successValue =  d.value ? d.value : 0;
          }
        });
        const successRate = result.value ? calcPercentage(successValue,result.value) : 0;
        return (
          <div
          style={{
            height: theme.spacing(3)
          }}
          >
            <div style={{
              display: 'flex',
              gap: 4,
              alignItems: 'baseline'
            }}>
              <Icon
                key={result.label}
                sx={{
                  color: result.data !== undefined ? colorByAverage(successRate) : 'transparent'
                }}
              >
                {expectationIconByType(result.label)}
              </Icon>
              {result.label && <span style={{ fontSize: theme.typography.body2.fontSize }}>{capitalize(result.label)}</span>}
              {result.data?.map((d:EsSeriesData) => {
                return (
                  <div style={{
                    display: 'flex',
                    gap: 4,
                    alignItems: 'baseline'
                  }}>
                    {
                      d.label && d.value && result.value && (
                        <span style={{ color: colorByLabel(d.label), fontSize: theme.typography.h4.fontSize }}>{formatPercentage(calcPercentage(d.value, result.value), 1)}</span>
                      )
                    }

                  </div>
                );
              })}
            </div>
          </div>
        );
      })
    ) : (
      <div className={classes.contained}>
        {results?.map((result: EsSeries) => {
          let successValue = 0;
          result.data?.map((d => {
            if (d.key === "success"){
              successValue =  d.value ? d.value : 0;
            }
          }))
          const successRate = result.value ? calcPercentage(successValue,result.value) : 0;
          return (
            <div style={{ flexGrow: 1 }}>
              <Icon
                key={result.label}
                sx={{
                  color: result.data !== undefined ? colorByAverage(successRate) : 'transparent'
                }}
              >
                {expectationIconByType(result.label)}
              </Icon>
            </div>
          )
        })}
      </div>
    )
  );
};

export default ExpectationResultByType;
