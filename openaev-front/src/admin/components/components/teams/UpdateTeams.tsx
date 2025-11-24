import { Add, GroupsOutlined } from '@mui/icons-material';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton } from '@mui/material';
import { type FunctionComponent, useContext, useEffect, useMemo, useState } from 'react';

import { findTeams } from '../../../../actions/teams/team-actions';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectList, { type SelectListElements } from '../../../../components/common/SelectList';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import { type Team, type TeamOutput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { TeamContext } from '../../common/Context';
import CreateTeam from './CreateTeam';

interface Props { addedTeamIds: Team['team_id'][] }

const UpdateTeams: FunctionComponent<Props> = ({ addedTeamIds }) => {
  // Standard hooks
  const { t } = useFormatter();
  const { searchTeams, onReplaceTeam } = useContext(TeamContext);

  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [teamValues, setTeamValues] = useState<TeamOutput[]>([]);
  const [selectedTeamValues, setSelectedTeamValues] = useState<TeamOutput[]>([]);

  // Dialog
  const [open, setOpen] = useState(false);

  const handleClose = () => {
    setOpen(false);
    setSelectedTeamValues([]);
  };

  const handleSubmit = async () => {
    setOpen(false);
    onReplaceTeam?.(selectedTeamValues.map(v => v.team_id));
  };

  useEffect(() => {
    if (open) {
      findTeams(addedTeamIds).then(result => setSelectedTeamValues(result.data));
    }
  }, [open, addedTeamIds]);

  // Pagination
  const addTeam = (_teamId: string, team: TeamOutput) => setSelectedTeamValues([...selectedTeamValues, team]);
  const removeTeam = (teamId: string) => setSelectedTeamValues(selectedTeamValues.filter(v => v.team_id !== teamId));

  // Headers
  const elements: SelectListElements<TeamOutput> = useMemo(() => ({
    icon: { value: () => <GroupsOutlined /> },
    headers: [
      {
        field: 'team_name',
        value: (team: TeamOutput) => team.team_name,
        width: 70,
      },
      {
        field: 'team_tags',
        value: (team: TeamOutput) => <ItemTags variant="reduced-view" tags={team.team_tags} />,
        width: 30,
      },
    ],
  }), []);

  const availableFilterNames = [
    'team_tags',
  ];
  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({}));

  const paginationComponent = (
    <PaginationComponentV2
      fetch={input => searchTeams(input)}
      searchPaginationInput={searchPaginationInput}
      setContent={setTeamValues}
      setLoading={setIsLoading}
      entityPrefix="team"
      availableFilterNames={availableFilterNames}
      queryableHelpers={queryableHelpers}
    />
  );

  return (
    <>
      <IconButton
        color="primary"
        aria-label="Add"
        onClick={() => setOpen(true)}
        size="small"
      >
        <Add fontSize="small" />
      </IconButton>
      <Dialog
        open={open}
        TransitionComponent={Transition}
        onClose={handleClose}
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
        <DialogTitle>{t('Update teams')}</DialogTitle>
        <DialogContent>
          <Box sx={{ marginTop: 2 }}>
            <SelectList
              values={teamValues}
              selectedValues={selectedTeamValues}
              isLoadingValues={isLoading}
              elements={elements}
              onSelect={addTeam}
              onDelete={removeTeam}
              paginationComponent={paginationComponent}
              buttonComponent={(
                <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
                  <CreateTeam
                    inline
                    onCreate={team => setSelectedTeamValues([...selectedTeamValues, team as TeamOutput])}
                  />
                </Can>
              )}
              getId={element => element.team_id}
              getName={element => element.team_name}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>{t('Cancel')}</Button>
          {!isLoading && (
            <Button color="secondary" onClick={handleSubmit}>
              {t('Update')}
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </>
  );
};

export default UpdateTeams;
