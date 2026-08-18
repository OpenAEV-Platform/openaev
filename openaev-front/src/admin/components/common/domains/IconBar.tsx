import { Paper } from '@filigran/design-system';
import { Card, CardContent, IconButton, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type IconBarElement } from './IconBar-model';

interface Props { elements: IconBarElement[] }
const useStyles = makeStyles()(theme => ({
  barre: {
    '&::-webkit-scrollbar': { height: theme.spacing(1) },
    '&::-webkit-scrollbar-thumb': {
      backgroundColor: theme.palette.action.focus,
      borderRadius: 2,
    },
  },
}));

const IconBar: FunctionComponent<Props> = ({ elements }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { classes } = useStyles();

  return (
    <Paper
      padding={8}
      // The scrollbar rules are pseudo-elements: they cannot live in `style`,
      // so they move to a class. `className` reaches the surface, and no DOM
      // level is added. The tinted colour here is the scrollbar THUMB's, not
      // the surface's.
      className={classes.barre}
      style={{
        marginBottom: 20,
        display: 'flex',
        flexWrap: 'nowrap',
        overflowX: 'auto',
        gap: 8,
      }}
    >
      {elements.map((element: IconBarElement) => {
        const isSelected = element.color === 'success';
        return (
          <Card
            key={element.name}
            onClick={element.function}
            sx={{
              'flexGrow': 1,
              'flexShrink': 0,
              'height': '100%',
              'cursor': 'pointer',
              'transition': theme.transitions.create('background-color'),
              'color': isSelected
                ? theme.palette.text.primary
                : theme.palette.text.secondary,
              'backgroundColor': isSelected
                ? theme.palette.action.selected
                : theme.palette.background.paper,
              '&:hover': { backgroundColor: theme.palette.action.hover },
            }}
          >
            <CardContent sx={{ textAlign: 'center' }}>
              <IconButton
                size="large"
                disableRipple
                sx={{
                  'color': 'inherit',
                  '& svg': { fontSize: '2rem' },
                }}
              >
                {element.icon()}
              </IconButton>
              <Typography
                variant="subtitle1"
                noWrap
                sx={{
                  lineHeight: 1,
                  fontSize: 14,
                }}
              >
                {t(element.name)}
              </Typography>
              {element.count !== undefined && (
                <Typography
                  variant="caption"
                  sx={{
                    fontStyle: 'italic',
                    color: theme.palette.text.secondary,
                  }}
                >
                  {element.count}
                </Typography>
              )}
            </CardContent>
          </Card>
        );
      })}
    </Paper>
  );
};

export default IconBar;
