import { Card, CardActionArea, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { ASSET_FORM_CATEGORIES, type AssetCategory, getCategoryDef } from './asset-categories';
import AssetCategoryIcon from './AssetCategoryIcon';

interface Props { onSelect: (category: AssetCategory) => void }

const AssetCategoryPicker: FunctionComponent<Props> = ({ onSelect }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '1fr 1fr',
      gap: theme.spacing(2),
    }}
    >
      {ASSET_FORM_CATEGORIES.map((category) => {
        const def = getCategoryDef(category);
        return (
          <Card key={category} variant="outlined">
            <CardActionArea
              onClick={() => onSelect(category)}
              style={{
                padding: theme.spacing(2),
                height: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-start',
                gap: theme.spacing(2),
              }}
            >
              <AssetCategoryIcon category={category} color="primary" />
              <div>
                <Typography variant="subtitle1">{t(def.label)}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t(def.description)}
                </Typography>
              </div>
            </CardActionArea>
          </Card>
        );
      })}
    </div>
  );
};

export default AssetCategoryPicker;
