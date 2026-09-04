import { PendingActionsOutlined } from '@mui/icons-material';
import { Alert, Button, IconButton, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';

import { fetchMe } from '../../../actions/Application';
import { fetchSimulationPlayerChallenges, validateChallenge } from '../../../actions/challenge-action';
import { fetchSimulationPlayerDocuments } from '../../../actions/Document';
import { type SimulationChallengesReaderHelper } from '../../../actions/helper';
import ChallengeCard from '../../../admin/components/common/challenges/ChallengeCard';
import ChallengesPreviewDocumentsList from '../../../admin/components/common/challenges/ChallengesPreviewDocumentsList';
import { FAILED } from '../../../admin/components/common/injects/expectations/ExpectationUtils';
import ChallengeTryForm from '../../../admin/components/components/challenges/ChallengeTryForm';
import Dialog from '../../../components/common/dialog/Dialog';
import Empty from '../../../components/Empty';
import ExpandableMarkdown from '../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { useHelper } from '../../../store';
import { type ChallengeInformation, type ChallengeTryInput, type Exercise, type SimulationChallengesReader } from '../../../utils/api-types';
import { useQueryParameter } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useSimulationPermissions from '../../../utils/permissions/useSimulationPermissions';

interface ValidateChallengeResult {
  result: string;
  entities?: { simulationchallengesreaders?: Record<string, SimulationChallengesReader> };
}

const NO_CATEGORY = 'null';

const ChallengesPlayer = () => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const [currentChallengeEntry, setCurrentChallengeEntry] = useState<ChallengeInformation | null>(null);
  const [hasSubmitted, setHasSubmitted] = useState(false);
  const [userId] = useQueryParameter(['user']);
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { challengesReader }: { challengesReader: SimulationChallengesReader } = useHelper(
    (helper: SimulationChallengesReaderHelper) => ({ challengesReader: helper.getSimulationChallengesReader(exerciseId) }),
  );
  console.log('challengesReader', challengesReader);
  const { exercise_information: exercise, exercise_challenges: challenges } = challengesReader ?? {};
  const {
    challenge_detail: currentChallenge,
    challenge_expectation: currentExpectation,
    challenge_attempt: currentAttempt,
  } = currentChallengeEntry ?? {};
  // Pass the full exercise because the exercise is never loaded in the store at this point
  const permissions = useSimulationPermissions(exerciseId, exercise);

  const handleClose = () => {
    setCurrentChallengeEntry(null);
    setHasSubmitted(false);
  };

  useEffect(() => {
    dispatch(fetchMe());
    dispatch(fetchSimulationPlayerChallenges(exerciseId, userId));
    dispatch(fetchSimulationPlayerDocuments(exerciseId, userId));
  }, [dispatch, exerciseId, userId]);

  const submit = (challengeId: string | undefined, data: ChallengeTryInput) => {
    if (!challengeId) {
      return;
    }
    dispatch(validateChallenge(exerciseId, challengeId, userId, data)).then(
      (response: ValidateChallengeResult) => {
        const challengeEntries = response.entities?.simulationchallengesreaders?.[response.result]?.exercise_challenges ?? [];
        setCurrentChallengeEntry(
          challengeEntries.find(entry => entry.challenge_detail?.challenge_id === challengeId) ?? null,
        );
        setHasSubmitted(true);
      },
    );
  };

  // Result

  const resultList = currentExpectation?.inject_expectation_results ?? [];

  const noResult = () => resultList.length === 0 && !hasSubmitted;
  const hasResult = () => resultList.length > 0 || hasSubmitted;
  const validResult = () => resultList.length > 0 && resultList.every(r => r.result !== FAILED);
  const invalidResult = () => (resultList.length === 0 && hasSubmitted) || resultList.some(r => r.result === FAILED);
  const maxAttemptsExceeded = () => !!currentChallenge?.challenge_max_attempts && (currentAttempt ?? 0) >= currentChallenge.challenge_max_attempts;

  if (!exercise) {
    return <Loader />;
  }

  const challengesByCategory = (challenges ?? []).reduce<Record<string, ChallengeInformation[]>>((acc, challengeEntry) => {
    const category = challengeEntry.challenge_detail?.challenge_category || NO_CATEGORY;
    acc[category] = acc[category] ?? [];
    acc[category].push(challengeEntry);
    return acc;
  }, {});

  return (
    <div style={{
      position: 'relative',
      flexGrow: 1,
      padding: theme.spacing(2),
    }}
    >
      {permissions.isLoggedIn && permissions.canAccess && (
        <>
          <Button
            color="secondary"
            variant="outlined"
            component={Link}
            to={`/admin/simulations/${exerciseId}/challenges`}
            style={{
              position: 'absolute',
              top: theme.spacing(2),
              right: theme.spacing(2),
            }}
          >
            {t('Switch to preview mode')}
          </Button>
          <Button
            color="primary"
            variant="outlined"
            component={Link}
            to={`/admin/simulations/${exerciseId}/definition`}
            style={{
              position: 'absolute',
              top: theme.spacing(2),
              left: theme.spacing(2),
            }}
          >
            {t('Back to administration')}
          </Button>
        </>
      )}
      <div style={{
        margin: '0 auto',
        width: '90%',
      }}
      >
        <div style={{
          margin: '0 auto',
          textAlign: 'center',
        }}
        >
          <img
            src={theme.logo}
            alt="logo"
            style={{
              width: 100,
              marginBottom: theme.spacing(1),
            }}
          />
        </div>
        <Typography
          variant="h1"
          style={{
            textAlign: 'center',
            fontSize: 40,
          }}
        >
          {exercise.name}
        </Typography>
        <Typography variant="h2" style={{ textAlign: 'center' }}>
          {exercise.description}
        </Typography>
        {(challenges ?? []).length === 0 && (
          <div style={{ marginTop: theme.spacing(19) }}>
            <Empty message={t('No challenge in this simulation yet.')} />
          </div>
        )}
        {Object.entries(challengesByCategory).map(([category, categoryChallenges]) => (
          <div key={category}>
            <Typography
              variant="h1"
              style={{
                marginTop: theme.spacing(5),
                marginBottom: theme.spacing(3),
              }}
            >
              {category !== NO_CATEGORY ? category : t('No category')}
            </Typography>
            <div style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr 1fr',
              gap: theme.spacing(3),
            }}
            >
              {categoryChallenges.map((challengeEntry) => {
                const challenge = challengeEntry.challenge_detail;
                if (!challenge) {
                  return null;
                }
                const expectation = challengeEntry.challenge_expectation;
                const hasSubmittedResult = (expectation?.inject_expectation_results ?? []).some(
                  r => r.result != null,
                );
                return (
                  <ChallengeCard
                    key={challenge.challenge_id}
                    challenge={challenge}
                    onClick={() => setCurrentChallengeEntry(challengeEntry)}
                    clickable
                    actionHeader={(
                      <IconButton
                        size="large"
                        color={hasSubmittedResult ? 'success' : 'inherit'}
                      >
                        <PendingActionsOutlined fontSize="large" />
                      </IconButton>
                    )}
                  />
                );
              })}
            </div>
          </div>
        ))}
      </div>
      <Dialog
        open={currentChallengeEntry !== null}
        handleClose={handleClose}
        maxWidth="md"
        title={currentChallenge?.challenge_name}
      >
        <>
          <ExpandableMarkdown source={currentChallenge?.challenge_content} limit={5000} />
          {(currentChallenge?.challenge_documents ?? []).length > 0 && (
            <div>
              <Typography variant="h2" style={{ marginTop: theme.spacing(3) }}>
                {t('Documents')}
              </Typography>
              <ChallengesPreviewDocumentsList currentChallenge={currentChallenge ?? null} />
            </div>
          )}
          <Typography variant="h2" style={{ marginTop: theme.spacing(3) }}>
            {t('Results')}
          </Typography>
          {hasResult() && (
            <div>
              {validResult() && (
                <Alert severity="success">
                  {t('Flag is correct! It has been successfully submitted.')}
                </Alert>
              )}
              {invalidResult() && (
                <Alert severity="error" onClose={() => setHasSubmitted(false)}>
                  {t('Flag is not correct! Try again...')}
                  {maxAttemptsExceeded() && (
                    <>
                      <br />
                      {t('Max attempts exceeded.')}
                    </>
                  )}
                </Alert>
              )}
              <div style={{
                float: 'right',
                marginTop: theme.spacing(2),
              }}
              >
                <Button variant="outlined" color="primary" onClick={handleClose} style={{ marginRight: theme.spacing(1) }}>
                  {t('Close')}
                </Button>
              </div>
            </div>
          )}
          {maxAttemptsExceeded() && noResult() && (
            <Alert severity="error">{t('Max attempts exceeded.')}</Alert>
          )}
          {!maxAttemptsExceeded() && noResult() && (
            <ChallengeTryForm
              onSubmit={data => submit(currentChallenge?.challenge_id, data)}
              handleClose={handleClose}
            />
          )}
        </>
      </Dialog>
    </div>
  );
};

export default ChallengesPlayer;
