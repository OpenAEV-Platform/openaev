import { ClearOutlined, InfoOutlined } from '@mui/icons-material';
import { AppBar, IconButton, Toolbar, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type ToolTasks } from './BulkToolBar-model';

const useStyles = makeStyles()(theme => ({
  title: {
    flex: '1',
    fontSize: theme.typography.h4.fontSize,
  },
  numberOfSelectedElements: {
    padding: theme.spacing(0.5, 1, 0.5, 1),
    marginRight: 5,
    backgroundColor: theme.palette.background.accent,
  },
}));

interface Props {
  info: string;
  numberOfSelectedElements: number;
  handleClearSelectedElements: () => void;
  toolTasks: ToolTasks[];
}

const BulkToolBar: FunctionComponent<Props> = ({ info, numberOfSelectedElements, handleClearSelectedElements, toolTasks }) => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { t } = useFormatter();

  return (
    <AppBar
      position="fixed"
      color="default"
      sx={{
        top: 'auto',
        bottom: 0,
      }}
    >
      <Toolbar style={{ minHeight: theme.spacing(7) }}>
        <Typography
          className={classes.title}
          color="inherit"
          variant="subtitle1"
        >
          <span className={classes.numberOfSelectedElements}>
            {numberOfSelectedElements}
          </span>
          {' '}
          {t('selected')}
          {' '}
          <IconButton
            aria-label="clear"
            disabled={
              numberOfSelectedElements === 0
            }
            onClick={handleClearSelectedElements}
            size="small"
            color="primary"
          >
            <ClearOutlined fontSize="small" />
          </IconButton>
        </Typography>

        <InfoOutlined fontSize="small" color="info" />
        <Typography variant="body2" sx={{ marginLeft: theme.spacing(1) }}>
          <span>
            {' '}
            {t(info)}
            {' '}
          </span>
        </Typography>

        <div style={{ marginLeft: theme.spacing(2.5) }}>
          {toolTasks.map((toolTask) => {
            return (
              <IconButton
                key={toolTask.type}
                size="small"
                color="primary"
                onClick={toolTask.function}
              >
                {toolTask.icon()}
              </IconButton>
            );
          })}
        </div>

      </Toolbar>
    </AppBar>
  );
};

export default BulkToolBar;
