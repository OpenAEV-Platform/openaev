import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../components/i18n';

const useStyles = makeStyles()({ root: { flexGrow: 1 } });

const Dashboard = () => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  return <div className={classes.root}>{t('Player dashboard!')}</div>;
};

export default Dashboard;
