import { ReplyOutlined } from '@mui/icons-material';
import { Button, Dialog, DialogContent, DialogTitle, GridLegacy, Paper, Typography } from '@mui/material';
import * as R from 'ramda';
import { useContext, useState } from 'react';
import { useDispatch } from 'react-redux';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchInjectCommunications } from '../../../../../actions/Communication';
import { executeInject, fetchExerciseInjects } from '../../../../../actions/Inject';
import { fetchPlayers } from '../../../../../actions/User';
import Transition from '../../../../../components/common/Transition';
import { useFormatter } from '../../../../../components/i18n';
import ItemTags from '../../../../../components/ItemTags';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { PermissionsContext } from '../../../common/Context.js';
import AnimationMenu from '../AnimationMenu';
import Communication from './Communication';
import CommunicationForm from './CommunicationForm';

const useStyles = makeStyles()(() => ({
  container: {
    margin: '0 0 50px 0',
    padding: '0 200px 0 0',
  },
  paper: {
    position: 'relative',
    padding: '20px 20px 0 20px',
    overflow: 'hidden',
    height: '100%',
  },
}));

const Inject = () => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useDispatch();
  const [reply, setReply] = useState(null);
  const { t, fndt, fldt } = useFormatter();
  const { injectId, exerciseId } = useParams();
  const { permissions } = useContext(PermissionsContext);

  // Fetching data
  const { exercise, inject, communications, usersMap } = useHelper((helper) => {
    return {
      exercise: helper.getExercise(exerciseId),
      inject: helper.getInject(injectId),
      communications: helper.getInjectCommunications(injectId),
      usersMap: helper.getUsersMap(),
    };
  });
  useDataLoader(() => {
    dispatch(fetchExerciseInjects(exerciseId));
    dispatch(fetchInjectCommunications(exerciseId, injectId));
    dispatch(fetchPlayers());
  });
  const sortCommunications = R.sortWith([
    R.descend(R.prop('communication_received_at')),
  ]);
  // Rendering
  const handleOpenReply = communicationId => setReply(communicationId);
  const handleCloseReply = () => setReply(null);
  const onSubmitReply = (topic, data) => {
    let body = data.communication_content;
    const lastCommunication = topic.communication_communications.length > 0
      ? R.head(topic.communication_communications)
      : topic;
    body += `<br />
<hr style=3D"display:inline-block;width:98%" tabindex=3D"-1">
<div id=3D"divRplyFwdMsg" dir=3D"ltr">
<font face=3D"Calibri, sans-serif" style=3D"font-size:11pt">
<b>From:</b> ${lastCommunication.communication_from
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')}<br>
<b>Sent:</b> ${fldt(lastCommunication.communication_sent_at)}<br>
<b>Subject:</b> ${lastCommunication.communication_subject}
</font>
</div>
<blockquote>
<div dir=3D"ltr">
<div class=3D"x_elementToProof" style=3D"font-family:Calibri,Arial,Helvetica,sans-serif; font-size:12pt;">
 ${
    lastCommunication.communication_content
    && lastCommunication.communication_content.length > 10
      ? lastCommunication.communication_content.replaceAll('\n', '<br />')
      : lastCommunication.communication_content_html
  }
</div>
</div>
</blockquote>`;
    const inputValues = {
      inject_title: 'Manual email',
      inject_description: 'Manual email',
      inject_injector_contract: inject.inject_injector_contract.injector_contract_id,
      inject_content: {
        inReplyTo: lastCommunication.communication_message_id,
        subject: data.communication_subject,
        body,
      },
      inject_users: topic.communication_users,
    };
    return dispatch(
      executeInject(exerciseId, inputValues, data.communication_file),
    ).then(() => handleCloseReply());
  };
  if (inject && communications) {
    // Group communication by subject
    const communicationsWithMails = R.map(
      n => R.assoc(
        'communication_mails',
        R.map(
          o => (usersMap[o] ? usersMap[o].user_email : '').toLowerCase(),
          n.communication_users,
        ),
        n,
      ),
      communications,
    );
    const topics = R.pipe(
      R.filter(n => !n.communication_subject.toLowerCase().includes('re: ')),
      R.map(n => R.assoc(
        'communication_communications',
        sortCommunications(
          R.filter(
            o => o.communication_subject.toLowerCase().includes('re: ')
              && ((o.communication_animation
                && R.any(
                  p => o.communication_to
                    .toLowerCase()
                    .includes(p.toLowerCase()),
                  n.communication_mails,
                ))
                || R.any(
                  p => o.communication_from
                    .toLowerCase()
                    .includes(p.toLowerCase()),
                  n.communication_mails,
                )),
            communicationsWithMails,
          ),
        ),
        n,
      )),
    )(communicationsWithMails);
    let defaultSubject = '';
    let topic = null;
    const defaultContent = '';
    if (reply) {
      topic = R.head(R.filter(n => n.communication_id === reply, topics));
      defaultSubject = `Re: ${topic.communication_subject}`;
    }

    return (
      <div className={classes.container}>
        <AnimationMenu exerciseId={exerciseId} />
        <GridLegacy container={true} spacing={3}>
          <GridLegacy item={true} xs={6} style={{ marginTop: -10 }}>
            <Typography variant="h4">{t('Inject context')}</Typography>
            <Paper variant="outlined" classes={{ root: classes.paper }}>
              <GridLegacy container={true} spacing={3}>
                <GridLegacy item={true} xs={6}>
                  <Typography variant="h3">{t('Title')}</Typography>
                  {inject.inject_title}
                </GridLegacy>
                <GridLegacy item={true} xs={6}>
                  <Typography variant="h3">{t('Description')}</Typography>
                  {inject.inject_description}
                </GridLegacy>
                <GridLegacy item={true} xs={6}>
                  <Typography variant="h3">{t('Sent at')}</Typography>
                  {fndt(inject.inject_sent_at)}
                </GridLegacy>
                <GridLegacy item={true} xs={6}>
                  <Typography variant="h3">
                    {t('Sender email address')}
                  </Typography>
                  {exercise.exercise_mail_from}
                </GridLegacy>
              </GridLegacy>
            </Paper>
          </GridLegacy>
          <GridLegacy item={true} xs={6} style={{ marginTop: -10 }}>
            <Typography variant="h4">{t('Inject details')}</Typography>
            <Paper variant="outlined" classes={{ root: classes.paper }}>
              <GridLegacy container={true} spacing={3}>
                <GridLegacy item={true} xs={6}>
                  <Typography variant="h3">{t('Targeted players')}</Typography>
                  {inject.inject_users_number}
                </GridLegacy>
                <GridLegacy item={true} xs={6}>
                  <Typography variant="h3">{t('Tags')}</Typography>
                  <ItemTags tags={inject.inject_tags} />
                </GridLegacy>
                <GridLegacy item={true} xs={6}>
                  <Typography variant="h3">{t('Documents')}</Typography>
                </GridLegacy>
                <GridLegacy item={true} xs={6}>
                  <Typography variant="h3">{t('Teams')}</Typography>
                </GridLegacy>
              </GridLegacy>
            </Paper>
          </GridLegacy>
        </GridLegacy>
        <br />
        <div style={{ marginTop: 40 }}>
          <Typography variant="h4" style={{ float: 'left' }}>
            {t('Mails')}
          </Typography>
          <div className="clearfix" />
          {topics.map((currentTopic) => {
            const topicUsers = currentTopic.communication_users.map(
              userId => usersMap[userId] ?? {},
            );
            return (
              <div key={currentTopic.communication_id}>
                <Communication
                  communication={currentTopic}
                  communicationUsers={topicUsers}
                  isTopic={true}
                />
                {currentTopic.communication_communications.toReversed().map(
                  (communication) => {
                    const communicationUsers = communication.communication_users.map(
                      userId => usersMap[userId] ?? {},
                    );
                    return (
                      <Communication
                        key={communication.communication_id}
                        communication={communication}
                        communicationUsers={communicationUsers}
                        isTopic={false}
                      />
                    );
                  },
                )}
                {permissions.canManage && (
                  <div style={{
                    display: 'flex',
                    justifyContent: 'flex-end',
                  }}
                  >
                    <Button
                      variant="outlined"
                      style={{ marginBottom: 20 }}
                      startIcon={<ReplyOutlined />}
                      onClick={() => handleOpenReply(currentTopic.communication_id)}
                    >
                      {t('Reply')}
                    </Button>
                  </div>
                )}

              </div>
            );
          })}
        </div>
        <Dialog
          open={reply !== null}
          TransitionComponent={Transition}
          onClose={handleCloseReply}
          fullWidth={true}
          maxWidth="md"
          PaperProps={{ elevation: 1 }}
        >
          <DialogTitle>{t('Reply')}</DialogTitle>
          <DialogContent style={{ overflow: 'hidden' }}>
            <CommunicationForm
              initialValues={{
                communication_subject: defaultSubject,
                communication_content: defaultContent,
              }}
              onSubmit={data => onSubmitReply(topic, data)}
              handleClose={handleCloseReply}
            />
          </DialogContent>
        </Dialog>
      </div>
    );
  }
  return (
    <div className={classes.container}>
      <AnimationMenu exerciseId={exerciseId} />
      <Loader />
    </div>
  );
};

export default Inject;
