import { FactCheckOutlined, MailOutlined, NoteAltOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Drawer, ListItemIcon, ListItemText, MenuItem, MenuList } from '@mui/material';
import { type FunctionComponent } from 'react';
import { Link, useLocation } from 'react-router';

import type { LoggedHelper } from '../../../../actions/helper';
import { useFormatter } from '../../../../components/i18n';
import { computeBannerSettings } from '../../../../public/components/systembanners/utils';
import { useHelper } from '../../../../store';
import { type Exercise } from '../../../../utils/api-types';

// Height of the top AppBar toolbar (see TopBar.tsx) - the menu paper starts
// below it so it never renders over the header.
const TOPBAR_HEIGHT = 68;

interface Props { exerciseId: Exercise['exercise_id'] }

// Permanent right-hand menu of the simulation Execution area (overview, mails,
// validations, logs). The simulation Index pads the content area by the menu
// width whenever the location is under /execution.
const ExecutionMenu: FunctionComponent<Props> = ({ exerciseId }) => {
  const location = useLocation();
  const { t } = useFormatter();
  const { settings } = useHelper((helper: LoggedHelper) => {
    return { settings: helper.getPlatformSettings() };
  });
  const { bannerHeightNumber } = computeBannerSettings(settings);
  const topOffset = bannerHeightNumber + TOPBAR_HEIGHT;

  const base = `/admin/simulations/${exerciseId}/execution`;
  const entries = [
    {
      to: `${base}/timeline`,
      label: t('Overview'),
      icon: <TrackChangesOutlined />,
      selected: location.pathname === `${base}/timeline`,
    },
    {
      to: `${base}/mails`,
      label: t('Mails'),
      icon: <MailOutlined />,
      selected: location.pathname.includes(`${base}/mails`),
    },
    {
      to: `${base}/validations`,
      label: t('Validations'),
      icon: <FactCheckOutlined />,
      selected: location.pathname === `${base}/validations`,
    },
    {
      to: `${base}/logs`,
      label: t('Simulation logs'),
      icon: <NoteAltOutlined />,
      selected: location.pathname === `${base}/logs`,
    },
  ];

  return (
    <Drawer
      variant="permanent"
      anchor="right"
      sx={{
        '& .MuiDrawer-paper': {
          width: 200,
          position: 'fixed',
          overflow: 'auto',
          padding: 0,
          top: topOffset,
          height: `calc(100% - ${topOffset}px)`,
        },
      }}
    >
      <MenuList component="nav">
        {entries.map(entry => (
          <MenuItem
            key={entry.to}
            component={Link}
            to={entry.to}
            selected={entry.selected}
            sx={{
              paddingTop: 1.25,
              paddingBottom: 1.25,
            }}
          >
            <ListItemIcon>
              {entry.icon}
            </ListItemIcon>
            <ListItemText primary={entry.label} />
          </MenuItem>
        ))}
      </MenuList>
    </Drawer>
  );
};

export default ExecutionMenu;
