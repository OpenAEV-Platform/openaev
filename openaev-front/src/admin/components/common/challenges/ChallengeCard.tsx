import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CrisisAlertOutlined, DescriptionOutlined, EmojiEventsOutlined, SportsScoreOutlined } from '@mui/icons-material';
import { Avatar, Card, CardActions, CardContent, CardHeader, Typography } from '@mui/material';
import { type ReactNode } from 'react';
import { makeStyles } from 'tss-react/mui';

import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import type { Challenge, PublicChallenge } from '../../../../utils/api-types';

const useStyles = makeStyles()(theme => ({
  // A challenge is a category, not a severity: the hue comes from the library's
  // categorical ramp. The token's name belongs to a sibling taxonomy and means
  // nothing here — it was picked for perceptual distance from the value it
  // replaces, and unlike that value it resolves per mode.
  avatar: { backgroundColor: theme.palette.designSystem.entities.events },
  cardContainer: {
    display: 'flex',
    flexDirection: 'column',
  },
  iconInfo: {
    display: 'flex',
    alignItems: 'center',
    marginTop: 'auto',
  },
  metric: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(0.5),
    marginRight: theme.spacing(2),
  },
  cardClickable: {
    'cursor': 'pointer',
    '&:hover': { backgroundColor: theme.palette.action.hover },
  },
}));

interface Props {
  challenge: Challenge | PublicChallenge;
  showTags?: boolean;
  clickable?: boolean;
  onClick?: () => void;
  actionHeader?: ReactNode;
  /** Attempts already spent by the player. Left out where no attempt is recorded, as in the admin preview. */
  attempt?: number;
}

const ChallengeCard = ({ challenge, showTags = false, clickable = false, onClick, actionHeader, attempt }: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const onCardClick = () => {
    if (clickable && onClick) {
      onClick();
    }
  };
  // A null or zero maximum means the challenge accepts an unlimited number of attempts
  const maxAttempts = challenge.challenge_max_attempts ? `${challenge.challenge_max_attempts}` : '∞';
  return (
    <Card
      variant="outlined"
      onClick={onCardClick}
      className={`${classes.cardContainer} ${clickable ? classes.cardClickable : ''}`}
    >
      <CardHeader
        avatar={(
          <Avatar className={classes.avatar} aria-label="challenge-icon">
            <EmojiEventsOutlined />
          </Avatar>
        )}
        title={challenge.challenge_name}
        subheader={challenge.challenge_category}
        action={actionHeader}
      />
      <CardContent>
        <ExpandableMarkdown
          source={challenge.challenge_content}
          limit={500}
        />
      </CardContent>
      <CardActions classes={{ root: classes.iconInfo }}>
        {showTags && (challenge.challenge_tags?.length ?? 0) > 0 && <ItemTags variant="list" tags={challenge.challenge_tags} />}

        <Tooltip>
          <TooltipTrigger asChild>
            <span className={classes.metric} style={{ marginLeft: 'auto' }}>
              <SportsScoreOutlined fontSize="small" color="primary" />
              <Typography color="primary" variant="body2">{challenge.challenge_score ?? 0}</Typography>
            </span>
          </TooltipTrigger>
          <TooltipContent>{t('Score')}</TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <span className={classes.metric}>
              <CrisisAlertOutlined fontSize="small" color="primary" />
              <Typography color="primary" variant="body2">
                {attempt === undefined ? maxAttempts : `${attempt}/${maxAttempts}`}
              </Typography>
            </span>
          </TooltipTrigger>
          <TooltipContent>{attempt === undefined ? t('Max number of attempts') : t('Attempts used out of the maximum')}</TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <span className={classes.metric}>
              <DescriptionOutlined fontSize="small" color="primary" />
              <Typography color="primary" variant="body2">{challenge.challenge_documents?.length ?? 0}</Typography>
            </span>
          </TooltipTrigger>
          <TooltipContent>{t('Documents')}</TooltipContent>
        </Tooltip>
      </CardActions>
    </Card>
  );
};

export default ChallengeCard;
