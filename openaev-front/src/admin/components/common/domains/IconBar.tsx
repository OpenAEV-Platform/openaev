import { Card, CardContent, Grid, IconButton, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type IconBarElement } from './IconBar-model';

const useStyles = makeStyles()({
  paper: {
    padding: 15,
    borderRadius: 4,
  },
});

interface Props { elements: IconBarElement[] }

const IconBar: FunctionComponent<Props> = ({ elements }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <Paper classes={{ root: classes.paper }} variant="outlined">
      <Grid container spacing={2}>
        {elements.map((element: IconBarElement) => {
          const isSelected = element.color === 'success';
          return (
            <Grid
              key={element.type}
              size={{
                xs: 12,
                sm: 6,
                md: 1.5,
              }}
            >
              <Card
                sx={{
                  'color': isSelected
                    ? theme.palette.text.primary
                    : theme.palette.text.secondary,
                  'cursor': 'pointer',
                  'transition': 'background-color 0.2s ease-in-out',
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
                    onClick={element.function}
                    sx={{
                      color: isSelected
                        ? theme.palette.text.primary
                        : theme.palette.text.secondary,
                      // '&:hover': {
                      //   backgroundColor: 'transparent',
                      // },
                    }}
                  >
                    {element.icon()}
                  </IconButton>

                  <Typography variant="subtitle1">
                    {t(element.name)}
                  </Typography>

                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'center',
                      alignItems: 'center',
                    }}
                  >
                    {element.results && element.results()}
                    <span style={{
                      fontSize: 'small',
                      fontStyle: 'italic',
                    }}
                    >
                      (
                      {element.count && element.count}
                      )
                    </span>
                  </div>
                </CardContent>
              </Card>

            </Grid>
          );
        })}
      </Grid>
    </Paper>
  );
};

export default IconBar;
