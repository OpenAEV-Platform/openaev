import { IconButton } from '@filigran/design-system';
import { Close } from '@mui/icons-material';
import { Box, Dialog, DialogContent, DialogTitle } from '@mui/material';
import { type FunctionComponent, memo, useCallback } from 'react';
import { makeStyles } from 'tss-react/mui';

import Transition from '../../../../../../components/common/Transition';
import { useFormatter } from '../../../../../../components/i18n';
import { type EsSeries, type StructuralHistogramWidget } from '../../../../../../utils/api-types';
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
  widgetConfig: StructuralHistogramWidget;
  fullscreen: boolean;
  setFullscreen: (fullscreen: boolean) => void;
}

const SecurityCoverage: FunctionComponent<Props> = ({ widgetId, widgetConfig, widgetTitle, data, fullscreen, setFullscreen }) => {
  const { t } = useFormatter();
  // Standard hooks
  const { classes } = useStyles();

  const handleClose = useCallback(() => setFullscreen(false), [setFullscreen]);

  if (fullscreen) {
    return (
      <Dialog
        open={fullscreen}
        onClose={handleClose}
        fullScreen
        slots={{ transition: Transition }}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogTitle className={classes.headerFull}>
          <IconButton
            icon={<Close fontSize="small" />}
            aria-label={t('Close')}
            onClick={handleClose}
            priority="tertiary"
            size="md"
          />
          {widgetTitle}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex' }}>
            <SecurityCoverageContent widgetId={widgetId} widgetConfig={widgetConfig} data={data} />
          </Box>
        </DialogContent>
      </Dialog>
    );
  }

  return <SecurityCoverageContent widgetId={widgetId} widgetConfig={widgetConfig} data={data} />;
};

export default memo(SecurityCoverage);
