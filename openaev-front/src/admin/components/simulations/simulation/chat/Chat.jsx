import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../components/i18n';
import AnimationMenu from '../AnimationMenu';

const useStyles = makeStyles()(() => ({
  container: {
    margin: '10px 0 50px 0',
    padding: '0 100px 0 0',
  },
}));

const Chat = () => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { exerciseId } = useParams();
  return (
    <div className={classes.container}>
      <AnimationMenu exerciseId={exerciseId} />
      {t('Chat')}
    </div>
  );
};

export default Chat;
