import { ExpandLessOutlined, ExpandMoreOutlined } from '@mui/icons-material';
import { Box, Button, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useState } from 'react';

import { useFormatter } from '../../i18n';

interface Props {
  title: string;
  icon?: ReactNode;
  action?: ReactNode;
  collapsible?: boolean;
  defaultCollapsed?: boolean;
  id?: string;
  children: ReactNode;
}

/**
 * Shared bordered overview section with an overline title header.
 * Extracted from ThreatArsenalActionOverview to be reused across overview screens.
 */
const Section: FunctionComponent<Props> = ({
  title,
  icon,
  action,
  collapsible = false,
  defaultCollapsed = false,
  id,
  children,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  return (
    <Box
      component="section"
      id={id}
      sx={{
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: 1,
        backgroundColor: alpha(theme.palette.background.paper, 0.5),
        overflow: 'hidden',
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        paddingBlock: 1,
        paddingInline: 1.5,
        borderBottom: collapsed ? 'none' : `1px solid ${theme.palette.divider}`,
        backgroundColor: alpha(theme.palette.background.paper, 0.4),
      }}
      >
        {icon && (
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            color: 'text.secondary',
          }}
          >
            {icon}
          </Box>
        )}
        <Typography
          variant="overline"
          sx={{
            color: 'text.primary',
            letterSpacing: '0.06em',
            fontWeight: 600,
            flex: 1,
            lineHeight: 1.2,
          }}
        >
          {title}
        </Typography>
        {action}
        {collapsible && (
          <Button
            size="small"
            onClick={() => setCollapsed(c => !c)}
            sx={{
              minWidth: 0,
              padding: 0.5,
              color: 'text.secondary',
            }}
            aria-label={collapsed ? t('Expand') : t('Collapse')}
          >
            {collapsed ? <ExpandMoreOutlined fontSize="small" /> : <ExpandLessOutlined fontSize="small" />}
          </Button>
        )}
      </Box>
      {!collapsed && (
        <Box sx={{ padding: 1.5 }}>
          {children}
        </Box>
      )}
    </Box>
  );
};

export default Section;
