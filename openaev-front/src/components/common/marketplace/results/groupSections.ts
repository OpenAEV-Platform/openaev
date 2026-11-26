export interface Section<T> {
  key: string | undefined;
  items: T[];
}

const groupSections = <T>(items: T[], sectionKey: (item: T) => string | undefined): Section<T>[] => {
  const sections: Section<T>[] = [];
  items.forEach((item) => {
    const key = sectionKey(item) || undefined;
    const last = sections[sections.length - 1];
    if (last && last.key === key) {
      last.items.push(item);
    } else {
      sections.push({
        key,
        items: [item],
      });
    }
  });
  return sections;
};

export default groupSections;

// Items of the same section side by side, without a section last; the order inside a section is kept.
export const bySection = <T>(sectionKey: (item: T) => string | undefined) => (a: T, b: T) => {
  const first = sectionKey(a);
  const second = sectionKey(b);
  if (first === second) return 0;
  if (!first) return 1;
  if (!second) return -1;
  return first.localeCompare(second);
};
