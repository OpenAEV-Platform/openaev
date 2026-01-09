import { canHandleExpression, CronParser } from './Cron';
import ISO8601Period from './ISO8601Period';
import { type PeriodExpressionHandler } from './PeriodExpressionHandler';

function handle(expression?: string): PeriodExpressionHandler | null {
  if (!expression) {
    return null;
  }

  if (ISO8601Period.canHandleExpression(expression)) {
    return new ISO8601Period(expression);
  }

  if (canHandleExpression(expression)) {
    return CronParser.parse(expression);
  }

  return null;
}

export default handle;
