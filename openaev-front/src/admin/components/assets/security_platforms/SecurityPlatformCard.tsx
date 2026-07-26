import { Box, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import ItemSecurityPlatformType from '../../../../components/ItemSecurityPlatformType';
import ItemTags from '../../../../components/ItemTags';
import { SECURITY_PLATFORM_BASE_URL } from '../../../../constants/BaseUrls';
import { type SecurityPlatform } from '../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import SecurityPlatformPopover from './SecurityPlatformPopover';
import isCollectorManaged from './securityPlatformUtils';

interface Props {
  securityPlatform: SecurityPlatform;
  onUpdate: (result: SecurityPlatform) => void;
  onDelete: (result: string) => void;
  openEditOnInit?: boolean;
}

const SecurityPlatformCard: FunctionComponent<Props> = ({
  securityPlatform,
  onUpdate,
  onDelete,
  openEditOnInit,
}) => {
  const theme = useTheme();

  return (
    <Paper
      variant="outlined"
      data-testid="security-platform-card"
      // Real router link (not a JS navigate) so ctrl/cmd+click opens a new tab.
      component={Link}
      to={`${SECURITY_PLATFORM_BASE_URL}/${securityPlatform.asset_id}`}
      sx={{
        'position': 'relative',
        'display': 'flex',
        'flexDirection': 'column',
        'gap': 1.5,
        'padding': 2,
        'borderRadius': 1,
        'height': '100%',
        'cursor': 'pointer',
        'textDecoration': 'none',
        'color': 'inherit',
        'transition': theme.transitions.create(['border-color', 'box-shadow', 'transform']),
        '&:hover': {
          borderColor: alpha(theme.palette.primary.main, 0.5),
          boxShadow: `0 4px 16px ${alpha(theme.palette.common.black, 0.25)}`,
          transform: 'translateY(-2px)',
        },
      }}
    >
      <Box
        sx={{
          position: 'absolute',
          top: 6,
          right: 6,
        }}
        onClick={event => event.stopPropagation()}
      >
        <SecurityPlatformPopover
          securityPlatform={{
            ...securityPlatform,
            type: 'static',
          }}
          onUpdate={onUpdate}
          onDelete={onDelete}
          openEditOnInit={openEditOnInit}
          disabled={isCollectorManaged(securityPlatform)}
        />
      </Box>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        minWidth: 0,
        paddingRight: 3,
      }}
      >
        <Box sx={{
          width: 44,
          height: 44,
          flexShrink: 0,
          borderRadius: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
          backgroundColor: alpha(theme.palette.text.primary, 0.02),
        }}
        >
          <img
            src={buildTenantApiPath(`/api/images/security_platforms/id/${securityPlatform.asset_id}/${theme.palette.mode}?${Date.now()}`)}
            alt={securityPlatform.asset_name}
            style={{
              width: 28,
              height: 28,
              borderRadius: 4,
            }}
          />
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Tooltip title={securityPlatform.asset_name}>
            <Typography sx={{
              fontSize: 14,
              fontWeight: 600,
              lineHeight: 1.3,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
            >
              {securityPlatform.asset_name}
            </Typography>
          </Tooltip>
          <Box sx={{ marginTop: 0.5 }}>
            <ItemSecurityPlatformType type={securityPlatform.security_platform_type} />
          </Box>
        </Box>
      </Box>

      <Typography
        variant="body2"
        sx={{
          color: 'text.secondary',
          minHeight: 40,
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        }}
      >
        {securityPlatform.asset_description || '-'}
      </Typography>

      <Box sx={{ marginTop: 'auto' }}>
        <ItemTags variant="reduced-view" tags={securityPlatform.asset_tags} />
      </Box>
    </Paper>
  );
};

export default SecurityPlatformCard;
