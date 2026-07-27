import { ControlPointOutlined, EmojiEventsOutlined } from '@mui/icons-material';
import { Box, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchChallenges } from '../../../../../../actions/challenge-action';
import { type ChallengeHelper } from '../../../../../../actions/helper';
import SelectListPicker, { type SelectListPickerElements } from '../../../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../../../components/i18n';
import SearchFilter from '../../../../../../components/SearchFilter';
import { useHelper } from '../../../../../../store';
import { type Challenge } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import useDataLoader from '../../../../../../utils/hooks/useDataLoader';
import { type Option } from '../../../../../../utils/Option';
import CreateChallenge from '../../../../components/challenges/CreateChallenge';
import TagsFilter from '../../../filters/TagsFilter';

const useStyles = makeStyles()(theme => ({
  icon: { minWidth: 30 },
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
  textError: {
    fontSize: 15,
    color: theme.palette.error.main,
    fontWeight: 500,
  },
}));

interface Props {
  handleAddChallenges: (challengeIds: string[]) => void;
  handleRemoveChallenge: (challengeId: string) => void;
  injectChallengesIds: string[];
  disabled?: boolean;
  error?: string | null;
}

const InjectAddChallenges: FunctionComponent<Props> = ({
  handleAddChallenges,
  handleRemoveChallenge,
  injectChallengesIds,
  disabled = false,
  error,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [open, setOpen] = useState(false);

  const { challenges } = useHelper((helper: ChallengeHelper) => ({ challenges: helper.getChallenges() }));

  useDataLoader(() => {
    if (open) {
      dispatch(fetchChallenges());
    }
  }, [open]);

  const [keyword, setKeyword] = useState('');
  const [challengesIds, setChallengesIds] = useState<string[]>([]);
  const [tags, setTags] = useState<Option[]>([]);

  const handleOpen = () => setOpen(true);

  const handleClose = () => {
    setOpen(false);
    setKeyword('');
    setChallengesIds([]);
  };

  const toggleChallenge = (challengeId: string) => {
    if (challengesIds.includes(challengeId)) {
      setChallengesIds(challengesIds.filter(u => u !== challengeId));
    } else if (injectChallengesIds.includes(challengeId)) {
      handleRemoveChallenge(challengeId);
    } else {
      setChallengesIds(R.append(challengeId, challengesIds));
    }
  };

  const submitAddChallenges = () => {
    handleAddChallenges(challengesIds);
    handleClose();
  };

  const onCreate = (result: string) => {
    setChallengesIds(prev => [...prev, result]);
  };

  const filterByKeyword = (n: Challenge) => keyword === ''
    || (n.challenge_name || '').toLowerCase().indexOf(keyword.toLowerCase())
    !== -1
    || (n.challenge_content || '')
      .toLowerCase()
      .indexOf(keyword.toLowerCase()) !== -1
      || (n.challenge_category || '')
        .toLowerCase()
        .indexOf(keyword.toLowerCase()) !== -1;
  const filteredChallenges = R.pipe(
    R.filter(
      (n: Challenge) => tags.length === 0
        || R.any(
          (filter: string) => R.includes(filter, n.challenge_tags),
          R.pluck('id', tags),
        ),
    ),
    R.filter(filterByKeyword),
    R.take(20),
  )(challenges);

  const elements: SelectListPickerElements<Challenge> = useMemo(() => ({
    icon: { value: () => <EmojiEventsOutlined /> },
    headers: [
      {
        field: 'challenge_name',
        label: 'Name',
        isSortable: true,
        value: (challenge: Challenge) => challenge.challenge_name,
        width: 60,
      },
      {
        field: 'challenge_category',
        label: 'Category',
        isSortable: true,
        value: (challenge: Challenge) => challenge.challenge_category ?? '',
        width: 40,
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
        onChange={(value?: string) => setKeyword(value || '')}
        fullWidth
      />
      <TagsFilter
        onAddTag={(value: Option) => {
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
      <ListItemButton
        divider
        onClick={handleOpen}
        color="primary"
        disabled={disabled}
      >
        <ListItemIcon classes={{ root: classes.icon }}>
          <ControlPointOutlined color={error ? 'error' : 'primary'} fontSize="small" />
        </ListItemIcon>
        <ListItemText
          primary={t('Add challenges')}
          classes={{ primary: error ? classes.textError : classes.text }}
        />
      </ListItemButton>
      {/* Inline dialog: the inject form is already a drawer (never drawer over drawer). */}
      <SelectListPicker<Challenge>
        open={open}
        onClose={handleClose}
        onSubmit={submitAddChallenges}
        title={t('Add challenge in this inject')}
        submitLabel={t('Add')}
        inline
        headerComponent={headerComponent}
        values={filteredChallenges}
        elements={elements}
        selectedIds={[...injectChallengesIds, ...challengesIds]}
        onToggle={toggleChallenge}
        getId={element => element.challenge_id}
        buttonComponent={(
          <CreateChallenge
            inline
            onCreate={onCreate}
          />
        )}
      />
    </>
  );
};

export default InjectAddChallenges;
