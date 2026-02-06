import { Button, Icon, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { capitalize } from '../../../../utils/String';
import {
  colorByLabel, type EsExpectationByDomainTypeAndStatus,
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
      <Typography>
        {`${capitalize(expectationType)} :`}
      </Typography>
      {datasByDomainsAndType.map(d => (
        <Tooltip
          key={d.key}
          onClick={() => onExpectationResultClick(d.label)}
          style={{ color: colorByLabel(d.label, theme) }}
          title={d.label}
        >
          <Button
            size="small"
            sx={{
              margin: 0,
              minWidth: 0,
              fontSize: theme.typography.fontSize,
            }}
            variant="text"
          >
            {formatPercentage(d.percentage ?? 0, 1)}
          </Button>
        </Tooltip>
      ))}
    </div>
  );
};

export default ExpectationPercentResultByType;
