import { type FunctionComponent } from 'react';

import CodeDiff from './CodeDiff';

interface Props {
  /** Name of the field, above the lines. */
  label: string;
  before: string;
  after: string;
  /** e.g. "Before · v1.0.0" */
  beforeLabel: string;
  /** e.g. "After · v1.1.0" */
  afterLabel: string;
  /** A code field (command, arguments…) keeps one line per row; other text wraps. */
  code?: boolean;
  /** Show the Before / After key on the diff (default); off when a key is shown once above. */
  legend?: boolean;
}

/**
 * One changed field, read line by line with − and +, like GitHub ({@link CodeDiff}): the same for a
 * command and for a one-line text such as a description, in the Versions tab, the History tab and the
 * update and rollback dialogs.
 */
const BeforeAfter: FunctionComponent<Props> = ({ label, before, after, beforeLabel, afterLabel, code = false, legend = true }) => (
  <CodeDiff label={label} before={before} after={after} beforeLabel={beforeLabel} afterLabel={afterLabel} legend={legend} wrap={!code} />
);

export default BeforeAfter;
