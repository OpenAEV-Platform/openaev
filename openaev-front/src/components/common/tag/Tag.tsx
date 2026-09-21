import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import type React from 'react';

import { useFormatter } from '../../i18n';

interface TagProps {
  label?: string | number | null;
  color?: string | null;
  onClick?: (e: React.MouseEvent) => void;
  onDelete?: () => void;
  maxWidth?: number | string;
  icon?: React.ReactElement;
  tooltipTitle?: string;
  disableTooltip?: boolean;
  labelTextTransform?: 'capitalize' | 'uppercase' | 'lowercase' | 'none';
}

const Tag = ({
  label,
  color,
  onClick,
  onDelete,
  maxWidth = '100%',
  icon,
  tooltipTitle,
  disableTooltip = false,
  labelTextTransform = 'capitalize',
}: TagProps) => {
  const { t } = useFormatter();
  const text = label == null ? '' : String(label);

  const chip = (
    <Chip
      label={text}
      startIcon={icon}
      onClick={onClick}
      onDelete={onDelete}
      deleteLabel={t('Remove')}
      color={color ?? undefined}
      style={{ maxWidth: typeof maxWidth === 'number' ? `${maxWidth}px` : maxWidth }}
    />
  );

  if (disableTooltip) {
    return chip;
  }

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        {onClick ? chip : <span className="inline-flex">{chip}</span>}
      </TooltipTrigger>
      <TooltipContent side="bottom" align="start" style={{ textTransform: labelTextTransform }}>
        {tooltipTitle ?? text}
      </TooltipContent>
    </Tooltip>
  );
};

export default Tag;
