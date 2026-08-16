import { Paper } from '@filigran/design-system';
import { FlagOutlined } from '@mui/icons-material';
import { Box, LinearProgress, List, ListItem, ListItemButton, ListItemText, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useContext } from 'react';

import { useFormatter } from '../../../components/i18n';
import { PermissionsContext } from '../common/Context';
import LessonsPlaceholder from './LessonsPlaceholder';
import ObjectivePopover from './ObjectivePopover';

const LessonsObjectives = ({
  objectives,
  source,
  setSelectedObjective,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { permissions } = useContext(PermissionsContext);

  const sortedObjectives = R.sortWith(
    [R.ascend(R.prop('objective_priority'))],
    objectives,
  );
  return (
    /* padding=0 (iso): the row gutters carry the inset, and their dividers
       must keep touching the edges — PAPER-GAP-INVENTORY §6.1. */
    <Paper
      padding={0}
      style={{
        flex: 1,
        overflow: 'hidden',
      }}
    >
      {sortedObjectives.length > 0 ? (
        <List disablePadding>
          {sortedObjectives.map(objective => (
            <ListItem
              key={objective.objective_id}
              divider
              disablePadding
              secondaryAction={(
                permissions.canManage && (
                  <ObjectivePopover
                    isReadOnly={source.isReadOnly}
                    objective={objective}
                  />
                )
              )}
            >
              <ListItemButton
                onClick={() => setSelectedObjective
                  && setSelectedObjective(objective.objective_id)}
              >
                <Box
                  sx={{
                    width: 30,
                    height: 30,
                    borderRadius: 1,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                    marginRight: 1.5,
                    color: 'primary.main',
                    backgroundColor: alpha(theme.palette.primary.main, 0.1),
                  }}
                >
                  <FlagOutlined sx={{ fontSize: 16 }} />
                </Box>
                <ListItemText
                  sx={{ width: '50%' }}
                  primary={objective.objective_title}
                  secondary={objective.objective_description}
                  primaryTypographyProps={{
                    sx: {
                      fontSize: 13.5,
                      fontWeight: 600,
                    },
                  }}
                  secondaryTypographyProps={{ noWrap: true }}
                />
                <Box
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    width: '30%',
                    flexShrink: 0,
                    marginRight: 1,
                    gap: 1,
                  }}
                >
                  <LinearProgress
                    variant="determinate"
                    value={objective.objective_score}
                    sx={{
                      flex: 1,
                      borderRadius: 1,
                    }}
                  />
                  <Typography
                    variant="body2"
                    sx={{
                      minWidth: 35,
                      color: 'text.secondary',
                      fontVariantNumeric: 'tabular-nums',
                    }}
                  >
                    {objective.objective_score}
                    %
                  </Typography>
                </Box>
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      ) : (
        <LessonsPlaceholder
          icon={FlagOutlined}
          message={source.type === 'scenario'
            ? t('No objectives in this scenario.')
            : t('No objectives in this simulation.')}
        />
      )}
    </Paper>
  );
};

export default LessonsObjectives;
