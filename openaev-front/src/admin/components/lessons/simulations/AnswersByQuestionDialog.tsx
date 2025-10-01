import { Button, LinearProgress, Typography, useTheme } from '@mui/material';
import { type FunctionComponent } from 'react';

import Dialog from '../../../../components/common/dialog/Dialog';
import { useFormatter } from '../../../../components/i18n';
import { type LessonsAnswer, type User } from '../../../../utils/api-types';
import { resolveUserName } from '../../../../utils/String';

interface Props {
  open: boolean;
  onClose: () => void;
  question: string;
  answers: LessonsAnswer[];
  anonymized: boolean;
  usersMap: Record<string, User>;
}

const AnswersByQuestionDialog: FunctionComponent<Props> = ({ open, onClose, question, answers, anonymized, usersMap }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <Dialog
      open={open}
      handleClose={onClose}
      title={question}
      maxWidth="lg"
    >
      <div style={{
        display: 'flex',
        flexDirection: 'column',
      }}
      >

        {answers.map((answer) => {
          let userName = '';
          if (anonymized) userName = t('Anonymized');
          if (!anonymized && answer.lessons_answer_user) userName = resolveUserName(usersMap[answer.lessons_answer_user as string]);
          return (
            <div
              key={answer.lessonsanswer_id}
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr 1fr 1fr',
                borderBottom: `1px solid ${theme.palette.background.paper}`,
                paddingBottom: 25,
                paddingTop: 25,
                gap: '10px',
              }}
            >
              <Typography variant="h4">{t('User')}</Typography>
              <Typography variant="h4">{t('Score')}</Typography>
              <Typography variant="h4">{t('What worked well')}</Typography>
              <Typography variant="h4">{t('What didn\'t work well')}</Typography>

              <Typography>{userName}</Typography>
              <div style={{
                width: '80%',
                display: 'flex',
                alignItems: 'center',
              }}
              >
                <LinearProgress
                  variant="determinate"
                  value={answer.lessons_answer_score}
                  style={{
                    flex: 1,
                    marginRight: 8,
                  }}
                />
                <Typography variant="body2" color="text.secondary">
                  {answer.lessons_answer_score}
                  %
                </Typography>
              </div>
              <Typography>{answer.lessons_answer_positive}</Typography>
              <Typography>{answer.lessons_answer_negative}</Typography>
            </div>
          );
        })}
        <Button style={{ marginLeft: 'auto' }} onClick={onClose}>
          {' '}
          {t('Close')}
          {' '}
        </Button>
      </div>

    </Dialog>
  );
};

export default AnswersByQuestionDialog;
