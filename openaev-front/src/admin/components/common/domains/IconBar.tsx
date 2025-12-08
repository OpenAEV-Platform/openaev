import { Card, CardContent, Grid, IconButton, Paper, Typography } from '@mui/material';
import type { FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';
import { useTheme } from '@mui/material/styles';
import { IconBarElement } from './IconBar-model';
import { useFormatter } from '../../../../components/i18n';

const useStyles = makeStyles()({
  paper: {
    padding: 15,
    borderRadius: 4,
  },
});

interface Props {
  elements: IconBarElement[];
}

const IconBar: FunctionComponent<Props> = ({ elements }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <Paper classes={{ root: classes.paper }} variant="outlined">
      <Grid container spacing={2}>
        {elements.map((element: IconBarElement) => {
          return (
            <Grid size={{ xs: 12, sm:6, md:1.5 }}>
              <Card>
                <CardContent sx={{ textAlign: 'center', backgroundColor: theme.palette.background.paper }}>
                  <IconButton
                    key={element.type}
                    size="large"
                    color={element.color}
                    onClick={element.function}
                  >
                    {element.icon()}
                  </IconButton>
                  <Typography variant="subtitle1">{t(element.name)}</Typography>
                  <div style={{
                    display: "flex",
                    justifyContent: "center",
                    alignItems: "center",
                  }}>
                    {element.results && element.results()}
                    {element.count && element.count}
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
