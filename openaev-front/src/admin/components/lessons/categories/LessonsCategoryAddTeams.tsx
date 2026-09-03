import { Add, CastForEducationOutlined } from '@mui/icons-material';
import { Box, Button, IconButton } from '@mui/material';
import { type FunctionComponent, useMemo, useState } from 'react';

import SelectListPicker, { type SelectListPickerElements } from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import SearchFilter from '../../../../components/SearchFilter';
import { type Team } from '../../../../utils/api-types';
import { type Option } from '../../../../utils/Option';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import TagsFilter from '../../common/filters/TagsFilter';
import CreateTeam from '../../components/teams/CreateTeam';

interface Props {
  teams: Team[];
  lessonsCategoryId: string;
  lessonsCategoryTeamsIds: string[];
  handleUpdateTeams: (lessonsCategoryId: string, teamsIds: string[]) => void;
}

const LessonsCategoryAddTeams: FunctionComponent<Props> = ({
  teams,
  lessonsCategoryId,
  lessonsCategoryTeamsIds,
  handleUpdateTeams,
}) => {
  const { t } = useFormatter();

  const [open, setOpen] = useState<boolean>(false);
  const [keyword, setKeyword] = useState<string>('');
  const [teamsIds, setTeamsIds] = useState<string[]>([]);
  const [tags, setTags] = useState<Option[]>([]);

  const handleClose = () => {
    setOpen(false);
    setKeyword('');
    setTeamsIds([]);
  };

  const toggleTeam = (teamId: string) => {
    if (teamsIds.includes(teamId)) {
      setTeamsIds(teamsIds.filter(id => id !== teamId));
    } else {
      setTeamsIds([...teamsIds, teamId]);
    }
  };

  const selectAllTeams = () => {
    const teamsToAdd = teams
      .map(n => n.team_id)
      .filter(n => !lessonsCategoryTeamsIds.includes(n));
    setTeamsIds(teamsToAdd);
  };

  const submitAddTeams = () => {
    handleUpdateTeams(lessonsCategoryId, [
      ...lessonsCategoryTeamsIds,
      ...teamsIds,
    ]);
    handleClose();
  };

  const filterByKeyword = (n: Team) => keyword === ''
    || (n.team_name || '').toLowerCase().indexOf(keyword.toLowerCase())
    !== -1
    || (n.team_description || '')
      .toLowerCase()
      .indexOf(keyword.toLowerCase()) !== -1;
  const filteredTeams = teams
    .filter(
      n => tags.length === 0
        || tags.some(tag => (n.team_tags ?? []).includes(tag.id)),
    )
    .filter(filterByKeyword);

  const elements: SelectListPickerElements<Team> = useMemo(() => ({
    icon: { value: () => <CastForEducationOutlined /> },
    headers: [
      {
        field: 'team_name',
        label: 'Name',
        isSortable: true,
        value: (team: Team) => team.team_name,
        width: 45,
      },
      {
        field: 'team_description',
        label: 'Description',
        isSortable: true,
        value: (team: Team) => team.team_description ?? '',
        width: 30,
      },
      {
        field: 'team_tags',
        label: 'Tags',
        value: (team: Team) => <ItemTags variant="list" limit={1} tags={team.team_tags} />,
        width: 25,
      },
    ],
  }), []);

  const headerComponent = (
    <Box sx={{
      display: 'flex',
      gap: 1,
    }}
    >
      <SearchFilter
        onChange={value => setKeyword(value || '')}
        fullWidth
      />
      <TagsFilter
        onAddTag={(value) => {
          if (value) {
            setTags([value]);
          }
        }}
        onClearTag={() => setTags([])}
        currentTags={tags}
        fullWidth
      />
    </Box>
  );

  return (
    <>
      <IconButton
        onClick={() => setOpen(true)}
        aria-haspopup="true"
        size="small"
        color="secondary"
      >
        <Add fontSize="small" />
      </IconButton>
      <SelectListPicker
        open={open}
        onClose={handleClose}
        onSubmit={submitAddTeams}
        title={t('Add target teams in this lessons learned category')}
        submitLabel={t('Add')}
        headerComponent={headerComponent}
        headerActions={(
          <Button
            onClick={selectAllTeams}
            variant="outlined"
            size="small"
          >
            {t('Select all')}
          </Button>
        )}
        values={filteredTeams}
        elements={elements}
        selectedIds={teamsIds}
        lockedIds={lessonsCategoryTeamsIds}
        onToggle={toggleTeam}
        getId={element => element.team_id}
        buttonComponent={(
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
            <CreateTeam
              inline
              onCreate={result => setTeamsIds(prev => [...prev, result.team_id])}
            />
          </Can>
        )}
      />
    </>
  );
};

export default LessonsCategoryAddTeams;
