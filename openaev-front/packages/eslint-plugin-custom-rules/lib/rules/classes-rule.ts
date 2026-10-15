// tss-react `makeStyles()(styles)` returns a hook whose `classes` object maps each key of
// `styles` to a generated class name. A key that no `classes.<key>` reads is dead CSS, and
// the hook of a module that keeps a single one is named `useStyles` so every component reads
// the same way. Removing an unused class is only a suggestion: as an autofix, a fix on save
// would delete a class that is being written.

import { AST_NODE_TYPES, ASTUtils, ESLintUtils, type TSESLint, type TSESTree } from '@typescript-eslint/utils';

type SourceCode = Readonly<TSESLint.SourceCode>;
type Variable = TSESLint.Scope.Variable;

const TSS_REACT_SOURCE = /^tss-react(\/|$)/;
const HOOK_NAME = 'useStyles';

const isTssMakeStyles = (sourceCode: SourceCode, node: TSESTree.Node) => {
  if (node.type !== AST_NODE_TYPES.Identifier) {
    return false;
  }
  const def = ASTUtils.findVariable(sourceCode.getScope(node), node)?.defs[0];
  if (def?.type !== 'ImportBinding' || def.node.type !== AST_NODE_TYPES.ImportSpecifier || def.parent.type !== AST_NODE_TYPES.ImportDeclaration) {
    return false;
  }
  const { imported } = def.node;
  return (imported.type === AST_NODE_TYPES.Identifier ? imported.name : imported.value) === 'makeStyles'
    && TSS_REACT_SOURCE.test(def.parent.source.value);
};

// The object literal of class definitions: `{...}`, `() => ({...})`, or a function whose
// last statement returns one. Anything else is built dynamically and is not checked.
const getStylesObject = (node: TSESTree.Node | undefined) => {
  if (node?.type === AST_NODE_TYPES.ObjectExpression) {
    return node;
  }
  if (node?.type !== AST_NODE_TYPES.ArrowFunctionExpression && node?.type !== AST_NODE_TYPES.FunctionExpression) {
    return null;
  }
  if (node.body.type === AST_NODE_TYPES.ObjectExpression) {
    return node.body;
  }
  const last = node.body.type === AST_NODE_TYPES.BlockStatement ? node.body.body.at(-1) : undefined;
  return last?.type === AST_NODE_TYPES.ReturnStatement && last.argument?.type === AST_NODE_TYPES.ObjectExpression
    ? last.argument
    : null;
};

const getKeyName = (node: TSESTree.Node) => {
  if (node.type !== AST_NODE_TYPES.Property || node.computed) {
    return null;
  }
  if (node.key.type === AST_NODE_TYPES.Identifier) {
    return node.key.name;
  }
  return node.key.type === AST_NODE_TYPES.Literal && typeof node.key.value === 'string' ? node.key.value : null;
};

const getDeclaredVariable = (sourceCode: SourceCode, node: TSESTree.Node, name: string) => sourceCode.getDeclaredVariables(node).find(variable => variable.name === name);

const isExportReference = ({ identifier }: TSESLint.Scope.Reference) => identifier.parent.type === AST_NODE_TYPES.ExportSpecifier
  || identifier.parent.type === AST_NODE_TYPES.ExportDefaultDeclaration;

// Adds the class names read through `classes` to `used`. Returns null as soon as one read
// cannot be resolved statically (`classes[key]`, `classes` passed along...), since any class
// could then be used.
const collectClassReads = (variable: Variable | undefined, used: Set<string>) => {
  if (!variable) {
    return null;
  }
  for (const { identifier, init } of variable.references) {
    if (init) {
      // the declaration itself
      continue;
    }
    const { parent } = identifier;
    if (parent.type !== AST_NODE_TYPES.MemberExpression || parent.object !== identifier) {
      return null;
    }
    if (!parent.computed) {
      used.add(parent.property.name);
    } else if (parent.property.type === AST_NODE_TYPES.Literal && typeof parent.property.value === 'string') {
      used.add(parent.property.value);
    } else {
      return null;
    }
  }
  return used;
};

// `const { classes } = useStyles()`, `const { classes: c } = ...` or `const { classes: { a } } = ...`
const collectHookCallReads = (sourceCode: SourceCode, call: TSESTree.CallExpression, used: Set<string>) => {
  const { parent } = call;
  if (parent.type === AST_NODE_TYPES.ExpressionStatement) {
    return used;
  }
  if (parent.type !== AST_NODE_TYPES.VariableDeclarator || parent.id.type !== AST_NODE_TYPES.ObjectPattern) {
    return null;
  }
  if (parent.id.properties.some(property => property.type === AST_NODE_TYPES.RestElement || property.computed)) {
    return null;
  }
  const property = parent.id.properties.find(p => getKeyName(p) === 'classes');
  if (property?.type !== AST_NODE_TYPES.Property) {
    return used;
  }
  const value = property.value.type === AST_NODE_TYPES.AssignmentPattern ? property.value.left : property.value;
  if (value.type === AST_NODE_TYPES.Identifier) {
    return collectClassReads(getDeclaredVariable(sourceCode, parent, value.name), used);
  }
  if (value.type !== AST_NODE_TYPES.ObjectPattern) {
    return null;
  }
  for (const p of value.properties) {
    const name = getKeyName(p);
    if (name === null) {
      return null;
    }
    used.add(name);
  }
  return used;
};

interface Hook {
  // module-level `const <id> = makeStyles()(...)`, when not exported
  id?: TSESTree.Identifier;
  variable?: Variable;
  // every call of the hook, or null when one of them cannot be followed
  calls: TSESTree.CallExpression[] | null;
}

// Where the hook created by `node` goes: called in place, or kept in a variable
const resolveHook = (sourceCode: SourceCode, node: TSESTree.CallExpression): Hook => {
  const { parent } = node;
  // `makeStyles()(styles)()`
  if (parent.type === AST_NODE_TYPES.CallExpression && parent.callee === node) {
    return { calls: [parent] };
  }
  if (parent.type !== AST_NODE_TYPES.VariableDeclarator || parent.init !== node || parent.id.type !== AST_NODE_TYPES.Identifier) {
    return { calls: null };
  }
  const { id } = parent;
  const variable = getDeclaredVariable(sourceCode, parent, id.name);
  const references = variable?.references.filter(reference => reference.identifier !== id) ?? [];
  if (parent.parent.parent?.type === AST_NODE_TYPES.ExportNamedDeclaration || references.some(isExportReference)) {
    // read by other modules, which this rule cannot see
    return { calls: null };
  }
  const calls: TSESTree.CallExpression[] = [];
  for (const { identifier } of references) {
    if (identifier.parent.type !== AST_NODE_TYPES.CallExpression || identifier.parent.callee !== identifier) {
      return {
        id,
        variable,
        calls: null,
      };
    }
    calls.push(identifier.parent);
  }
  return {
    id,
    variable,
    calls,
  };
};

export default ESLintUtils.RuleCreator.withoutDocs({
  meta: {
    type: 'suggestion',
    fixable: 'code',
    hasSuggestions: true,
    docs: { description: 'Require tss-react makeStyles hooks to be named useStyles and to define only the classes they use' },
    messages: {
      name: 'A makeStyles hook used only in its module must be named \'{{expected}}\'.',
      empty: 'makeStyles defines no class.',
      unusedClass: 'Class \'{{name}}\' is never read from \'classes\'.',
      removeClass: 'Remove class \'{{name}}\'.',
    },
    schema: [],
  },
  defaultOptions: [],
  create: (context) => {
    const { sourceCode } = context;

    // Drops a property with its comma, and its whole line when it sits alone on it.
    const removeProperty = (fixer: TSESLint.RuleFixer, property: TSESTree.Node) => {
      const { text } = sourceCode;
      const before = sourceCode.getTokenBefore(property);
      const after = sourceCode.getTokenAfter(property);
      let [start, end] = property.range;
      const trailingComma = after?.value === ',';
      if (after && trailingComma) {
        end = after.range[1];
      } else if (before?.value === ',') {
        start = before.range[0];
      }
      const lineStart = text.lastIndexOf('\n', start - 1) + 1;
      const lineEnd = text.indexOf('\n', end);
      if (lineEnd !== -1 && !text.slice(lineStart, start).trim() && !text.slice(end, lineEnd).trim()) {
        return fixer.removeRange([lineStart, lineEnd + 1]);
      }
      // `{ a: {}, b: {} }` keeps one space before `b` once `a: {},` is gone
      while (trailingComma && (text[end] === ' ' || text[end] === '\t')) {
        end += 1;
      }
      return fixer.removeRange([start, end]);
    };

    const checkHookName = (id: TSESTree.Identifier, variable: Variable) => {
      if (id.name === HOOK_NAME) {
        return;
      }
      const identifiers = variable.references.map(reference => reference.identifier);
      const free = identifiers.every(identifier => !ASTUtils.findVariable(sourceCode.getScope(identifier), HOOK_NAME));
      context.report({
        node: id,
        messageId: 'name',
        data: { expected: HOOK_NAME },
        fix: free ? fixer => identifiers.map(identifier => fixer.replaceText(identifier, HOOK_NAME)) : null,
      });
    };

    const checkClasses = (node: TSESTree.CallExpression, stylesObject: TSESTree.ObjectExpression, calls: TSESTree.CallExpression[]) => {
      let used: Set<string> | null = new Set();
      for (const call of calls) {
        used = collectHookCallReads(sourceCode, call, used);
        if (!used) {
          return;
        }
      }
      // `(theme, params, classes) => ...` lets a class reference another one in a selector
      const [styles] = node.arguments;
      const classesParam = 'params' in styles ? styles.params[2] : undefined;
      if (classesParam && (classesParam.type !== AST_NODE_TYPES.Identifier || !collectClassReads(getDeclaredVariable(sourceCode, styles, classesParam.name), used))) {
        return;
      }
      stylesObject.properties.forEach((property) => {
        const name = getKeyName(property);
        if (name !== null && !used.has(name)) {
          context.report({
            node: property,
            messageId: 'unusedClass',
            data: { name },
            suggest: [{
              messageId: 'removeClass',
              data: { name },
              fix: fixer => removeProperty(fixer, property),
            }],
          });
        }
      });
    };

    // Hooks kept in a module-level variable, named once all of them are known
    const localHooks: Hook[] = [];

    return {
      CallExpression(node) {
        // tss-react is curried: `makeStyles(options?)(styles)`
        if (node.callee.type !== AST_NODE_TYPES.CallExpression || !isTssMakeStyles(sourceCode, node.callee.callee)) {
          return;
        }
        const stylesObject = getStylesObject(node.arguments[0]);
        if (stylesObject?.properties.length === 0) {
          context.report({
            node: stylesObject,
            messageId: 'empty',
          });
        }
        const hook = resolveHook(sourceCode, node);
        if (hook.id) {
          localHooks.push(hook);
        }
        // a hook never called is left to no-unused-vars
        if (stylesObject && hook.calls?.length) {
          checkClasses(node, stylesObject, hook.calls);
        }
      },
      'Program:exit'() {
        // with several hooks, a single `useStyles` would not tell them apart
        const [hook] = localHooks;
        if (localHooks.length === 1 && hook.id && hook.variable) {
          checkHookName(hook.id, hook.variable);
        }
      },
    };
  },
});
