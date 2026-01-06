export type LocalHourMinute = {
  hour: number | undefined;
  minute: number | undefined;
};

export abstract class PeriodExpressionHandler {
  rawExpression: string;

  protected constructor(expression: string) {
    this.rawExpression = expression;
  }

  /**
   * @returns the plain text interpretation of the cron expression
   * @param locale the language code in which to output the plain text
   */
  abstract toHumanReadableString(locale: string): string;

  /**
   * @returns an array with string codes to enable internationalisation
   * to be handled by the i18n middleware
   * @example ["every_plural", "2", "month_plural"]
   */
  abstract toTranslatableStringArray(locale: string): string[];

  /**
     * Checks whether the expression is supported by the UI widgets
     * Note this requires arbitrary knowledge of which expression shape is supported
     * @returns true if the expression is supported by the input dialog widgets
     */
  abstract isUiSupported(): boolean;

  /**
     * Checks whether the expression as a whole is valid
     * @returns true if the expression is valid
     */
  abstract isValid(): boolean;

  /**
     * Returns a string representation of the order of magnitude for the
     * expressed interval
     * @example 'monthly', 'weekly', 'daily'
     */
  abstract getRecurrenceMagnitude(): string;

  /**
     * Gets the hour and minute when the recurrence occurs
     * Hour part may be null due to the recurrence being an hourly recurrence
     */
  abstract getRecurrenceTime(): LocalHourMinute;
}
