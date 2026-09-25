import { Button, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Icon, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { capitalize } from '../../../../utils/String';
import {
  type EsExpectationByDomainTypeAndStatus,
  formatPercentage,
} from '../../workspaces/custom_dashboards/widgets/viz/domains/SecurityDomainsWidgetUtils';
import expectationIconByType from '../ExpectationIconByType';

interface Props {
  expectationType: string;
  color: string;
  datasByDomainsAndType: EsExpectationByDomainTypeAndStatus[];
  onExpectationResultClick: (expectationStatus: string) => void;
}

const ExpectationPercentResultByType: FunctionComponent<Props> = ({ expectationType, color, datasByDomainsAndType, onExpectationResultClick }) => {
  const theme = useTheme();
  return (
    <div style={{
      display: 'flex',
      alignItems: 'baseline',
      gap: theme.spacing(0.5),
    }}
    >
      <Icon sx={{ color }}>
        {expectationIconByType(expectationType)}
      </Icon>
      <Typography sx={{ whiteSpace: 'nowrap' }}>
        {`${capitalize(expectationType)} :`}
      </Typography>
      {datasByDomainsAndType.map(d => (
        <Tooltip key={`${expectationType}-${d.key}`}>
          <TooltipTrigger asChild>
            <Button
              type="button"
              priority="tertiary"
              size="sm"
              onClick={() => onExpectationResultClick(d.key)}
              style={{
                color: d.color,
                margin: 0,
                minWidth: 0,
              }}
            >
              {formatPercentage(d.percentage ?? 0, 1)}
            </Button>
          </TooltipTrigger>
          {d.label && <TooltipContent>{d.label}</TooltipContent>}
        </Tooltip>
      ))}
    </div>
  );
};

export default ExpectationPercentResultByType;
