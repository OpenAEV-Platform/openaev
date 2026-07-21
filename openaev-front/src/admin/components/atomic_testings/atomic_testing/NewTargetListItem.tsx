import { OpenInNewOutlined } from '@mui/icons-material';
import { IconButton, ListItemButton, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useNavigate } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import type { InjectTarget } from '../../../../utils/api-types';
import { getTargetOverviewUrl, isAssetGroups } from '../../../../utils/target/TargetUtils';
import NewAtomicTestingResult from './NewAtomicTestingResult';
import TargetIcon from './TargetIcon';

interface Props {
  selected?: boolean;
  onClick: (target: InjectTarget) => void;
  target: InjectTarget;
}

const NewTargetListItem: React.FC<Props> = ({ onClick, target, selected }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const navigate = useNavigate();
  const handleItemClick = () => {
    onClick(target);
  };

  const overviewUrl = getTargetOverviewUrl(target);
  const overviewLabel = isAssetGroups(target) ? t('Open asset group overview') : t('Open asset overview');

  const handleOpenOverview = (event: React.MouseEvent) => {
    event.stopPropagation();
    event.preventDefault();
    if (overviewUrl) {
      navigate(overviewUrl);
    }
  };

  return (
    <ListItemButton
      onClick={handleItemClick}
      selected={selected}
      sx={{
        'paddingBlock': 1,
        'paddingInline': 1.5,
        'gap': 1.5,
        'borderLeft': `2px solid ${selected ? theme.palette.primary.main : 'transparent'}`,
        '&.Mui-selected': { backgroundColor: alpha(theme.palette.primary.main, 0.08) },
        '&.Mui-selected:hover': { backgroundColor: alpha(theme.palette.primary.main, 0.12) },
        // The pivot action stays discoverable without relying on hover alone
        // (hover does not exist on touch): it is revealed on hover, on keyboard
        // focus, and is always shown on the selected row.
        '& .target-open-overview': {
          opacity: 0,
          transition: 'opacity 0.15s',
        },
        '&:hover .target-open-overview, &:focus-within .target-open-overview': { opacity: 1 },
        '&.Mui-selected .target-open-overview': { opacity: 1 },
      }}
    >
      <TargetIcon target={target} />
      <Typography
        sx={{
          flex: 1,
          minWidth: 0,
          fontSize: 13,
          fontWeight: 600,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
      >
        {target?.target_name}
      </Typography>
      {overviewUrl && (
        <Tooltip title={overviewLabel}>
          <IconButton
            className="target-open-overview"
            size="small"
            onClick={handleOpenOverview}
            aria-label={overviewLabel}
          >
            <OpenInNewOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
      <NewAtomicTestingResult target={target} />
    </ListItemButton>
  );
};

export default NewTargetListItem;
