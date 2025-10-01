import { FlagOutlined } from '@mui/icons-material';
import { Box, LinearProgress, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Paper, Typography } from '@mui/material';
import * as R from 'ramda';
import { useContext } from 'react';

import Empty from '../../../components/Empty.js';
import { useFormatter } from '../../../components/i18n';
import { PermissionsContext } from '../common/Context.js';
import ObjectivePopover from './ObjectivePopover.js';

const LessonsObjectives = ({
  objectives,
  source,
  setSelectedObjective,
}) => {
  const { t } = useFormatter();
  const { permissions } = useContext(PermissionsContext);

  const sortedObjectives = R.sortWith(
    [R.ascend(R.prop('objective_priority'))],
    objectives,
  );
  return (
    <Paper variant="outlined">
      {sortedObjectives.length > 0 ? (
        <List style={{ padding: 0 }}>
          {sortedObjectives.map(objective => (
            <ListItem
              key={objective.objective_id}
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
                divider
                onClick={() => setSelectedObjective
                  && setSelectedObjective(objective.objective_id)}
              >
                <ListItemIcon>
                  <FlagOutlined />
                </ListItemIcon>
                <ListItemText
                  style={{ width: '50%' }}
                  primary={objective.objective_title}
                  secondary={objective.objective_description}
                />
                <Box
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    width: '30%',
                    marginRight: 1,
                  }}
                >
                  <Box sx={{
                    width: '100%',
                    mr: 1,
                  }}
                  >
                    <LinearProgress
                      variant="determinate"
                      value={objective.objective_score}
                    />
                  </Box>
                  <Box sx={{ minWidth: 35 }}>
                    <Typography variant="body2" color="text.secondary">
                      {objective.objective_score}
                      %
                    </Typography>
                  </Box>
                </Box>
              </ListItemButton>
            </ListItem>

          ))}
        </List>
      ) : (
        <Empty message={t(`No objectives in this ${source.type}.`)} />
      )}
    </Paper>
  );
};

export default LessonsObjectives;
