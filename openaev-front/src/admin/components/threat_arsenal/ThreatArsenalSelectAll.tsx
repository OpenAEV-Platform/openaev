import { Checkbox, IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { LinkOffOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';

interface Props {
  label: string;
  checked: boolean | 'indeterminate';
  disabled: boolean;
  onToggle: () => void;
  onSelectOrphans?: () => void;
}

// Toolbar select-all control, shared by the grid and list views, with the orphan purge shortcut.
const ThreatArsenalSelectAll: FunctionComponent<Props> = ({ label, checked, disabled, onToggle, onSelectOrphans }) => {
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
      {/* Checkbox only (#7340): the toolbar must fit one row at ~1512px, so the
          caption lives in the tooltip and the accessible name. */}
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="inline-flex">
            <Checkbox aria-label={label} checked={checked} onCheckedChange={onToggle} disabled={disabled} />
          </span>
        </TooltipTrigger>
        {label && <TooltipContent>{label}</TooltipContent>}
      </Tooltip>
      {onSelectOrphans && (
        <Tooltip>
          <TooltipTrigger asChild>
            <IconButton
              icon={<LinkOffOutlined fontSize="small" />}
              aria-label={t('Select orphaned actions')}
              onClick={onSelectOrphans}
              priority="tertiary"
              size="sm"
            />
          </TooltipTrigger>
          <TooltipContent>{t('Select orphaned actions (no injector, no payload) to purge them at once')}</TooltipContent>
        </Tooltip>
      )}
    </Box>
  );
};

export default ThreatArsenalSelectAll;
