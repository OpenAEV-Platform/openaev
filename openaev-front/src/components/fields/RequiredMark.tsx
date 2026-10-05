/**
 * The required asterisk of the library Input, for the SelectLabel and
 * ComboboxLabel of our fields. Their own asterisk is 12px against the Input's
 * 14px, so a required select's label row came out 3px shorter (18px against
 * 21px) and its control sat 3px higher than a required text field in the same
 * row. Same classes as the Input's asterisk, plus its own `leading-normal`: the
 * labels set `line-height: 150%`, which children inherit as the computed 18px,
 * not as a ratio, so a 14px asterisk would otherwise keep an 18px line box.
 */
const RequiredMark = () => (
  <span aria-hidden="true" className="font-sans-plex font-normal text-3 leading-normal text-input-required">*</span>
);

export default RequiredMark;
