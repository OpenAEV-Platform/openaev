import { Paper } from '@mui/material';
import { useOutletContext } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type ConnectorContextLayoutType } from '../common/ConnectorLayout';
import ConnectorPage from '../common/ConnectorPage';
import InjectorContracts from './InjectorContracts';

const useStyles = makeStyles()(theme => ({
  paperConnector: {
    marginTop: theme.spacing(3),
    height: '100%',
  },
}));

const InjectorPage = () => {
  const { classes } = useStyles();
  const { catalogConnector } = useOutletContext<ConnectorContextLayoutType>();

  return (
    <>
      {catalogConnector
        ? (
            <ConnectorPage extraInfoComponent={(
              <Paper variant="outlined" className={`paper ${classes.paperConnector}`}>
                <InjectorContracts />
              </Paper>
            )}
            />
          )
        : (
            <Paper variant="outlined" className={`paper ${classes.paperConnector}`}>
              <InjectorContracts />
            </Paper>
          )}
    </>
  );
};

export default InjectorPage;
