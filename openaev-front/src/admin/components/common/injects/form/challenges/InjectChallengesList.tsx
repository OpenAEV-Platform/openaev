import { EmojiEventsOutlined } from '@mui/icons-material';
import { FormHelperText, List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useContext, useEffect, useState } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { type ChallengeHelper } from '../../../../../../actions/helper';
import { useFormatter } from '../../../../../../components/i18n';
import ItemTags from '../../../../../../components/ItemTags';
import { useHelper } from '../../../../../../store';
import type { Challenge } from '../../../../../../utils/api-types';
import { Can } from '../../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../../utils/permissions/types';
import ChallengePopover from '../../../../components/challenges/ChallengePopover';
import { ChallengeContext } from '../../../Context';
import InjectAddChallenges from './InjectAddChallenges';

const useStyles = makeStyles()(theme => ({
  columns: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr 1fr',
  },
  bodyItem: {
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    fontSize: theme.typography.h3.fontSize,
  },
}));

interface Props {
  readOnly?: boolean;
  error?: string | null;
}

const InjectChallengesList = ({ readOnly = false, error }: Props) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const { control, setValue } = useFormContext();

  const { fetchChallenges } = useContext(ChallengeContext);
  const [sortedChallenges, setSortedChallenges] = useState<Challenge[]>([]);
  const { challengesMap } = useHelper((helper: ChallengeHelper) => ({ challengesMap: helper.getChallengesMap() }));

  const injectChallengeIds: string[] = useWatch({
    control,
    name: 'inject_content.challenges',
    defaultValue: [],
  });

  useEffect(() => {
    const sortChallenges = (challenges: Challenge[]) => challenges.toSorted((a, b) => (a.challenge_name ?? '').localeCompare(b.challenge_name ?? ''));

    const challenges = injectChallengeIds.map(id => challengesMap[id]).filter(e => e !== undefined) as Challenge[];
    const missingIds = injectChallengeIds.filter(id => !challengesMap[id]);

    if (missingIds.length > 0) {
      fetchChallenges?.().then((result) => {
        const injectChallenges = injectChallengeIds.map(id => result.entities.challenges[id]).filter(a => a !== undefined);
        setSortedChallenges(sortChallenges(injectChallenges));
      });
    } else {
      setSortedChallenges(sortChallenges(challenges));
    }
  }, [injectChallengeIds]);

  const addChallenge = (ids: string[]) => setValue('inject_content.challenges', [...ids, ...injectChallengeIds]);
  const removeChallenge = (challengeId: string) => setValue('inject_content.challenges', injectChallengeIds.filter(id => id !== challengeId));

  return (
    <>
      <List>
        {sortedChallenges.map(challenge => (
          <ListItem
            key={challenge.challenge_id}
            divider
            secondaryAction={(
              <ChallengePopover
                inline
                challenge={challenge}
                onRemoveChallenge={removeChallenge}
                disabled={readOnly}
              />
            )}
          >
            <ListItemIcon>
              <EmojiEventsOutlined />
            </ListItemIcon>
            <ListItemText
              primary={(
                <div className={classes.columns}>
                  <div className={classes.bodyItem}>
                    {t(challenge.challenge_category ?? 'Unknown')}
                  </div>
                  <div className={classes.bodyItem}>
                    {challenge.challenge_name}
                  </div>
                  <div className={classes.bodyItem}>
                    <ItemTags
                      variant="reduced-view"
                      tags={challenge.challenge_tags}
                    />
                  </div>
                </div>
              )}
            />
          </ListItem>
        ))}
      </List>
      <Can I={ACTIONS.ACCESS} a={SUBJECTS.CHALLENGES}>
        <InjectAddChallenges
          injectChallengesIds={injectChallengeIds ?? []}
          handleAddChallenges={addChallenge}
          handleRemoveChallenge={removeChallenge}
          disabled={readOnly}
          error={error}
        />
      </Can>
      {error && (
        <FormHelperText error>
          {error}
        </FormHelperText>
      )}
    </>

  );
};

export default InjectChallengesList;
