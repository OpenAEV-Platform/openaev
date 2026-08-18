import { IconButton as FdsIconButton } from '@filigran/design-system';
import { Add, CastForEducationOutlined } from '@mui/icons-material';
import { Box, Button } from '@mui/material';
import * as R from 'ramda';
import { useMemo, useState } from 'react';

import SelectListPicker from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import SearchFilter from '../../../../components/SearchFilter';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import TagsFilter from '../../common/filters/TagsFilter';
import CreateTeam from '../../components/teams/CreateTeam';

const LessonsCategoryAddTeams = ({
  teams,
  lessonsCategoryId,
  lessonsCategoryTeamsIds,
  handleUpdateTeams,
}) => {
  const { t } = useFormatter();

  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [teamsIds, setTeamsIds] = useState([]);
  const [tags, setTags] = useState([]);

  const handleClose = () => {
    setOpen(false);
    setKeyword('');
    setTeamsIds([]);
  };

  const toggleTeam = (teamId) => {
    if (teamsIds.includes(teamId)) {
      setTeamsIds(teamsIds.filter(id => id !== teamId));
    } else {
      setTeamsIds([...teamsIds, teamId]);
    }
  };

  const selectAllTeams = () => {
    const teamsToAdd = R.pipe(
      R.map(n => n.team_id),
      R.filter(n => !lessonsCategoryTeamsIds.includes(n)),
    )(teams);
    setTeamsIds(teamsToAdd);
  };

  const submitAddTeams = () => {
    handleUpdateTeams(lessonsCategoryId, [
      ...lessonsCategoryTeamsIds,
      ...teamsIds,
    ]);
    handleClose();
  };

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
  )(teams);

  const elements = useMemo(() => ({
    icon: { value: () => <CastForEducationOutlined /> },
    headers: [
      {
        field: 'team_name',
        label: 'Name',
        isSortable: true,
        value: team => team.team_name,
        width: 45,
      },
      {
        field: 'team_description',
        label: 'Description',
        isSortable: true,
        value: team => team.team_description ?? '',
        width: 30,
      },
      {
        field: 'team_tags',
        label: 'Tags',
        value: team => <ItemTags variant="list" limit={1} tags={team.team_tags} />,
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
      {/* The library icon button at `sm` — 24px, the header row's own height.
          MUI's `size="small"` renders 30px and overflowed the row. */}
      <FdsIconButton
        onClick={() => setOpen(true)}
        aria-haspopup="true"
        size="sm"
        priority="tertiary"
        icon={<Add fontSize="small" />}
      />
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
