import { Card, CardContent, Divider, Icon, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../../components/i18n';
import { type IconBarElement } from '../../../../../common/domains/IconBar-model';

const useStyles = makeStyles()(theme => ({
  container: {
    display: 'inline-flex',
    width: '100%',
    gap: 2,
    overflow: 'auto',
  },
  hover: {
    'background': theme.palette.background.paper,
    '&:hover': { backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)' },
    'cursor': 'pointer',
  },
}));

interface Props { elements: IconBarElement[] }

const IconBar: FunctionComponent<Props> = ({ elements }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <div className={classes.container}>
      {elements.map((element: IconBarElement) => {
        const isOpen = element.selectedType === element.type;
        return (
          <div key={element.name} style={{ flexGrow: 1 }}>
            <Card
              className={classes.hover}
              sx={{
                transition: 'all 200ms ease',
                boxShadow: isOpen ? 4 : 1,
              }}
              key={element.type}
              onClick={element.function}
            >
              <CardContent sx={{ textAlign: 'center' }}>
                <div style={{
                  display: 'flex',
                  gap: theme.spacing(2),
                  justifyContent: 'center',
                }}
                >
                  <div>
                    <Icon
                      fontSize="large"
                      sx={{ color: element.color }}
                    >
                      {element.icon()}
                    </Icon>
                    {element.name && (
                      <Typography variant="subtitle1">{t(element.name)}</Typography>
                    )}
                    <div>
                      {element.results && element.results()}
                      {element.count && element.count}
                    </div>
                  </div>
                  {isOpen
                    && (
                      <div style={{
                        display: 'flex',
                        gap: theme.spacing(2),
                      }}
                      >
                        <Divider orientation="vertical" variant="middle" />
                        <div style={{ width: theme.spacing(22) }}>
                          {element.expandedResults && element.expandedResults()}
                        </div>
                      </div>
                    )}
                </div>
              </CardContent>
            </Card>
          </div>
        );
      })}
    </div>
  );
};

export default IconBar;
