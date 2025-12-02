import { Typography } from '@mui/material';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { getInjectorSelector } from '../../../../actions/selectors';
import { useSelectorHelper } from '../../../../store';

const useStyles = makeStyles()(() => ({ container: { width: '100%' } }));

const InjectorHeader = () => {
  const { classes } = useStyles();
  const { injectorId } = useParams();
  const injector = useSelectorHelper(state => getInjectorSelector(injectorId, state));
  return (
    <div className={classes.container}>
      <Typography
        variant="h1"
        gutterBottom={true}
      >
        {injector.injector_name}
      </Typography>
    </div>
  );
};

export default InjectorHeader;
