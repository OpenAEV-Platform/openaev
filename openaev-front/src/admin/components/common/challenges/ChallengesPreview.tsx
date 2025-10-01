import {
  Alert,
  Button,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { tryChallenge } from '../../../../actions/challenge-action';
import Dialog from '../../../../components/common/dialog/Dialog';
import Empty from '../../../../components/Empty';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type Challenge, type ChallengeInformation, type ChallengeResult, type ChallengeTryInput } from '../../../../utils/api-types';
import ChallengeTryForm from '../../components/challenges/ChallengeTryForm';
import { PreviewChallengeContext } from '../Context';
import ChallengeCard from './ChallengeCard';
import ChallengesPreviewDocumentsList from './ChallengesPreviewDocumentsList';

const useStyles = makeStyles()(theme => ({
  root: {
    position: 'relative',
    flexGrow: 1,
    padding: theme.spacing(2),
  },
  logo: {
    width: 100,
    marginBottom: theme.spacing(1),
  },
  container: {
    margin: '0 auto',
    width: '90%',
  },
}));

interface Props {
  challenges: ChallengeInformation[] | undefined;
  permissions: {
    canAccess: boolean;
    isRunning: boolean;
    isLoggedIn: boolean;
  };
}

const ChallengesPreview: FunctionComponent<Props> = ({
  challenges,
  permissions,
}) => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { t } = useFormatter();
  const [currentChallenge, setCurrentChallenge] = useState<Challenge | null>(null);
  const [currentResult, setCurrentResult] = useState<ChallengeResult | null>(null);
  const value = useContext(PreviewChallengeContext);

  const handleClose = () => {
    setCurrentChallenge(null);
    setCurrentResult(null);
  };

  const submit = (cid: string | undefined, data: ChallengeTryInput) => {
    if (cid) {
      return tryChallenge(cid, data).then(result => setCurrentResult(result.data));
    }
    return null;
  };

  if (value.scenarioOrExercise) {
    const sortedChallenges = challenges?.reduce<Record<string, ChallengeInformation[]>>((acc, challenge) => {
      const category = challenge.challenge_detail?.challenge_category || '-';
      acc[category] = acc[category] || [];
      acc[category].push(challenge);
      return acc;
    }, {});

    return (
      <div className={classes.root}>
        {permissions.isLoggedIn && permissions.canAccess && value.linkToPlayerMode.length > 0 && (
          <Button
            color="secondary"
            variant="outlined"
            component={Link}
            to={value.linkToPlayerMode}
            style={{
              position: 'relative',
              float: 'right',
              top: theme.spacing(2),
              right: theme.spacing(2),
            }}
          >
            {t('Switch to player mode')}
          </Button>
        )}
        {permissions.isLoggedIn && permissions.canAccess && (
          <Button
            color="primary"
            variant="outlined"
            component={Link}
            to={value.linkToAdministrationMode}
            style={{
              position: 'relative',
              top: theme.spacing(2),
              left: theme.spacing(2),
            }}
          >
            {t('Back to administration')}
          </Button>
        )}
        <div className={classes.container}>
          <div style={{
            margin: '0 auto',
            textAlign: 'center',
          }}
          >
            <img src={theme.logo} alt="logo" className={classes.logo} />
          </div>
          <Typography
            variant="h1"
            style={{
              textAlign: 'center',
              fontSize: 40,
            }}
          >
            {value.scenarioOrExercise.name}
          </Typography>
          <Typography
            variant="h2"
            style={{ textAlign: 'center' }}
          >
            {value.scenarioOrExercise.description}
          </Typography>
          {challenges && challenges.length === 0 && (
            <div style={{ marginTop: theme.spacing(19) }}>
              <Empty message={t('No challenge in this simulation yet.')} />
            </div>
          )}
          {sortedChallenges && Object.keys(sortedChallenges).map((category: string) => {
            return (
              <div key={category}>
                <Typography
                  variant="h1"
                  style={{
                    marginTop: theme.spacing(5),
                    marginBottom: theme.spacing(3),
                  }}
                >
                  {category !== 'null' ? category : t('No category')}
                </Typography>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr 1fr',
                  gap: theme.spacing(3),
                }}
                >
                  {sortedChallenges[category].map(({ challenge_detail: challenge }: { challenge_detail?: ChallengeInformation['challenge_detail'] }) => {
                    return (
                      challenge
                        ? (
                            <ChallengeCard
                              key={challenge.challenge_id}
                              challenge={challenge as Challenge}
                              onClick={() => {
                                setCurrentChallenge(challenge as Challenge);
                              }}
                              clickable
                            />
                          )
                        : null
                    );
                  },
                  )}
                </div>
              </div>
            );
          })}
        </div>
        <Dialog
          open={currentChallenge !== null}
          handleClose={handleClose}
          maxWidth="md"
          title={currentChallenge?.challenge_name}
        >
          <>
            <ExpandableMarkdown
              source={currentChallenge?.challenge_content}
              limit={5000}
            />
            {(currentChallenge?.challenge_documents || []).length > 0 && (
              <div>
                <Typography variant="h2" style={{ marginTop: theme.spacing(3) }}>
                  {t('Documents')}
                </Typography>
                <ChallengesPreviewDocumentsList currentChallenge={currentChallenge} />
              </div>
            )}
            <Typography variant="h2" style={{ marginTop: theme.spacing(3) }}>
              {t('Results')}
            </Typography>
            {currentResult !== null && (
              <div>
                {currentResult.result === true ? (
                  <Alert severity="success">
                    {t('Flag is correct! It has been successfully submitted.')}
                  </Alert>
                ) : (
                  <Alert
                    severity="error"
                    onClose={() => setCurrentResult(null)}
                  >
                    {t('Flag is not correct! Try again...')}
                  </Alert>
                )}
                <div style={{
                  float: 'right',
                  marginTop: theme.spacing(2),
                }}
                >
                  <Button onClick={handleClose} style={{ marginRight: theme.spacing(1) }}>
                    {t('Close')}
                  </Button>
                </div>
              </div>
            )}
            {currentResult === null && (
              <ChallengeTryForm onSubmit={data => submit(currentChallenge?.challenge_id, data)} handleClose={handleClose} />
            )}
          </>
        </Dialog>
      </div>
    );
  }
  return <Loader />;
};

export default ChallengesPreview;
