import { ControlPointOutlined, EmojiEventsOutlined } from '@mui/icons-material';
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchChallenges } from '../../../../../../actions/challenge-action';
import { type ChallengeHelper } from '../../../../../../actions/helper';
import Transition from '../../../../../../components/common/Transition';
import { useFormatter } from '../../../../../../components/i18n';
import SearchFilter from '../../../../../../components/SearchFilter';
import { useHelper } from '../../../../../../store';
import { type Challenge } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import useDataLoader from '../../../../../../utils/hooks/useDataLoader';
import { type Option } from '../../../../../../utils/Option';
import { truncate } from '../../../../../../utils/String';
import CreateChallenge from '../../../../components/challenges/CreateChallenge';
import TagsFilter from '../../../filters/TagsFilter';

const useStyles = makeStyles()(theme => ({
  box: {
    width: '100%',
    minHeight: '100%',
    padding: 20,
    border: `1px dashed ${theme.palette.divider}`,
  },
  chip: { margin: '0 10px 10px 0' },
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
  const [open, setopen] = useState(false);

  const { challenges, challengesMap } = useHelper((helper: ChallengeHelper) => ({
    challenges: helper.getChallenges(),
    challengesMap: helper.getChallengesMap(),
  }));

  useDataLoader(() => {
    if (open) {
      dispatch(fetchChallenges());
    }
  }, [open]);

  const [keyword, setKeyword] = useState('');
  const [challengesIds, setChallengesIds] = useState<string[]>([]);
  const [tags, setTags] = useState<Option[]>([]);

  const handleOpen = () => setopen(true);

  const handleClose = () => {
    setopen(false);
    setKeyword('');
    setChallengesIds([]);
  };

  const handleSearchChallenges = (value?: string) => {
    setKeyword(value || '');
  };

  const handleAddTag = (value: Option) => {
    if (value) {
      setTags([value]);
    }
  };

  const handleClearTag = () => {
    setTags([]);
  };

  const addChallenge = (challengeId: string) => {
    setChallengesIds(R.append(challengeId, challengesIds));
  };

  const removeChallenge = (challengeId: string) => {
    if (challengesIds.includes(challengeId)) {
      setChallengesIds(challengesIds.filter(u => u !== challengeId));
    } else if (injectChallengesIds.includes(challengeId)) {
      handleRemoveChallenge(challengeId);
    }
  };

  const submitAddChallenges = () => {
    handleAddChallenges(challengesIds);
    handleClose();
  };

  const onCreate = (result: string) => {
    addChallenge(result);
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
    R.take(10),
  )(challenges);
  return (
    <>
      <ListItemButton
        divider
        onClick={handleOpen}
        color="primary"
        disabled={disabled}
      >
        <ListItemIcon>
          <ControlPointOutlined color={error ? 'error' : 'primary'} />
        </ListItemIcon>
        <ListItemText
          primary={t('Add challenges')}
          classes={{ primary: error ? classes.textError : classes.text }}
        />
      </ListItemButton>
      <Dialog
        open={open}
        slots={{ transition: Transition }}
        onClose={handleClose}
        fullWidth
        maxWidth="lg"
        slotProps={{
          paper: {
            elevation: 1,
            sx: {
              minHeight: 580,
              maxHeight: 580,
            },
          },
        }}
      >
        <DialogTitle>{t('Add challenge in this inject')}</DialogTitle>
        <DialogContent>
          <Grid container spacing={3}>
            <Grid size={{ xs: 8 }}>
              <Grid container spacing={3}>
                <Grid size={{ xs: 6 }}>
                  <SearchFilter
                    onChange={handleSearchChallenges}
                    fullWidth
                  />
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <TagsFilter
                    onAddTag={handleAddTag}
                    onClearTag={handleClearTag}
                    currentTags={tags}
                    fullWidth
                  />
                </Grid>
              </Grid>
              <List>
                {filteredChallenges.map((challenge: Challenge) => {
                  const disabled = challengesIds.includes(challenge.challenge_id)
                    || injectChallengesIds.includes(challenge.challenge_id);
                  return (
                    (
                      <ListItemButton
                        key={challenge.challenge_id}
                        disabled={disabled}
                        divider
                        dense
                        onClick={() => addChallenge(challenge.challenge_id)}
                      >
                        <ListItemIcon>
                          <EmojiEventsOutlined />
                        </ListItemIcon>
                        <ListItemText
                          primary={challenge.challenge_name}
                          secondary={challenge.challenge_category}
                        />
                      </ListItemButton>
                    )
                  );
                })}
                <CreateChallenge
                  inline
                  onCreate={onCreate}
                />
              </List>
            </Grid>
            <Grid size={{ xs: 4 }}>
              <Box className={classes.box}>
                {[...injectChallengesIds, ...challengesIds].map((challengeId) => {
                  const challenge = challengesMap[challengeId];
                  return (
                    <Chip
                      key={challengeId}
                      onDelete={() => removeChallenge(challengeId)}
                      label={truncate(challenge?.challenge_name || '', 22)}
                      icon={<EmojiEventsOutlined />}
                      classes={{ root: classes.chip }}
                    />
                  );
                })}
              </Box>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>{t('Cancel')}</Button>
          <Button
            color="secondary"
            onClick={submitAddChallenges}
          >
            {t('Add')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default InjectAddChallenges;
