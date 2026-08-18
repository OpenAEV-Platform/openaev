import { Button } from '@filigran/design-system';
import { AddOutlined, GroupsOutlined } from '@mui/icons-material';
import { type FunctionComponent, useContext, useEffect, useMemo, useState } from 'react';

import { findTeams } from '../../../../actions/teams/team-actions';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectListPicker, { type SelectListPickerElements } from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import { type Team, type TeamOutput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
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

  // Drawer
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

  const selectedIds = useMemo(() => selectedTeamValues.map(v => v.team_id), [selectedTeamValues]);

  const toggleTeam = (teamId: string, team: TeamOutput) => {
    if (selectedIds.includes(teamId)) {
      setSelectedTeamValues(selectedTeamValues.filter(v => v.team_id !== teamId));
    } else {
      setSelectedTeamValues([...selectedTeamValues, team]);
    }
  };

  // Headers
  const elements: SelectListPickerElements<TeamOutput> = useMemo(() => ({
    icon: { value: () => <GroupsOutlined /> },
    headers: [
      {
        field: 'team_name',
        label: 'Name',
        isSortable: true,
        value: (team: TeamOutput) => team.team_name,
        width: 70,
      },
      {
        field: 'team_tags',
        label: 'Tags',
        value: (team: TeamOutput) => <ItemTags variant="list" limit={2} tags={team.team_tags} />,
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
      <Button
        size="sm"
        startIcon={<AddOutlined fontSize="small" />}
        onClick={() => setOpen(true)}
      >
        {t('Add team')}
      </Button>
      <SelectListPicker<TeamOutput>
        open={open}
        onClose={handleClose}
        onSubmit={handleSubmit}
        title={t('Update teams')}
        headerComponent={paginationComponent}
        values={teamValues}
        elements={elements}
        sortHelpers={queryableHelpers.sortHelpers}
        selectedIds={selectedIds}
        onToggle={toggleTeam}
        getId={element => element.team_id}
        isLoading={isLoading}
        containerTestId="select-team-list"
        buttonComponent={(
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
            <CreateTeam
              inline
              onCreate={team => setSelectedTeamValues([...selectedTeamValues, team as TeamOutput])}
            />
          </Can>
        )}
      />
    </>
  );
};

export default UpdateTeams;
