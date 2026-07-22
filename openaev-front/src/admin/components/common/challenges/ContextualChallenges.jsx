import {
  SlowMotionVideoOutlined,
  VisibilityOutlined,
} from '@mui/icons-material';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';
import { Link } from 'react-router';

import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import useSearchAndFilter from '../../../../utils/SortingFiltering';
import ConfigurationSection from '../ConfigurationSection';
import { ChallengeContext, PermissionsContext } from '../Context';
import ChallengeCard from './ChallengeCard';

const ContextualChallenges = ({ challenges, linkToInjects }) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  // Context
  const { previewChallengeUrl } = useContext(ChallengeContext);
  const { permissions } = useContext(PermissionsContext);

  // Filter and sort hook
  const searchColumns = ['name', 'category', 'content'];
  const filtering = useSearchAndFilter('challenge', 'name', searchColumns);
  // Rendering
  const sortedChallenges = filtering.filterAndSort(challenges);
  return (
    <ConfigurationSection
      title={t('Challenges')}
      count={challenges.length}
      action={(
        <Button
          variant="outlined"
          color="primary"
          size="small"
          startIcon={<VisibilityOutlined />}
          component={Link}
          to={previewChallengeUrl()}
          target="_blank"
        >
          {t('Preview')}
        </Button>
      )}
    >
      {sortedChallenges.length === 0 && (
        <Empty message={(
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 18 }}>
              {t('No challenge are used in the injects of this simulation.')}
            </div>
            {linkToInjects && permissions.canManage && (
              <Button
                style={{ marginTop: 20 }}
                startIcon={<SlowMotionVideoOutlined />}
                variant="outlined"
                color="primary"
                size="small"
                component={Link}
                to={linkToInjects}
              >
                {t('Create an inject')}
              </Button>
            )}
          </div>
        )}
        />
      )}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr 1fr',
        gap: theme.spacing(3),
      }}
      >
        {sortedChallenges.map(challenge => <ChallengeCard showTags key={challenge.challenge_id} challenge={challenge} />)}
      </div>
    </ConfigurationSection>
  );
};

export default ContextualChallenges;
