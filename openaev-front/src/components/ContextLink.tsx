import { Chip, Tooltip } from '@mui/material';
import { type FunctionComponent, type ReactElement } from 'react';
import { Link } from 'react-router';

import { truncate } from '../utils/String';

interface Props {
  title: string;
  url: string;
  // Optional leading entity-type icon (inject / simulation / scenario / ...).
  icon?: ReactElement;
  /**
   * 'list' (default): ultra-dense 20px chip aligned with the ItemTargets
   * chips inside table rows. 'field': standard 25px detail-field chip
   * (ItemSeverity & co) for detail page fields, where the dense chip is
   * not visible enough.
   */
  variant?: 'list' | 'field';
}

// Pivot button towards another entity. Shares the exact chip anatomy of the
// target chips rendered by ItemTargets (outlined, 20px tall, 4px radius) so
// every context column (endpoints / injects / simulations / scenarios) exposes
// one single, clearly clickable shape. `clickable` brings the standard button
// feedback (hover background + ripple) on top of the primary-tinted border.
const ContextLink: FunctionComponent<Props> = ({
  title,
  url,
  icon,
  variant = 'list',
}) => {
  return (
    <Tooltip title={title}>
      <Chip
        variant="outlined"
        clickable
        component={Link}
        to={url}
        icon={icon}
        label={truncate(title, 30)}
        sx={{
          'fontSize': 12,
          'height': variant === 'field' ? 25 : 20,
          'borderRadius': 1,
          'maxWidth': '100%',
          '& .MuiChip-icon': { fontSize: '1rem' },
          '&:hover': {
            borderColor: 'primary.main',
            color: 'primary.main',
          },
        }}
      />
    </Tooltip>
  );
};

export default ContextLink;
