import { Add, CastForEducationOutlined } from '@mui/icons-material';
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  GridLegacy,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { withStyles } from 'tss-react/mui';

import Transition from '../../../../components/common/Transition';
import inject18n from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import SearchFilter from '../../../../components/SearchFilter';
import { Can } from '../../../../utils/permissions/PermissionsProvider.js';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types.js';
import { truncate } from '../../../../utils/String';
import TagsFilter from '../../common/filters/TagsFilter';
import CreateTeam from '../../components/teams/CreateTeam';

const styles = theme => ({
  box: {
    width: '100%',
    minHeight: '100%',
    padding: 20,
    border: '1px dashed rgba(255, 255, 255, 0.3)',
  },
  chip: { margin: '0 10px 10px 0' },
  item: {
    paddingLeft: 10,
    height: 50,
  },
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
});

class LessonsCategoryAddTeams extends Component {
  constructor(props) {
    super(props);
    this.state = {
      open: false,
      keyword: '',
      teamsIds: [],
      tags: [],
    };
  }

  handleOpen() {
    this.setState({ open: true });
  }

  handleClose() {
    this.setState({
      open: false,
      keyword: '',
      teamsIds: [],
    });
  }

  handleSearchTeams(value) {
    this.setState({ keyword: value });
  }

  handleAddTag(value) {
    if (value) {
      this.setState({ tags: [value] });
    }
  }

  handleClearTag() {
    this.setState({ tags: [] });
  }

  addTeam(teamId) {
    this.setState({ teamsIds: R.append(teamId, this.state.teamsIds) });
  }

  addAllTeams() {
    const { lessonsCategoryTeamsIds, teams } = this.props;
    const teamsToAdd = R.pipe(
      R.map(n => n.team_id),
      R.filter(n => !lessonsCategoryTeamsIds.includes(n)),
    )(teams);
    this.setState({ teamsIds: teamsToAdd });
  }

  removeTeam(teamId) {
    this.setState({ teamsIds: R.filter(u => u !== teamId, this.state.teamsIds) });
  }

  submitAddTeams() {
    const {
      lessonsCategoryTeamsIds,
      lessonsCategoryId,
      handleUpdateTeams,
    } = this.props;
    handleUpdateTeams(lessonsCategoryId, [
      ...lessonsCategoryTeamsIds,
      ...this.state.teamsIds,
    ]);
    this.handleClose();
  }

  onCreate(result) {
    this.addTeam(result.team_id);
  }

  render() {
    const {
      classes,
      t,
      teams,
      lessonsCategoryTeamsIds,
      teamsMap,
    } = this.props;
    const { keyword, teamsIds, tags } = this.state;
    const filterByKeyword = n => keyword === ''
      || (n.team_name || '').toLowerCase().indexOf(keyword.toLowerCase())
      !== -1
      || (n.team_description || '')
        .toLowerCase()
        .indexOf(keyword.toLowerCase()) !== -1;
    const filteredTeams = R.pipe(
      R.filter(
        n => tags.length === 0
          || R.any(
            filter => R.includes(filter, n.team_tags),
            R.pluck('id', tags),
          ),
      ),
      R.filter(filterByKeyword),
      R.take(10),
    )(teams);
    return (
      <>
        <IconButton
          onClick={this.handleOpen.bind(this)}
          aria-haspopup="true"
          size="small"
          color="secondary"
        >
          <Add fontSize="small" />
        </IconButton>
        <Dialog
          open={this.state.open}
          TransitionComponent={Transition}
          onClose={this.handleClose.bind(this)}
          fullWidth
          maxWidth="lg"
          PaperProps={{
            elevation: 1,
            sx: {
              minHeight: 580,
              maxHeight: 580,
            },
          }}
        >
          <DialogTitle>
            <div style={{ float: 'left' }}>
              {t('Add target teams in this lessons learned category')}
            </div>
            <div style={{
              float: 'right',
              marginTop: -4,
            }}
            >
              <Button
                onClick={this.addAllTeams.bind(this)}
                variant="outlined"
                color="warning"
              >
                {t('Select all')}
              </Button>
            </div>
          </DialogTitle>
          <DialogContent>
            <GridLegacy container spacing={3} style={{ marginTop: -15 }}>
              <GridLegacy item xs={8}>
                <GridLegacy container spacing={3}>
                  <GridLegacy item xs={6}>
                    <SearchFilter
                      onChange={this.handleSearchTeams.bind(this)}
                      fullWidth
                    />
                  </GridLegacy>
                  <GridLegacy item xs={6}>
                    <TagsFilter
                      onAddTag={this.handleAddTag.bind(this)}
                      onClearTag={this.handleClearTag.bind(this)}
                      currentTags={tags}
                      fullWidth
                    />
                  </GridLegacy>
                </GridLegacy>
                <List>
                  {filteredTeams.map((team) => {
                    const disabled = teamsIds.includes(team.team_id)
                      || lessonsCategoryTeamsIds.includes(
                        team.team_id,
                      );
                    return (
                      <ListItemButton
                        key={team.team_id}
                        disabled={disabled}
                        divider
                        dense
                        onClick={this.addTeam.bind(
                          this,
                          team.team_id,
                        )}
                      >
                        <ListItemIcon>
                          <CastForEducationOutlined />
                        </ListItemIcon>
                        <ListItemText
                          primary={team.team_name}
                          secondary={team.team_description}
                        />
                        <ItemTags
                          variant="list"
                          tags={team.team_tags}
                        />
                      </ListItemButton>
                    );
                  })}
                  <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
                    <CreateTeam
                      inline
                      onCreate={this.onCreate.bind(this)}
                    />
                  </Can>
                </List>
              </GridLegacy>
              <GridLegacy item xs={4}>
                <Box className={classes.box}>
                  {this.state.teamsIds.map((teamId) => {
                    const team = teamsMap[teamId];
                    return (
                      <Chip
                        key={teamId}
                        onDelete={this.removeTeam.bind(this, teamId)}
                        label={truncate(team?.team_name || '', 22)}
                        icon={<CastForEducationOutlined />}
                        classes={{ root: classes.chip }}
                      />
                    );
                  })}
                </Box>
              </GridLegacy>
            </GridLegacy>
          </DialogContent>
          <DialogActions>
            <Button onClick={this.handleClose.bind(this)}>{t('Cancel')}</Button>
            <Button
              color="secondary"
              onClick={this.submitAddTeams.bind(this)}
            >
              {t('Add')}
            </Button>
          </DialogActions>
        </Dialog>
      </>
    );
  }
}

LessonsCategoryAddTeams.propTypes = {
  t: PropTypes.func,
  fetchTeams: PropTypes.func,
  handleUpdateTeams: PropTypes.func,
  organizations: PropTypes.array,
  teams: PropTypes.array,
  lessonsCategoryId: PropTypes.string,
  lessonsCategoryTeamsIds: PropTypes.array,
  attachment: PropTypes.bool,
};

export default R.compose(
  inject18n,
  Component => withStyles(Component, styles),
)(LessonsCategoryAddTeams);
