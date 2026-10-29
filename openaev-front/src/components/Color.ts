// The status vocabulary, on the library's feedback and entity tokens. Only the
// KEYS are consumed today (one type-only import), but the values stay in step
// so the vocabulary cannot drift back to off-palette hues.
const colorStyles = {
  green: {
    backgroundColor: 'var(--color-feedback-success-secondary-transparency-30)',
    color: 'var(--color-feedback-success-primary)',
  },
  blue: {
    backgroundColor: 'var(--color-feedback-info-secondary-transparency-30)',
    color: 'var(--color-feedback-info-primary)',
  },
  red: {
    backgroundColor: 'var(--color-feedback-error-secondary-transparency-30)',
    color: 'var(--color-feedback-error-primary)',
  },
  orange: {
    backgroundColor: 'var(--color-feedback-warning-secondary-transparency-30)',
    color: 'var(--color-feedback-warning-primary)',
  },
  yellow: {
    backgroundColor: 'var(--color-feedback-alert-secondary-transparency-30)',
    color: 'var(--color-feedback-alert-primary)',
  },
  purple: {
    backgroundColor: 'var(--color-entities-victimology-transparency-20)',
    color: 'var(--color-entities-victimology)',
  },
  lightPurple: {
    backgroundColor: 'var(--color-entities-cases-transparency-20)',
    color: 'var(--color-entities-cases)',
  },
  blueGrey: {
    backgroundColor: 'var(--color-feedback-neutral-secondary-transparency-30)',
    color: 'var(--color-feedback-neutral-primary)',
    fontStyle: 'italic' as const,
  },
  grey: {
    backgroundColor: 'var(--color-feedback-neutral-secondary-transparency-30)',
    color: 'var(--color-feedback-neutral-primary)',
  },
  canceled: {
    backgroundColor: 'var(--color-feedback-neutral-secondary-transparency-30)',
    color: 'var(--color-feedback-neutral-primary)',
  },
};

export default colorStyles;
