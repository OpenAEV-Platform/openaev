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
