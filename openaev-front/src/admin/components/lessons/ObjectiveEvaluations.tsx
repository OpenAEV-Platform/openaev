import { HowToVoteOutlined } from '@mui/icons-material';
import { Box, Button, LinearProgress, List, ListItem, ListItemIcon, ListItemText, Slider, Typography } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';

import { type ObjectiveHelper, type UserHelper } from '../../../actions/helper';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { useHelper } from '../../../store';
import { type Evaluation, type Objective, type User } from '../../../utils/api-types';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { resolveUserName } from '../../../utils/String';
import { LessonContext } from '../common/Context';

interface Props {
  objectiveId: string | null;
  handleClose: () => void;
  isUpdatable: boolean;
}

const ObjectiveEvaluations: FunctionComponent<Props> = ({ objectiveId, handleClose, isUpdatable }) => {
  const { t } = useFormatter();
  const [value, setValue] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState<boolean>(false);

  // Context
  const {
    onAddEvaluation,
    onUpdateEvaluation,
    onFetchEvaluation,
  } = useContext(LessonContext);
  // Fetching data
  const { me, objective, evaluations, usersMap }: {
    me: User;
    objective: Objective | undefined;
    evaluations: Evaluation[];
    usersMap: Record<string, User>;
  } = useHelper(
    (helper: ObjectiveHelper & UserHelper) => {
      return {
        me: helper.getMe(),
        usersMap: helper.getUsersMap(),
        objective: helper.getObjective(objectiveId ?? ''),
        evaluations: helper.getObjectiveEvaluations(objectiveId ?? ''),
      };
    },
  );

  useDataLoader(() => {
    onFetchEvaluation(objectiveId ?? '');
  });
  const currentUserEvaluation = evaluations.find(n => n.evaluation_user === me.user_id);
  const submitEvaluation = () => {
    setSubmitting(true);
    const data = { evaluation_score: value ?? undefined };
    if (currentUserEvaluation) {
      return onUpdateEvaluation(
        objectiveId ?? '',
        currentUserEvaluation.evaluation_id,
        data,
      ).then((result) => {
        // The thunk resolves the normalised `{ result, entities }` payload, whereas the context
        // types it as the `Evaluation`: keep the original runtime check on `result`.
        if ((result as unknown as { result?: string }).result) {
          return handleClose();
        }
        return result;
      });
    }
    return onAddEvaluation(objectiveId ?? '', data).then(
      (result) => {
        if ((result as unknown as { result?: string }).result) {
          return handleClose();
        }
        return result;
      },
    );
  };
  if (!objective) {
    return <Loader variant="inElement" />;
  }
  return (
    <div>
      {evaluations.length > 0 ? (
        <List style={{ padding: 0 }}>
          {evaluations.map(evaluation => (
            <ListItem key={evaluation.evaluation_id} divider>
              <ListItemIcon>
                <HowToVoteOutlined />
              </ListItemIcon>
              <ListItemText
                style={{ width: '50%' }}
                primary={resolveUserName(usersMap[evaluation.evaluation_user])}
              />
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  width: '30%',
                  marginRight: 1,
                }}
              >
                <Box sx={{
                  width: '100%',
                  mr: 1,
                }}
                >
                  <LinearProgress
                    variant="determinate"
                    value={evaluation.evaluation_score}
                  />
                </Box>
                <Box sx={{ minWidth: 35 }}>
                  <Typography variant="body2" color="text.secondary">
                    {evaluation.evaluation_score}
                    %
                  </Typography>
                </Box>
              </Box>
            </ListItem>
          ))}
        </List>
      ) : (
        <List style={{ padding: 0 }}>
          <ListItem divider>
            <ListItemIcon>
              <HowToVoteOutlined />
            </ListItemIcon>
            <ListItemText
              style={{ width: '50%' }}
              primary={
                <i>{t('There is no evaluation for this objective yet')}</i>
              }
            />
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                width: '30%',
                marginRight: 1,
              }}
            >
              <Box sx={{
                width: '100%',
                mr: 1,
              }}
              >
                <LinearProgress variant="determinate" value={0} />
              </Box>
              <Box sx={{ minWidth: 35 }}>
                <Typography variant="body2" color="text.secondary">
                  -
                </Typography>
              </Box>
            </Box>
          </ListItem>
        </List>
      )}
      {isUpdatable && (
        <Box
          sx={{
            width: '100%',
            marginTop: '30px',
            padding: '0 5px 0 5px',
          }}
        >
          <Typography variant="h4">{t('My evaluation')}</Typography>
          <Slider
            aria-label={t('Score')}
            value={
              value === null
                ? currentUserEvaluation?.evaluation_score || 10
                : value
            }
            onChange={(_, val) => setValue(Array.isArray(val) ? val[0] : val)}
            valueLabelDisplay="auto"
            step={5}
            marks
            min={10}
            max={100}
          />
        </Box>
      )}
      <div style={{
        float: 'right',
        marginTop: 20,
      }}
      >
        <Button
          variant="outlined"
          color="primary"
          onClick={handleClose}
          style={{ marginRight: isUpdatable ? 10 : 0 }}
          disabled={submitting}
        >
          {isUpdatable ? t('Cancel') : t('Close')}
        </Button>
        {isUpdatable && (
          <Button
            variant="contained"
            color="primary"
            onClick={submitEvaluation}
            disabled={submitting}
          >
            {t('Evaluate')}
          </Button>
        )}
      </div>
    </div>
  );
};

export default ObjectiveEvaluations;
