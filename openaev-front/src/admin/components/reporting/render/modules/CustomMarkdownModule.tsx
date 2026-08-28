import { Box } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import MarkdownDisplay from '../../../../../components/MarkdownDisplay';
import { type ReportingModule } from '../../../../../utils/api-types';
import { ModuleEmpty } from './ModuleSection';

/**
 * Free-form markdown block, rendered through the app's shared markdown
 * renderer (GFM tables included). Content comes from module_config.content.
 */

interface Props { module: ReportingModule }

const CustomMarkdownModule: FunctionComponent<Props> = ({ module }) => {
  const { t } = useFormatter();
  const content = typeof module.module_config?.content === 'string' ? module.module_config.content : '';

  if (!content) {
    return <ModuleEmpty message={t('This custom section has no content yet.')} />;
  }

  return (
    <Box sx={{
      'fontSize': 12,
      'lineHeight': 1.7,
      '& table': { width: '100%' },
    }}
    >
      <MarkdownDisplay
        content={content}
        remarkGfmPlugin
        markdownComponents
        removeLinks
      />
    </Box>
  );
};

export default CustomMarkdownModule;
