/**
 * Step 5b — computed-style diff of the product Header against the design
 * system's own documentation site, which is the known-correct host (it imports
 * full Tailwind, so it has the preflight this product does not).
 *
 * Re-run this at every pin bump; it turns a bump from an afternoon into
 * twenty minutes.
 *
 *   # 1. the docs site, at the SHA pinned in openaev-front/package.json
 *   git clone https://github.com/XTM-Foundation/filigran-design-system.git /tmp/fdsdoc
 *   cd /tmp/fdsdoc && git checkout <PIN_SHA> && corepack enable
 *   pnpm install --frozen-lockfile        # the root `prepare` also builds the package
 *   cd docs && npx next dev --port 3066   # NOT `pnpm --filter ./docs dev -- --port`
 *
 *   # 2. the product, logged in, on port 3055
 *   # 3. from openaev-front/:
 *   node ../fds-migration/scripts/compare-header-vs-docs.cjs
 *
 * One measurement function is applied to both pages, so a difference can never
 * come from measuring two different things.
 *
 * Known, named differences — these are expected, not defects:
 *   position    relative / fixed   the Header ships no positioning by design;
 *                                  the product fixes it to the viewport top.
 *   z-index     auto / 1100        set by the product, above the content.
 *   min-height  auto / 0px         the product's MUI CssBaseline reset.
 *   font-size   16px / 14.4px      the product's MUI theme sets a 90% base,
 *                                  product-wide and pre-existing.
 *   font-family fallback name      next/font local fallback, docs-site only.
 * Everything else must be identical: height 68px, padding 16px, 1px bottom
 * border, ::before opacity .94, backdrop-filter blur(4px).
 */
const { chromium } = require('@playwright/test');
const MEASURE = ({sel,idx}) => {
  const h = document.querySelectorAll(sel)[idx];
  if(!h) return {ERROR:'not found: '+sel+'['+idx+']'};
  const cs = getComputedStyle(h), be = getComputedStyle(h,'::before');
  const pick = o => ({height:o.height, minHeight:o.minHeight, position:o.position, display:o.display,
    alignItems:o.alignItems, justifyContent:o.justifyContent, gap:o.gap, padding:o.padding,
    borderBottomWidth:o.borderBottomWidth, borderBottomColor:o.borderBottomColor,
    fontFamily:o.fontFamily.slice(0,40), fontSize:o.fontSize, color:o.color,
    backgroundColor:o.backgroundColor, backdropFilter:o.backdropFilter, zIndex:o.zIndex, overflow:o.overflow});
  return {
    cls: h.className,
    root: pick(cs),
    before: {content:be.content, opacity:be.opacity, backgroundImage:be.backgroundImage,
             position:be.position, inset:be.inset, backdropFilter:be.backdropFilter, zIndex:be.zIndex},
    headerHeightVar: cs.getPropertyValue('--fds-header-height').trim(),
    groups: [...h.children].map(c=>{const g=getComputedStyle(c);
      return {tag:c.tagName, cls:typeof c.className==='string'?c.className:'', display:g.display,
        alignItems:g.alignItems, gap:g.gap, flexGrow:g.flexGrow, flexShrink:g.flexShrink,
        flexBasis:g.flexBasis, minWidth:g.minWidth, padding:g.padding};}),
  };
};
const diff=(a,b,path='')=>{const out=[];
  if(typeof a!=='object'||a===null||typeof b!=='object'||b===null){ if(String(a)!==String(b)) out.push([path,a,b]); return out;}
  for(const k of new Set([...Object.keys(a),...Object.keys(b)])) out.push(...diff(a[k],b[k],path?path+'.'+k:k));
  return out;};
(async()=>{const br=await chromium.launch();
const dp=await br.newPage({viewport:{width:1600,height:900}});
await dp.goto('http://localhost:3066/components/header/',{waitUntil:'networkidle',timeout:90000});
await dp.waitForTimeout(2500);
const info = await dp.evaluate(()=>[...document.querySelectorAll('header')].map((h,i)=>({i,cls:String(h.className).slice(0,120)})));
console.log('docs headers:',JSON.stringify(info,null,1));

const pp=await br.newPage({viewport:{width:1600,height:900}});
await pp.goto('http://localhost:3055',{waitUntil:'domcontentloaded'});
await pp.waitForSelector('input[name="username"]',{timeout:30000});
await pp.fill('input[name="username"]','admin@openaev.io');await pp.fill('input[name="password"]','admin');
await pp.keyboard.press('Enter');await pp.waitForSelector('header',{timeout:40000});await pp.waitForTimeout(3000);

const prod = await pp.evaluate(MEASURE,{sel:'header',idx:0});
console.log('\n=== PRODUCT ===\n'+JSON.stringify(prod,null,1));
for (const i of info.map(x=>x.i)) {
  const docs = await dp.evaluate(MEASURE,{sel:'header',idx:i});
  console.log('\n=== DOCS header['+i+'] cls='+String(docs.cls).slice(0,100));
  const d = diff(docs.root,prod.root,'root').concat(diff(docs.before,prod.before,'before'));
  console.log(d.length?d.map(([p,a,b])=>'  DIFF '+p+'\n     docs: '+a+'\n     prod: '+b).join('\n'):'  identical');
}
await br.close();})();
