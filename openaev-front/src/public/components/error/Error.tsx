import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../components/i18n';
import { useQueryParameter } from '../../../utils/Environment';

const useStyles = makeStyles()(() => ({
  root: {
    position: 'relative',
    flexGrow: 1,
    padding: 60,
  },
  container: {
    margin: '0 auto',
    width: '90%',
  },
  logo: {
    width: 100,
    margin: '0px 0px 40px 0px',
  },
}));

const Error = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { classes } = useStyles();
  const [code] = useQueryParameter(['code']);

  const key = `errors.${code ?? 'UNKNOWN'}`;
  const translated = t(key);
  const message = translated === key ? t('errors.UNKNOWN') : translated;

  return (
    <div className={classes.root}>
      <div className={classes.container}>
        <div style={{ textAlign: 'center' }}>
          <img src={theme.logo} alt="logo" className={classes.logo} />
        </div>
        <Typography variant="h3" style={{ textAlign: 'center' }}>
          {message}
        </Typography>
      </div>
    </div>
  );
};
export default Error;
