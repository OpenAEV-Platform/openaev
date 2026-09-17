import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type FunctionComponent, type ReactElement } from 'react';

import { statusSeverity } from '../utils/statusUtils';

interface ItemStatusProps {
  label: string;
  status?: string | null;
  tooltipLabel?: string;
  variant?: 'inList';
  isInject?: boolean;
  /** Optional leading icon; inherits the chip's status color. */
  icon?: ReactElement;
}

const ItemStatus: FunctionComponent<ItemStatusProps> = ({
  label,
  status,
  tooltipLabel,
  icon,
}) => {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Chip severity={statusSeverity(status)} label={label} startIcon={icon} />
      </TooltipTrigger>
      {(tooltipLabel ?? label) && <TooltipContent>{tooltipLabel ?? label}</TooltipContent>}
    </Tooltip>
  );
};

export default ItemStatus;
