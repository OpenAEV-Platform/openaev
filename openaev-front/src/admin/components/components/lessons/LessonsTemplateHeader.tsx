import { Typography } from '@mui/material';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type UserHelper } from '../../../../actions/helper';
import { type LessonsTemplatesHelper } from '../../../../actions/lessons/lesson-helper';
import { useHelper } from '../../../../store';
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
  const { lessonsTemplate } = useHelper((helper: LessonsTemplatesHelper & UserHelper) => ({ lessonsTemplate: helper.getLessonsTemplate(lessonsTemplateId) }));
  return (
    <>
      <div className={classes.containerTitle}>
        <Typography
          variant="h1"
          classes={{ root: classes.title }}
        >
          {lessonsTemplate.lessons_template_name}
        </Typography>
        <div>
          <LessonsTemplatePopover lessonsTemplate={lessonsTemplate} />
        </div>
      </div>
      <Typography variant="body2">
        {lessonsTemplate.lessons_template_description}
      </Typography>
    </>
  );
};

export default LessonsTemplateHeader;
