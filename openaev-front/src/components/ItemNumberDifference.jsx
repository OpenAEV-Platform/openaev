import { ArrowDownward, ArrowForward, ArrowUpward } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import * as PropTypes from 'prop-types';

import { useFormatter } from './i18n';

const ItemNumberDifference = ({ difference, description }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  let color = { color: theme.palette.success.main };
  if (difference < 0) color = { color: theme.palette.error.main };
  if (!difference || difference === 0) color = { color: theme.palette.common.grey };

  let Icon = ArrowUpward;
  if (difference < 0) Icon = ArrowDownward;
  if (!difference || difference === 0) Icon = ArrowForward;

  return (
    <div style={{
      ...color,
      fontSize: 12,
      display: 'flex',
      alignItems: 'center',
      gap: theme.spacing(0.25),
      whiteSpace: 'nowrap',
    }}
    >
      <Icon color="inherit" style={{ fontSize: 13 }} />
      <span>{difference ?? ''}</span>
      {description && (
        <span>
          (
          {t(description)}
          )
        </span>
      )}
    </div>
  );
};

ItemNumberDifference.propTypes = {
  difference: PropTypes.number,
  description: PropTypes.string,
};

export default ItemNumberDifference;
