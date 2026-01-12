import { Box, Card, CardContent, Grid, IconButton, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type IconBarElement } from './IconBar-model';

interface Props {
  elements: IconBarElement[];
  variant?: 'grid' | 'scroll';
}
const IconBar: FunctionComponent<Props> = ({ elements, variant = 'grid' }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const isScroll = variant === 'scroll';

  return (
    <Paper
      variant="outlined"
      sx={{
        overflow: 'hidden',
        bgcolor: theme.palette.background.paper,
        marginRight: theme.spacing(2.5),
      }}
    >
      <Box
        sx={{
          'overflowX': isScroll ? 'auto' : 'visible',
          'py': 1,
          'px': 1,
          '&::-webkit-scrollbar': { height: '8px' },
          '&::-webkit-scrollbar-thumb': {
            backgroundColor: theme.palette.action.focus,
            borderRadius: 2,
          },
        }}
      >
        <Grid
          container
          spacing={1}
          wrap={isScroll ? 'nowrap' : 'wrap'}
          sx={{ width: isScroll ? 'max-content' : '100%' }}
        >
          {elements.map((element: IconBarElement) => {
            const isSelected = element.color === 'success';

            return (
              <Grid
                key={element.type}
                size={isScroll ? undefined : {
                  xs: 12,
                  sm: 6,
                  md: 1.5,
                }}
                sx={{
                  flexShrink: isScroll ? 0 : 1,
                  flexGrow: isScroll ? 0 : 1,
                  minWidth: isScroll ? '180px' : 'auto',
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
                    <Box
                      sx={{
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        gap: 0.5,
                        minHeight: 24,
                      }}
                    >
                      {element.results && element.results()}
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
                    </Box>
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
