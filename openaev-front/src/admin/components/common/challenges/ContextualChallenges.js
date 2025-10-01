import {
  SlowMotionVideoOutlined,
  VisibilityOutlined,
} from '@mui/icons-material';
import { Button, IconButton, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import useSearchAnFilter from '../../../../utils/SortingFiltering';
import { ChallengeContext, PermissionsContext } from '../Context';
import ChallengeCard from './ChallengeCard.js';

const useStyles = makeStyles()(() => ({
  flag: {
    fontSize: 12,
    float: 'left',
    marginRight: 7,
    maxWidth: 300,
    borderRadius: 4,
  },
  card: { position: 'relative' },
  footer: {
    width: '100%',
    position: 'absolute',
    padding: '0 15px 0 15px',
    left: 0,
    bottom: 10,
  },
  button: { cursor: 'default' },
  createButton: {
    float: 'left',
    marginTop: -15,
  },
}));

const ContextualChallenges = ({ challenges, linkToInjects }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();

  // Context
  const { previewChallengeUrl } = useContext(ChallengeContext);
  const { permissions } = useContext(PermissionsContext);

  // Filter and sort hook
  const searchColumns = ['name', 'category', 'content'];
  const filtering = useSearchAnFilter('challenge', 'name', searchColumns);
  // Rendering
  const sortedChallenges = filtering.filterAndSort(challenges);
  return (
    <>
      <div style={{ float: 'left' }}>
        <Tooltip title={t('Preview challenges page')}>
          <IconButton
            color="primary"
            aria-label="Add"
            component={Link}
            to={previewChallengeUrl()}
            target="_blank"
            classes={{ root: classes.createButton }}
            size="large"
          >
            <VisibilityOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
      </div>
      <div className="clearfix" />
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
    </>
  );
};

export default ContextualChallenges;
