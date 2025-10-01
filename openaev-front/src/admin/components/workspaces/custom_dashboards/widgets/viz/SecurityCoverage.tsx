import { Close } from '@mui/icons-material';
import { Box, Dialog, DialogContent, DialogTitle, IconButton } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import Transition from '../../../../../../components/common/Transition';
import { type EsSeries } from '../../../../../../utils/api-types';
import SecurityCoverageContent from './SecurityCoverageContent';

const useStyles = makeStyles()(theme => ({
  headerFull: {
    backgroundColor: theme.palette.mode === 'light' ? theme.palette.background.default : theme.palette.background.nav,
    borderBottom: `1px solid ${theme.palette.divider}`,
    padding: '10px 0',
    display: 'inline-flex',
    alignItems: 'center',
  },
}));

interface Props {
  widgetId: string;
  widgetTitle: string;
  data: EsSeries[];
  fullscreen: boolean;
  setFullscreen: (fullscreen: boolean) => void;
}

const SecurityCoverage: FunctionComponent<Props> = ({ widgetId, widgetTitle, data, fullscreen, setFullscreen }) => {
  // Standard hooks
  const { classes } = useStyles();

  const handleClose = () => setFullscreen(false);

  if (fullscreen) {
    return (
      <Dialog
        open={fullscreen}
        onClose={handleClose}
        fullScreen
        PaperProps={{ elevation: 1 }}
        TransitionComponent={Transition}
      >
        <DialogTitle className={classes.headerFull}>
          <IconButton
            aria-label="Close"
            onClick={handleClose}
            size="large"
            color="primary"
          >
            <Close fontSize="small" color="primary" />
          </IconButton>
          {widgetTitle}
        </DialogTitle>
        <DialogContent>
          <Box display="flex">
            <SecurityCoverageContent widgetId={widgetId} data={data} />
          </Box>
        </DialogContent>
      </Dialog>
    );
  }

  return <SecurityCoverageContent widgetId={widgetId} data={data} />;
};

export default SecurityCoverage;
