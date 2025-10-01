import { CrisisAlertOutlined, DescriptionOutlined, EmojiEventsOutlined, SportsScoreOutlined } from '@mui/icons-material';
import {
  Avatar,
  Card,
  CardActions,
  CardContent,
  CardHeader,
  Typography,
} from '@mui/material';
import { type ReactNode } from 'react';
import { makeStyles } from 'tss-react/mui';

import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import ItemTags from '../../../../components/ItemTags';
import type { Challenge } from '../../../../utils/api-types';

const useStyles = makeStyles()(theme => ({
  cardContainer: {
    display: 'flex',
    flexDirection: 'column',
  },
  iconInfo: {
    display: 'flex',
    alignItems: 'center',
    marginTop: 'auto',
  },
  marginRight2: { marginRight: theme.spacing(2) },
  cardClickable: {
    'cursor': 'pointer',
    '&:hover': { backgroundColor: theme.palette.action.hover },
  },
}));

interface Props {
  challenge: Challenge;
  showTags?: boolean;
  clickable?: boolean;
  onClick?: () => void;
  actionHeader?: ReactNode;
}

const ChallengeCard = ({ challenge, showTags = false, clickable = false, onClick, actionHeader }: Props) => {
  const { classes } = useStyles();
  const onCardClick = () => {
    if (clickable && onClick) {
      onClick();
    }
  };
  return (
    <Card
      variant="outlined"
      onClick={onCardClick}
      className={`${classes.cardContainer} ${clickable ? classes.cardClickable : ''}`}
    >
      <CardHeader
        avatar={(
          <Avatar sx={{ backgroundColor: '#e91e63' }} aria-label="challenge-icon">
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

        <SportsScoreOutlined style={{ marginLeft: 'auto' }} fontSize="small" color="primary" />
        <Typography classes={{ root: classes.marginRight2 }} color="primary" variant="body2">{challenge.challenge_score ?? 0}</Typography>
        <CrisisAlertOutlined fontSize="small" color="primary" />
        <Typography classes={{ root: classes.marginRight2 }} color="primary" variant="body2">{challenge.challenge_max_attempts ?? 0}</Typography>
        <DescriptionOutlined fontSize="small" color="primary" />
        <Typography classes={{ root: classes.marginRight2 }} color="primary" variant="body2">{challenge.challenge_documents?.length ?? 0}</Typography>
      </CardActions>
    </Card>
  );
};

export default ChallengeCard;
