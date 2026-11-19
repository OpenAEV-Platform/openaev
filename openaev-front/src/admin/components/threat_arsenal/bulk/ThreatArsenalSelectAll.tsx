import { Checkbox, IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { LinkOffOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';

interface Props {
  label: string;
  checked: boolean | 'indeterminate';
  disabled: boolean;
  onToggle: () => void;
  onSelectOrphans?: () => void;
  orphansActive: boolean;
}

const ThreatArsenalSelectAll: FunctionComponent<Props> = ({ label, checked, disabled, onToggle, onSelectOrphans, orphansActive }) => {
  const { t } = useFormatter();
  return (
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 2.5,
      marginRight: 1.5,
      flexShrink: 0,
    }}
    >
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="inline-flex">
            <Checkbox aria-label={label} checked={checked} onCheckedChange={onToggle} disabled={disabled} />
          </span>
        </TooltipTrigger>
        <TooltipContent>{label}</TooltipContent>
      </Tooltip>
      {onSelectOrphans && (
        <Tooltip>
          <TooltipTrigger asChild>
            <IconButton
              icon={<LinkOffOutlined fontSize="small" />}
              aria-label={t('Select orphaned actions')}
              aria-pressed={orphansActive}
              active={orphansActive}
              onClick={onSelectOrphans}
              priority="tertiary"
              size="sm"
            />
          </TooltipTrigger>
          <TooltipContent>
            {orphansActive
              ? t('Show all actions again')
              : t('Select orphaned actions (no injector, no payload) to purge them at once')}
          </TooltipContent>
        </Tooltip>
      )}
    </Box>
  );
};

export default ThreatArsenalSelectAll;
