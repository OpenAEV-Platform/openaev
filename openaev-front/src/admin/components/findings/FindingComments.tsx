import { Avatar, Box, Button, List, ListItem, ListItemAvatar, ListItemText, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';

import { addFindingComment, fetchFindingComments } from '../../../actions/findings/finding-comment-actions';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { type FindingCommentOutput } from './FindingCommentTypes';

interface Props { findingId: string }

/**
 * Step 2 of the Findings "Comments" feature: adds comment creation on top of the Step 1
 * read-only list. Still no edit/delete UI - that is added in a later step.
 */
const FindingComments = ({ findingId }: Props) => {
  const { t, nsdt } = useFormatter();

  const [comments, setComments] = useState<FindingCommentOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [newComment, setNewComment] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);

  useEffect(() => {
    setLoading(true);
    fetchFindingComments(findingId)
      .then((result: { data: FindingCommentOutput[] }) => setComments(result.data))
      .finally(() => setLoading(false));
  }, [findingId]);

  const handlePostComment = () => {
    const content = newComment.trim();
    if (!content || submitting) return;
    setSubmitting(true);
    addFindingComment(findingId, content)
      .then((result: { data: FindingCommentOutput }) => {
        // Optimistic prepend: the endpoint already returns the persisted comment, so no
        // refetch is needed - it just goes straight to the top (most recent first).
        setComments(previous => [result.data, ...previous]);
        setNewComment('');
      })
      // Errors are already surfaced via the global snackbar by simplePostCall's default
      // error behavior (see utils/Action.ts) - nothing else to show inline here.
      .finally(() => setSubmitting(false));
  };

  const renderCommentList = () => {
    if (loading) {
      return <Loader variant="inElement" />;
    }
    if (comments.length === 0) {
      return <Empty message={t('No comments yet')} />;
    }
    return (
      <List disablePadding>
        {comments.map((comment) => {
          const authorName = comment.finding_comment_author_firstname && comment.finding_comment_author_lastname
            ? `${comment.finding_comment_author_firstname} ${comment.finding_comment_author_lastname}`
            : comment.finding_comment_author_id;
          return (
            <ListItem
              key={comment.finding_comment_id}
              alignItems="flex-start"
              divider
            >
              <ListItemAvatar>
                <Avatar>{authorName.charAt(0).toUpperCase()}</Avatar>
              </ListItemAvatar>
              <ListItemText
                primary={(
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'baseline',
                    gap: 1,
                  }}
                  >
                    <Typography sx={{ fontWeight: 600 }}>{authorName}</Typography>
                    <Typography
                      variant="body2"
                      color="text.secondary"
                    >
                      {nsdt(comment.finding_comment_created_at)}
                    </Typography>
                  </Box>
                )}
                secondary={(
                  <Typography
                    variant="body2"
                    sx={{ whiteSpace: 'pre-wrap' }}
                    component="span"
                  >
                    {comment.finding_comment_content}
                  </Typography>
                )}
              />
            </ListItem>
          );
        })}
      </List>
    );
  };

  return (
    <>
      {renderCommentList()}
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        marginTop: 2,
      }}
      >
        <TextField
          value={newComment}
          onChange={event => setNewComment(event.target.value)}
          placeholder={t('Write a comment')}
          multiline
          minRows={2}
          fullWidth
          disabled={submitting}
          // TODO: add a soft character-counter at 4000 (mirrors the backend
          // FindingCommentInput/FindingComment CHECK constraint) once this form stabilizes.
        />
        <Box sx={{
          display: 'flex',
          justifyContent: 'flex-end',
        }}
        >
          <Button
            variant="contained"
            onClick={handlePostComment}
            disabled={submitting || newComment.trim().length === 0}
          >
            {t('Post')}
          </Button>
        </Box>
      </Box>
    </>
  );
};

export default FindingComments;
