import { Box, Card, CardContent, Grid, IconButton, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type IconBarElement } from './IconBar-model';

interface Props { elements: IconBarElement[] }
const IconBar: FunctionComponent<Props> = ({ elements }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <Paper
      variant="outlined"
      sx={{
        overflow: 'hidden',
        bgcolor: theme.palette.background.paper,
        marginBottom: theme.spacing(2.5),
      }}
    >
      <Box
        sx={{
          'overflowX': 'auto',
          'padding': theme.spacing(1),
          '&::-webkit-scrollbar': { height: theme.spacing(1) },
          '&::-webkit-scrollbar-thumb': {
            backgroundColor: theme.palette.action.focus,
            borderRadius: 2,
          },
        }}
      >
        <Grid
          container
          spacing={1}
          wrap="nowrap"
          sx={{ width: '100%' }}
        >
          {elements.map((element: IconBarElement) => {
            const isSelected = element.color === 'success';
            return (
              <Grid
                key={element.type}
                size={{ md: 1.5 }}
                sx={{
                  flexShrink: 0,
                  flexGrow: 1,
                  minWidth: theme.spacing(20),
                }}
              >
                <Card
                  onClick={element.function}
                  sx={{
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
              </Grid>
            );
          })}
        </Grid>
      </Box>
    </Paper>
  );
};

export default IconBar;
