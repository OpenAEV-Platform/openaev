import { Paper } from '@filigran/design-system';
import { CheckCircleOutlineOutlined } from '@mui/icons-material';
import { AppBar, Toolbar } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchComcheckStatus } from '../../../actions/Comcheck';
import { useFormatter } from '../../../components/i18n';

const useStyles = makeStyles()(() => ({
  container: {
    textAlign: 'center',
    margin: '0 auto',
    width: 500,
  },
  content: {
    width: '100%',
    padding: 20,
  },
  appBar: { textAlign: 'center' },
  logo: {
    width: 200,
    margin: '0px 0px 50px 0px',
  },
  subtitle: {
    width: '100%',
    // Inside a filled `AppBar color="primary"`: the ink has to be the one MUI computes
    // against that fill (`primary.contrastText`), not the ground ink of the page.
    color: 'inherit',
    fontWeight: 400,
    fontSize: 18,
    textAlign: 'center',
  },
}));

const Comcheck = () => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { fldt, t } = useFormatter();
  const { statusId } = useParams();
  // loading, then answered (the status), refused (409: already answered or closed) or failed.
  const [outcome, setOutcome] = useState({ state: 'loading' });
  // One request per status id for this mounted page: the answer is recorded by the request itself, so the replay
  // of the effect by StrictMode (development) must reuse it, or its second request would get the 409 of the first.
  // A ref lives and dies with the mounted page: a later visit mounts again and asks again.
  const request = useRef(null);
  useEffect(() => {
    if (request.current?.statusId !== statusId) {
      request.current = {
        statusId,
        promise: fetchComcheckStatus(statusId),
      };
    }
    let current = true;
    request.current.promise
      .then(status => current && setOutcome({
        state: 'answered',
        status,
      }))
      .catch(error => current && setOutcome({ state: (error?.response?.status ?? error?.status) === 409 ? 'refused' : 'failed' }));
    return () => {
      current = false;
    };
  }, [statusId]);
  const status = outcome.status;
  const [dimension, setDimension] = useState({
    width: window.innerWidth,
    height: window.innerHeight,
  });
  const updateWindowDimensions = () => {
    setDimension({
      width: window.innerWidth,
      height: window.innerHeight,
    });
  };
  useEffect(() => {
    window.addEventListener('resize', updateWindowDimensions);
    return () => window.removeEventListener('resize', updateWindowDimensions);
  });
  const comcheckHeight = 400;
  const marginTop = dimension.height / 2 - comcheckHeight / 2 - 200;
  return (
    <div className={classes.container} style={{ marginTop }}>
      <img src={theme.logo} alt="logo" className={classes.logo} />
      <Paper padding={0}>
        <AppBar color="primary" position="relative" className={classes.appBar}>
          <Toolbar>
            <div className={classes.subtitle}>{t('Communication check')}</div>
          </Toolbar>
        </AppBar>
        <div className={classes.content}>
          {outcome.state === 'refused' && (
            <div data-testid="comcheck-refused">
              {t('This communication check has already been answered or is closed.')}
            </div>
          )}
          {(outcome.state === 'answered' || outcome.state === 'failed') && (
            <>
              {t('Your communication check is')}
          &nbsp;
              <strong>
                {status?.comcheckstatus_receive_date
                  ? t('successful')
                  : t('failed')}
              </strong>
              .
              <br />
              <CheckCircleOutlineOutlined color="success" sx={{ fontSize: 50 }} />
              <br />
              <pre>
                {t('Verification done at')}
                {' '}
                {fldt(status?.comcheckstatus_receive_date)}
                .
              </pre>
            </>
          )}
        </div>
      </Paper>
    </div>
  );
};

export default Comcheck;
