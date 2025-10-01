import {
  ArrowDropDownOutlined,
  ArrowDropUpOutlined,
  AttachmentOutlined,
  PendingActionsOutlined,
} from '@mui/icons-material';
import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useEffect, useState } from 'react';
import { Form } from 'react-final-form';
import { useDispatch } from 'react-redux';
import { Link, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchMe } from '../../../actions/Application';
import { fetchSimulationPlayerChallenges, validateChallenge } from '../../../actions/challenge-action.js';
import { fetchSimulationPlayerDocuments } from '../../../actions/Document';
import ChallengeCard from '../../../admin/components/common/challenges/ChallengeCard.js';
import { FAILED } from '../../../admin/components/common/injects/expectations/ExpectationUtils.js';
import DocumentType from '../../../admin/components/components/documents/DocumentType';
import Transition from '../../../components/common/Transition';
import Empty from '../../../components/Empty';
import ExpandableMarkdown from '../../../components/ExpandableMarkdown';
import OldTextField from '../../../components/fields/OldTextField';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import Loader from '../../../components/Loader';
import { useHelper } from '../../../store';
import { useQueryParameter } from '../../../utils/Environment';
import useSimulationPermissions from '../../../utils/permissions/useSimulationPermissions.js';

const useStyles = makeStyles()(() => ({
  root: {
    position: 'relative',
    flexGrow: 1,
    padding: 20,
  },
  logo: {
    width: 100,
    margin: '0px 0px 10px 0px',
  },
  container: {
    margin: '0 auto',
    width: '90%',
  },
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
  bodyItem: {
    height: '100%',
    fontSize: 13,
  },
}));

const inlineStylesHeaders = {
  iconSort: {
    position: 'absolute',
    margin: '0 0 0 5px',
    padding: 0,
    top: '0px',
  },
  document_name: {
    float: 'left',
    width: '35%',
    fontSize: 12,
    fontWeight: '700',
  },
  document_type: {
    float: 'left',
    width: '20%',
    fontSize: 12,
    fontWeight: '700',
  },
  document_tags: {
    float: 'left',
    width: '30%',
    fontSize: 12,
    fontWeight: '700',
  },
};

const inlineStyles = {
  document_name: {
    float: 'left',
    width: '35%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  document_type: {
    float: 'left',
    width: '20%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  document_tags: {
    float: 'left',
    width: '30%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

const ChallengesPlayer = () => {
  const theme = useTheme();
  const { classes } = useStyles();
  const dispatch = useDispatch();
  const { t } = useFormatter();
  const [currentChallengeEntry, setCurrentChallengeEntry] = useState(null);
  const [currentResult, setCurrentResult] = useState(null);
  const [userId] = useQueryParameter(['user']);
  const [documentsSortBy, setDocumentsSortBy] = useState('document_name');
  const [documentsOrderAsc, setDocumentsOrderAsc] = useState(true);
  const { exerciseId } = useParams();
  const { challengesReader, documentsMap } = useHelper(helper => ({
    challengesReader: helper.getSimulationChallengesReader(exerciseId),
    documentsMap: helper.getDocumentsMap(),
  }));
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
    setCurrentResult(null);
  };
  useEffect(() => {
    dispatch(fetchMe());
    dispatch(fetchSimulationPlayerChallenges(exerciseId, userId));
    dispatch(fetchSimulationPlayerDocuments(exerciseId, userId));
  }, []);
  const documentsReverseBy = (field) => {
    setDocumentsSortBy(field);
    setDocumentsOrderAsc(!documentsSortBy);
  };
  const documentsSortHeader = (field, label, isSortable) => {
    const sortComponent = documentsOrderAsc
      ? (
          <ArrowDropDownOutlined style={inlineStylesHeaders.iconSort} />
        )
      : (
          <ArrowDropUpOutlined style={inlineStylesHeaders.iconSort} />
        );
    if (isSortable) {
      return (
        <div
          style={inlineStylesHeaders[field]}
          onClick={() => documentsReverseBy(field)}
        >
          <span>{t(label)}</span>
          {documentsSortBy === field ? sortComponent : ''}
        </div>
      );
    }
    return (
      <div style={inlineStylesHeaders[field]}>
        <span>{t(label)}</span>
      </div>
    );
  };
  const validate = (values) => {
    const errors = {};
    const requiredFields = ['challenge_value'];
    requiredFields.forEach((field) => {
      if (!values[field]) {
        errors[field] = t('This field is required.');
      }
    });
    return errors;
  };
  const submit = (cid, data) => {
    return dispatch(validateChallenge(exerciseId, cid, userId, data)).then(
      (result) => {
        const challengeEntries = result.entities?.simulationchallengesreaders[result.result].exercise_challenges || [];
        setCurrentChallengeEntry(
          R.head(
            challengeEntries.filter(
              n => n.challenge_detail.challenge_id === cid,
            ),
          ),
        );
        setCurrentResult('submitted');
      },
    );
  };

  // Result

  const resultList = currentExpectation?.inject_expectation_results ?? [];
  const hasManualResult = currentResult !== null;

  const noResult = () => resultList.length === 0 && !hasManualResult;
  const hasResult = () => resultList.length > 0 || hasManualResult;
  const validResult = () =>
    resultList.length > 0 && resultList.every(r => r.result !== FAILED);
  const invalidResult = () => (resultList.length === 0 && hasManualResult) || resultList.some(r => r.result === FAILED);
  const maxAttemptsExceeded = () => !!currentChallenge?.challenge_max_attempts && currentAttempt >= currentChallenge?.challenge_max_attempts;

  if (exercise) {
    const groupChallenges = R.groupBy(
      R.path(['challenge_detail', 'challenge_category']),
    );
    const sortedChallenges = groupChallenges(challenges);
    return (
      <div className={classes.root}>
        {permissions.isLoggedIn && permissions.canAccess && (
          <Button
            color="secondary"
            variant="outlined"
            component={Link}
            to={`/admin/simulations/${exerciseId}/challenges`}
            style={{
              position: 'absolute',
              top: 20,
              right: 20,
            }}
          >
            {t('Switch to preview mode')}
          </Button>
        )}
        {permissions.isLoggedIn && permissions.canAccess && (
          <Button
            color="primary"
            variant="outlined"
            component={Link}
            to={`/admin/simulations/${exerciseId}/definition`}
            style={{
              position: 'absolute',
              top: 20,
              left: 20,
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
            {exercise.name}
          </Typography>
          <Typography
            variant="h2"
            style={{ textAlign: 'center' }}
          >
            {exercise.description}
          </Typography>
          {challenges.length === 0 && (
            <div style={{ marginTop: 150 }}>
              <Empty message={t('No challenge in this simulation yet.')} />
            </div>
          )}
          {Object.keys(sortedChallenges).map(category => (
            <div key={category}>
              <Typography variant="h1" style={{ margin: '40px 0 30px 0' }}>
                {category !== 'null' ? category : t('No category')}
              </Typography>
              <div style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr 1fr',
                gap: theme.spacing(3),
              }}
              >
                {sortedChallenges[category].map((challengeEntry) => {
                  const challenge = challengeEntry.challenge_detail;
                  const expectation = challengeEntry.challenge_expectation;
                  return (
                    <ChallengeCard
                      key={challenge.challenge_id}
                      challenge={challenge}
                      onClick={() => {
                        setCurrentChallengeEntry(challengeEntry);
                      }}
                      clickable
                      actionHeader={(
                        <IconButton
                          size="large"
                          color={
                            (expectation?.inject_expectation_results?.length ?? 0) > 0
                              ? 'success'
                              : 'inherit'
                          }
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
          TransitionComponent={Transition}
          open={currentChallengeEntry !== null}
          onClose={handleClose}
          fullWidth={true}
          maxWidth="md"
          PaperProps={{ elevation: 1 }}
        >
          <DialogTitle>{currentChallenge?.challenge_name}</DialogTitle>
          <DialogContent>
            <ExpandableMarkdown
              source={currentChallenge?.challenge_content}
              limit={5000}
            />
            {(currentChallenge?.challenge_documents || []).length > 0 && (
              <div>
                <Typography variant="h2" style={{ marginTop: 30 }}>
                  {t('Documents')}
                </Typography>
                <List>
                  <ListItem
                    classes={{ root: classes.itemHead }}
                    divider={false}
                    style={{ paddingTop: 0 }}
                  >
                    <ListItemIcon>
                      <span
                        style={{
                          padding: '0 8px 0 8px',
                          fontWeight: 700,
                          fontSize: 12,
                        }}
                      >
                        &nbsp;
                      </span>
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div>
                          {documentsSortHeader('document_name', 'Name', true)}
                          {documentsSortHeader('document_type', 'Type', true)}
                          {documentsSortHeader('document_tags', 'Tags', true)}
                        </div>
                      )}
                    />
                  </ListItem>
                  {(currentChallenge?.challenge_documents || []).map(
                    (documentId) => {
                      const document = documentsMap[documentId] || {};
                      return (
                        <ListItem
                          key={document.document_id}
                          classes={{ root: classes.item }}
                          divider={true}
                          button={true}
                          component="a"
                          href={`/api/documents/${document.document_id}/file`}
                        >
                          <ListItemIcon>
                            <AttachmentOutlined />
                          </ListItemIcon>
                          <ListItemText
                            primary={(
                              <div>
                                <div
                                  className={classes.bodyItem}
                                  style={inlineStyles.document_name}
                                >
                                  {document.document_name}
                                </div>
                                <div
                                  className={classes.bodyItem}
                                  style={inlineStyles.document_type}
                                >
                                  <DocumentType
                                    type={document.document_type}
                                    variant="list"
                                  />
                                </div>
                                <div
                                  className={classes.bodyItem}
                                  style={inlineStyles.document_tags}
                                >
                                  <ItemTags
                                    variant="list"
                                    tags={document.document_tags}
                                  />
                                </div>
                              </div>
                            )}
                          />
                        </ListItem>
                      );
                    },
                  )}
                </List>
              </div>
            )}
            <Typography variant="h2" style={{ marginTop: 30 }}>
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
                  <Alert
                    severity="error"
                    onClose={() => setCurrentResult(null)}
                  >
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
                  marginTop: 20,
                }}
                >
                  <Button onClick={handleClose} style={{ marginRight: 10 }}>
                    {t('Close')}
                  </Button>
                </div>
              </div>
            )}
            {maxAttemptsExceeded() && noResult() && (
              <Alert severity="error">
                {t('Max attempts exceeded.')}
              </Alert>
            )}
            {!maxAttemptsExceeded() && noResult() && (
              <Form
                keepDirtyOnReinitialize={true}
                onSubmit={data => submit(currentChallenge?.challenge_id, data)}
                validate={validate}
                mutators={{
                  setValue: ([field, value], state, { changeValue }) => {
                    changeValue(state, field, () => value);
                  },
                }}
              >
                {({ handleSubmit, submitting, errors }) => (
                  <form id="challengeForm" onSubmit={handleSubmit}>
                    <OldTextField
                      variant="standard"
                      name="challenge_value"
                      fullWidth={true}
                      label={t('Flag')}
                    />
                    <div style={{
                      float: 'right',
                      marginTop: 20,
                    }}
                    >
                      <Button
                        onClick={handleClose}
                        style={{ marginRight: 10 }}
                        disabled={submitting}
                      >
                        {t('Cancel')}
                      </Button>
                      <Button
                        color="secondary"
                        type="submit"
                        disabled={
                          submitting || Object.keys(errors).length > 0
                        }
                      >
                        {t('Submit')}
                      </Button>
                    </div>
                  </form>
                )}
              </Form>
            )}
          </DialogContent>
        </Dialog>
      </div>
    );
  }
  return <Loader />;
};

export default ChallengesPlayer;
