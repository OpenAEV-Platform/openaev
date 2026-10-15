import rule from '../../../lib/rules/classes-rule.ts';
import RuleTester from '../../rule-tester.ts';

const ruleTester = new RuleTester({ languageOptions: { parserOptions: { ecmaFeatures: { jsx: true } } } });

const tss = (code: string) => `import { makeStyles } from 'tss-react/mui';\n${code}`;

ruleTester.run('classes-rule', rule, {
  valid: [
    tss(`
const useStyles = makeStyles()(theme => ({
  container: { padding: theme.spacing(1) },
  avatar: { width: 24 },
}));
const C = () => {
  const { classes } = useStyles();
  return <div className={classes.container}><span className={classes.avatar} /></div>;
};`),
    // object styles, generic params, `cx` next to `classes`
    tss(`
const useStyles = makeStyles<{ top: boolean }>()({ root: { flexGrow: 1 } });
const C = ({ top }) => {
  const { classes, cx } = useStyles({ top });
  return <div className={cx(classes.root)} />;
};`),
    // classes read by any component calling the hook
    tss(`
const useStyles = makeStyles()(() => ({ a: {}, b: {} }));
const A = () => { const { classes } = useStyles(); return classes.a; };
const B = () => { const { classes } = useStyles(); return classes.b; };`),
    // renamed, nested and string-keyed reads
    tss(`
const useStyles = makeStyles()(() => ({ a: {}, 'b-c': {}, d: {} }));
const A = () => { const { classes: c } = useStyles(); return [c.a, c['b-c']]; };
const B = () => { const { classes: { d } } = useStyles(); return d; };`),
    // hook called in place
    tss(`
const C = () => {
  const { classes } = makeStyles()(() => ({ a: {} }))();
  return classes.a;
};`),
    // a class referenced from another class selector
    tss(`
const useStyles = makeStyles()((theme, _params, classes) => ({
  parent: { [\`&:hover .\${classes.child}\`]: { color: 'red' } },
  child: {},
}));
const C = () => { const { classes } = useStyles(); return classes.parent; };`),
    // exported hooks are read elsewhere and keep their own name
    tss('export const useRowStyles = makeStyles()(() => ({ a: {} }));'),
    tss('const useRowStyles = makeStyles()(() => ({ a: {} }));\nexport default useRowStyles;'),
    // reads that cannot be resolved statically
    tss(`
const useStyles = makeStyles()(() => ({ banner_info: {}, banner_alert: {} }));
const C = ({ level }) => { const { classes } = useStyles(); return classes[\`banner_\${level}\`]; };`),
    tss(`
const useStyles = makeStyles()(() => ({ root: {} }));
const C = () => { const { classes } = useStyles(); return <Child classes={classes} />; };`),
    tss(`
const useStyles = makeStyles()(() => ({ root: {} }));
const C = () => { const styles = useStyles(); return styles.classes.root; };`),
    // a `classes` third parameter that is not a plain identifier
    tss(`
const useStyles = makeStyles()((theme, _params, { b }) => ({ a: { [\`& .\${b}\`]: {} }, b: {} }));
const C = () => { const { classes } = useStyles(); return classes.a; };`),
    // a computed key in the destructuring
    tss(`
const useStyles = makeStyles()(() => ({ a: {}, b: {} }));
const C = () => { const { ['classes']: c } = useStyles(); return c.a; };`),
    // several hooks in one module keep their own names
    tss(`
const useHeaderStyles = makeStyles()(() => ({ a: {} }));
const C = () => { const { classes } = useHeaderStyles(); return classes.a; };
const useBodyStyles = makeStyles()(() => ({ b: {} }));
const D = () => { const { classes } = useBodyStyles(); return classes.b; };`),
    // not the tss-react makeStyles
    `
import { makeStyles } from '@mui/styles';
const useFoo = makeStyles(() => ({ unused: {} }));`,
  ],

  invalid: [
    {
      code: tss(`
const useStyles = makeStyles()(theme => ({
  drawerPaper: {
    minHeight: '100vh',
    padding: theme.spacing(1),
  },
  container: { padding: 0 },
  avatar: { width: 24 },
}));
const C = () => {
  const { classes } = useStyles();
  return [classes.avatar, classes.container];
};`),
      errors: [{
        messageId: 'unusedClass',
        data: { name: 'drawerPaper' },
        suggestions: [{
          messageId: 'removeClass',
          data: { name: 'drawerPaper' },
          output: tss(`
const useStyles = makeStyles()(theme => ({
  container: { padding: 0 },
  avatar: { width: 24 },
}));
const C = () => {
  const { classes } = useStyles();
  return [classes.avatar, classes.container];
};`),
        }],
      }],
    },
    {
      code: tss(`
const useStyles = makeStyles()({ a: {}, b: {}, c: {} });
const C = () => { const { classes } = useStyles(); return classes.b; };`),
      errors: [
        {
          messageId: 'unusedClass',
          data: { name: 'a' },
          suggestions: [{
            messageId: 'removeClass',
            data: { name: 'a' },
            output: tss(`
const useStyles = makeStyles()({ b: {}, c: {} });
const C = () => { const { classes } = useStyles(); return classes.b; };`),
          }],
        },
        {
          messageId: 'unusedClass',
          data: { name: 'c' },
          suggestions: [{
            messageId: 'removeClass',
            data: { name: 'c' },
            output: tss(`
const useStyles = makeStyles()({ a: {}, b: {} });
const C = () => { const { classes } = useStyles(); return classes.b; };`),
          }],
        },
      ],
    },
    {
      // the hook is called but `classes` is never taken
      code: tss(`
const useStyles = makeStyles()(() => ({
  a: {},
}));
const C = () => { const { cx } = useStyles(); return cx('x'); };`),
      errors: [{
        messageId: 'unusedClass',
        data: { name: 'a' },
        suggestions: [{
          messageId: 'removeClass',
          data: { name: 'a' },
          output: tss(`
const useStyles = makeStyles()(() => ({
}));
const C = () => { const { cx } = useStyles(); return cx('x'); };`),
        }],
      }],
    },
    {
      code: tss(`
const C = () => {
  const { classes } = makeStyles()(() => ({ a: {}, b: {} }))();
  return classes.a;
};`),
      errors: [{
        messageId: 'unusedClass',
        data: { name: 'b' },
        suggestions: [{
          messageId: 'removeClass',
          data: { name: 'b' },
          output: tss(`
const C = () => {
  const { classes } = makeStyles()(() => ({ a: {} }))();
  return classes.a;
};`),
        }],
      }],
    },
    {
      code: tss(`
const useFooStyles = makeStyles()(() => ({ a: {} }));
const A = () => { const { classes } = useFooStyles(); return classes.a; };
const B = () => { const { classes } = useFooStyles(); return classes.a; };`),
      errors: [{
        messageId: 'name',
        data: { expected: 'useStyles' },
      }],
      output: tss(`
const useStyles = makeStyles()(() => ({ a: {} }));
const A = () => { const { classes } = useStyles(); return classes.a; };
const B = () => { const { classes } = useStyles(); return classes.a; };`),
    },
    {
      // renaming would clash with an existing useStyles
      code: tss(`
const useStyles = () => null;
const useFooStyles = makeStyles()(() => ({ a: {} }));
const C = () => [useStyles(), useFooStyles().classes];`),
      errors: [{ messageId: 'name' }],
    },
    {
      code: tss(`
const useStyles = makeStyles()(() => ({}));
const C = () => { const { classes } = useStyles(); return classes; };`),
      errors: [{ messageId: 'empty' }],
    },
    {
      // aliased import
      code: `
import { makeStyles as tssMakeStyles } from 'tss-react';
const useStyles = tssMakeStyles()({ a: {}, b: {} });
const C = () => { const { classes } = useStyles(); return classes.a; };`,
      errors: [{
        messageId: 'unusedClass',
        data: { name: 'b' },
        suggestions: [{
          messageId: 'removeClass',
          data: { name: 'b' },
          output: `
import { makeStyles as tssMakeStyles } from 'tss-react';
const useStyles = tssMakeStyles()({ a: {} });
const C = () => { const { classes } = useStyles(); return classes.a; };`,
        }],
      }],
    },
  ],
});
