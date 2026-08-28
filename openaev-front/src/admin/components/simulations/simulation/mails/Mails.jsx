import { BarChartOutlined, KeyboardArrowRight, MailOutlined, ReorderOutlined } from '@mui/icons-material';
import {
  Box,
  Chip,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemSecondaryAction,
  ListItemText,
  Paper,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useContext, useState } from 'react';
import { useDispatch } from 'react-redux';
import { Link, useParams } from 'react-router';

import { fetchExerciseInjects } from '../../../../../actions/Inject';
import { SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import ItemTags from '../../../../../components/ItemTags';
import SearchFilter from '../../../../../components/SearchFilter';
import { useHelper } from '../../../../../store';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useSearchAndFilter from '../../../../../utils/SortingFiltering';
import { PermissionsContext, TeamContext } from '../../../common/Context';
import TagsFilter from '../../../common/filters/TagsFilter';
import InjectIcon from '../../../common/injects/InjectIcon';
import ExecutionMenu from '../ExecutionMenu';
import CreateQuickInject from '../injects/CreateQuickInject';
import teamContextForExercise from '../teams/teamContextForExercise';
import MailDistributionByInject from './MailDistributionByInject';
import MailDistributionByPlayer from './MailDistributionByPlayer';
import MailDistributionByTeam from './MailDistributionByTeam';
import MailDistributionOverTimeChart from './MailDistributionOverTimeChart';
import MailDistributionOverTimeLine from './MailDistributionOverTimeLine';

// The chosen view survives navigation, like the other list/grid toggles.
const VIEW_MODE_STORAGE_KEY = 'simulation-mails:view-mode';

const headerStyles = {
  iconSort: {
    position: 'absolute',
    margin: '0 0 0 5px',
    padding: 0,
    top: '0px',
  },
  inject_title: {
    float: 'left',
    width: '30%',
    fontSize: 12,
    fontWeight: '700',
  },
  inject_users_number: {
    float: 'left',
    width: '15%',
    fontSize: 12,
    fontWeight: '700',
  },
  inject_sent_at: {
    float: 'left',
    width: '15%',
    fontSize: 12,
    fontWeight: '700',
  },
  inject_communications_not_ack_number: {
    float: 'left',
    width: '10%',
    fontSize: 12,
    fontWeight: '700',
  },
  inject_communications_number: {
    float: 'left',
    width: '10%',
    fontSize: 12,
    fontWeight: '700',
  },
  inject_tags: {
    float: 'left',
    fontSize: 12,
    fontWeight: '700',
  },
};

const cellStyle = {
  float: 'left',
  height: 20,
  fontSize: 13,
  whiteSpace: 'nowrap',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
};

const inlineStyles = {
  inject_title: {
    ...cellStyle,
    width: '30%',
  },
  inject_users_number: {
    ...cellStyle,
    width: '15%',
  },
  inject_sent_at: {
    ...cellStyle,
    width: '15%',
  },
  inject_communications_not_ack_number: {
    ...cellStyle,
    width: '10%',
  },
  inject_communications_number: {
    ...cellStyle,
    width: '10%',
  },
  inject_tags: cellStyle,
};

const Mails = () => {
  // Standard hooks
  const theme = useTheme();
  const dispatch = useDispatch();
  const { t, fndt } = useFormatter();
  const [viewMode, setViewMode] = useState(() => localStorage.getItem(VIEW_MODE_STORAGE_KEY) ?? 'list');
  const { permissions } = useContext(PermissionsContext);

  const handleViewModeChange = (_, next) => {
    if (next) {
      setViewMode(next);
      localStorage.setItem(VIEW_MODE_STORAGE_KEY, next);
    }
  };

  // Mail count chips: theme-driven tones (read = primary, not read = error).
  const comChipSx = color => ({
    fontSize: 12,
    height: 20,
    textTransform: 'uppercase',
    borderRadius: 1,
    color,
    backgroundColor: alpha(color, 0.08),
    border: `1px solid ${alpha(color, 0.5)}`,
  });

  // Filter and sort hook
  const searchColumns = ['title', 'description', 'content'];
  const filtering = useSearchAndFilter('inject', 'sent_at', searchColumns);
  // Fetching data
  const { exerciseId } = useParams();
  const { exercise, injects } = useHelper((helper) => {
    return {
      exercise: helper.getExercise(exerciseId),
      injects: helper.getExerciseInjects(exerciseId),
    };
  });
  useDataLoader(() => {
    dispatch(fetchExerciseInjects(exerciseId));
  });
  const sortedInjects = filtering
    .filterAndSort(injects)
    .filter(i => i.inject_communications_number > 0);

  const teamContext = teamContextForExercise(exerciseId, exercise.exercise_teams_users, exercise.exercise_all_users_number, exercise.exercise_users_number);

  // Rendering
  return (
    <div>
      <ExecutionMenu exerciseId={exerciseId} />
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        paddingBottom: 5,
      }}
      >
        {/* Toolbar: scoping filters on the left, actions + view toggle on the
            right, all sharing the same gap (no stuck-together controls). */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: theme.spacing(1.5),
        }}
        >
          {viewMode === 'list' && (
            <>
              <SearchFilter
                variant="small"
                onChange={filtering.handleSearch}
                keyword={filtering.keyword}
              />
              <TagsFilter
                onAddTag={filtering.handleAddTag}
                onRemoveTag={filtering.handleRemoveTag}
                currentTags={filtering.tags}
              />
            </>
          )}
          <div style={{ flex: 1 }} />
          {viewMode === 'list' && permissions.canManage && (
            <TeamContext.Provider value={teamContext}>
              <CreateQuickInject exercise={exercise} />
            </TeamContext.Provider>
          )}
          <ToggleButtonGroup
            value={viewMode}
            exclusive
            size="small"
            onChange={handleViewModeChange}
            aria-label={t('View mode')}
            sx={{ '& .MuiToggleButton-root.Mui-selected .MuiSvgIcon-root': { color: 'primary.main' } }}
          >
            <ToggleButton value="list" aria-label={t('List view')}>
              <Tooltip title={t('List view')}>
                <ReorderOutlined fontSize="small" />
              </Tooltip>
            </ToggleButton>
            <ToggleButton value="distribution" aria-label={t('Distribution view')}>
              <Tooltip title={t('Distribution view')}>
                <BarChartOutlined fontSize="small" />
              </Tooltip>
            </ToggleButton>
          </ToggleButtonGroup>
        </div>
        {viewMode === 'distribution' && (
          <>
            <Box sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: 2,
              alignItems: 'stretch',
            }}
            >
              <SectionBlock title={t('Sent mails over time')}>
                <MailDistributionOverTimeChart exerciseId={exerciseId} />
              </SectionBlock>
              <SectionBlock title={t('Sent mails over time by team')}>
                <MailDistributionOverTimeLine exerciseId={exerciseId} />
              </SectionBlock>
            </Box>
            <Box sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, 1fr)',
              gap: 2,
              alignItems: 'stretch',
            }}
            >
              <SectionBlock title={t('Distribution of mails by team')}>
                <MailDistributionByTeam exerciseId={exerciseId} />
              </SectionBlock>
              <SectionBlock title={t('Distribution of mails by player')}>
                <MailDistributionByPlayer exerciseId={exerciseId} />
              </SectionBlock>
              <SectionBlock title={t('Distribution of mails by inject')}>
                <MailDistributionByInject exerciseId={exerciseId} />
              </SectionBlock>
            </Box>
          </>
        )}
        {viewMode === 'list' && (
          sortedInjects.length === 0
            ? (
                <Paper variant="outlined" sx={{ borderRadius: 1 }}>
                  <Empty
                    icon={MailOutlined}
                    message={t('No mails have been sent yet')}
                    hint={t('Mails sent to players will appear here once the simulation runs')}
                  />
                </Paper>
              )
            : (
                <List style={{ paddingTop: 0 }}>
                  <ListItem
                    divider={false}
                    style={{
                      paddingTop: 0,
                      paddingLeft: 10,
                      textTransform: 'uppercase',
                      cursor: 'pointer',
                    }}
                  >
                    <ListItemIcon>
                      <span
                        style={{
                          padding: '0 8px 0 8px',
                          fontWeight: 700,
                          fontSize: 12,
                        }}
                      >
                    &nbsp;
                      </span>
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div>
                          {filtering.buildHeader(
                            'inject_title',
                            'Title',
                            false,
                            headerStyles,
                          )}
                          {filtering.buildHeader(
                            'inject_users_number',
                            'Players',
                            true,
                            headerStyles,
                          )}
                          {filtering.buildHeader(
                            'inject_sent_at',
                            'Sent at',
                            true,
                            headerStyles,
                          )}
                          {filtering.buildHeader(
                            'inject_communications_not_ack_number',
                            'Mails not read',
                            true,
                            headerStyles,
                          )}
                          {filtering.buildHeader(
                            'inject_communications_number',
                            'Total mails',
                            true,
                            headerStyles,
                          )}
                          {filtering.buildHeader(
                            'inject_tags',
                            'Tags',
                            true,
                            headerStyles,
                          )}
                        </div>
                      )}
                    />
                    <ListItemSecondaryAction> &nbsp; </ListItemSecondaryAction>
                  </ListItem>
                  {sortedInjects.map((inject) => {
                    return (
                      <ListItemButton
                        key={inject.inject_id}
                        component={Link}
                        to={`/admin/simulations/${exerciseId}/execution/mails/${inject.inject_id}`}
                        divider={true}
                        style={{
                          paddingLeft: 10,
                          height: 50,
                        }}
                      >
                        <ListItemIcon style={{ paddingTop: 5 }}>
                          <InjectIcon type={inject.inject_type} disabled={!inject.inject_enabled} />
                        </ListItemIcon>
                        <ListItemText
                          primary={(
                            <div>
                              <div style={inlineStyles.inject_title}>
                                {inject.inject_title}
                              </div>
                              <div style={inlineStyles.inject_users_number}>
                                {inject.inject_users_number}
                              </div>
                              <div style={inlineStyles.inject_sent_at}>
                                {fndt(inject.inject_sent_at)}
                              </div>
                              <div style={inlineStyles.inject_communications_not_ack_number}>
                                <Chip
                                  sx={comChipSx(theme.palette.error.main)}
                                  label={inject.inject_communications_not_ack_number}
                                />
                              </div>
                              <div style={inlineStyles.inject_communications_number}>
                                <Chip
                                  sx={comChipSx(theme.palette.primary.main)}
                                  label={inject.inject_communications_number}
                                />
                              </div>
                              <div style={inlineStyles.inject_tags}>
                                <ItemTags variant="list" tags={inject.inject_tags} />
                              </div>
                            </div>
                          )}
                        />
                        <ListItemSecondaryAction style={{ paddingTop: 3 }}>
                          <KeyboardArrowRight />
                        </ListItemSecondaryAction>
                      </ListItemButton>
                    );
                  })}
                </List>
              )
        )}
      </Box>
    </div>
  );
};

export default Mails;
