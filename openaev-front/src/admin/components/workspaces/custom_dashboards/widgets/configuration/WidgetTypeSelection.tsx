import { Card, CardActionArea, CardContent, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import { type Widget } from '../../../../../../utils/api-types-custom';
import { renderWidgetIcon, widgetVisualizationTypes } from '../WidgetUtils';

const WidgetTypeSelection: FunctionComponent<{
  value: Widget['widget_type'];
  onChange: (type: Widget['widget_type']) => void;
}> = ({ value, onChange }) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <div
      style={{
        display: 'grid',
        gap: theme.spacing(3),
        gridTemplateColumns: '1fr 1fr 1fr',
      }}
    >
      {widgetVisualizationTypes.map((visualizationType) => {
        const isSelected = value === visualizationType.category;
        return (
          <Card
            key={visualizationType.category}
            variant="outlined"
            style={{
              height: 100,
              textAlign: 'center',
              borderColor: isSelected ? `${theme.palette.primary.main}` : undefined,
            }}
          >
            <CardActionArea
              onClick={() => onChange(visualizationType.category)}
              aria-label={t(`custom_dashboard_name_${visualizationType.category}`)}
            >
              <CardContent>
                {renderWidgetIcon(visualizationType.category, 'large')}
                <Typography
                  gutterBottom
                  variant="body1"
                  sx={{ mt: 1 }}
                >
                  {t(`custom_dashboard_name_${visualizationType.category}`)}
                </Typography>
              </CardContent>
            </CardActionArea>
          </Card>
        );
      })}
    </div>
  );
};

export default WidgetTypeSelection;
