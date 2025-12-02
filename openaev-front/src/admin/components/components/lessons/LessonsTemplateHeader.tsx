import { Typography } from '@mui/material';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { getLessonsTemplateSelector } from '../../../../actions/selectors';
import { useSelectorHelper } from '../../../../store';
import LessonsTemplatePopover from './LessonsTemplatePopover';

const useStyles = makeStyles()(() => ({
  containerTitle: {
    display: 'flex',
    alignItems: 'center',
  },
  title: {
    float: 'left',
    textTransform: 'uppercase',
    margin: 0,
  },
}));

const LessonsTemplateHeader = () => {
  // Standard hooks
  const { classes } = useStyles();

  const { lessonsTemplateId } = useParams() as { lessonsTemplateId: string };
  const lessonsTemplate = useSelectorHelper(state => getLessonsTemplateSelector(lessonsTemplateId, state));
  return (
    <>
      <div className={classes.containerTitle}>
        <Typography
          variant="h1"
          classes={{ root: classes.title }}
        >
          {lessonsTemplate?.lessons_template_name}
        </Typography>
        <div>
          {lessonsTemplate && <LessonsTemplatePopover lessonsTemplate={lessonsTemplate} />}
        </div>
      </div>
      <Typography variant="body2">
        {lessonsTemplate?.lessons_template_description}
      </Typography>
    </>
  );
};

export default LessonsTemplateHeader;
