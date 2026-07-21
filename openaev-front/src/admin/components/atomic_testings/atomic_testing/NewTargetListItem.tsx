import { Groups3Outlined, OpenInNewOutlined, PersonOutlined, SmartToyOutlined } from '@mui/icons-material';
import { Box, IconButton, ListItemButton, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { SelectGroup } from 'mdi-material-ui';
import { useNavigate } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { ASSET_BASE_URL, ASSET_GROUP_BASE_URL } from '../../../../constants/BaseUrls';
import type { InjectTarget } from '../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import NewAtomicTestingResult from './NewAtomicTestingResult';

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
  const getIcon = (target: InjectTarget) => {
    const iconMap = {
      // TODO: for Endpoints and Agents, check the targetSubType attribute
      ASSETS_GROUPS: <SelectGroup sx={{ fontSize: 18 }} />,
      ASSETS: <PlatformIcon platform={target?.target_subtype ?? 'Unknown'} width={18} />,
      TEAMS: <Groups3Outlined sx={{ fontSize: 18 }} />,
      PLAYERS: <PersonOutlined sx={{ fontSize: 18 }} />,
      AI_TARGETS: <SmartToyOutlined sx={{ fontSize: 18 }} />,
      AGENT: (
        <img
          src={buildTenantApiPath(`/api/images/executors/icons/${target.target_subtype}`)}
          alt={target.target_subtype}
          style={{
            width: 18,
            height: 18,
            borderRadius: 4,
          }}
        />
      ),
    };

    return iconMap[target.target_type];
  };

  // Overview pivot only exists for asset-backed targets (AI targets are assets).
  const overviewUrl = (() => {
    switch (target.target_type) {
      case 'ASSETS':
      case 'AI_TARGETS':
        return `${ASSET_BASE_URL}/${target.target_id}`;
      case 'ASSETS_GROUPS':
        return `${ASSET_GROUP_BASE_URL}/${target.target_id}`;
      default:
        return null;
    }
  })();

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
        '&:hover .target-open-overview': { opacity: 1 },
      }}
    >
      <Box
        aria-hidden
        sx={{
          width: 32,
          height: 32,
          flexShrink: 0,
          borderRadius: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: alpha(theme.palette.text.primary, 0.04),
        }}
      >
        {getIcon(target)}
      </Box>
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
        <Tooltip title={t('Open overview')}>
          <IconButton
            className="target-open-overview"
            size="small"
            onClick={handleOpenOverview}
            sx={{
              opacity: 0,
              transition: 'opacity 0.15s',
            }}
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
