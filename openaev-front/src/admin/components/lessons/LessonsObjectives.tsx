import { FlagOutlined } from '@mui/icons-material';
import { Box, LinearProgress, List, ListItem, ListItemButton, ListItemText, Paper, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type Objective } from '../../../utils/api-types';
import { PermissionsContext } from '../common/Context';
import LessonsPlaceholder from './LessonsPlaceholder';
import ObjectivePopover from './ObjectivePopover';

interface Props {
  objectives: Objective[];
  source: {
    type: string;
    isReadOnly: boolean;
  };
  setSelectedObjective?: (objectiveId: string) => void;
}

// Mirrors the previous `R.ascend(R.prop('objective_priority'))`: a missing priority compares equal
// to everything, which leaves those objectives in their original order.
const ascendByPriority = (a: Objective, b: Objective): number => {
  const left = a.objective_priority;
  const right = b.objective_priority;
  if (left === undefined || right === undefined) {
    return 0;
  }
  if (left < right) {
    return -1;
  }
  if (left > right) {
    return 1;
  }
  return 0;
};

const LessonsObjectives: FunctionComponent<Props> = ({
  objectives,
  source,
  setSelectedObjective,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { permissions } = useContext(PermissionsContext);

  const sortedObjectives = [...objectives].sort(ascendByPriority);
  return (
    <Paper
      variant="outlined"
      sx={{
        borderRadius: 1,
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
