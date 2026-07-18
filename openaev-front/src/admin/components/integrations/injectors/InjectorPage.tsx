import { Paper } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

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

  // Always render the shared connector hero (like collectors/executors) with the
  // injector contracts below it. Built-in injectors have no catalog entry, but the
  // hero derives its title/type/description from the injector itself, so they must
  // no longer fall back to a bare contracts list without a header.
  return (
    <ConnectorPage
      extraInfoComponent={(
        <Paper variant="outlined" className={`paper ${classes.paperConnector}`}>
          <InjectorContracts />
        </Paper>
      )}
    />
  );
};

export default InjectorPage;
