// Runs the same bundled Readability + DOMPurify program against an inert AMP fixture.
// Usage: NODE_PATH=<directory containing jsdom> node Tools/tests/amp_reader_fixture.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');
const root = path.resolve(__dirname, '../..');
let html = fs.readFileSync(path.join(__dirname, 'fixtures/amp/full-article.html'), 'utf8');
// Match the amp-img normalization performed before the existing parser is invoked.
html = html.replace(/<(\/?)amp-img(?=[\s/>])/gi, '<$1img');
const dom = new JSDOM('', { url: 'https://example.org/article', runScripts: 'outside-only' });
const window = dom.window;
window.fetch = async url => {
 assert.equal(url, '/index.html', 'Parser must not fetch external resources');
 return { text: async () => html, arrayBuffer: async () => new TextEncoder().encode(html).buffer };
};
window.TextDecoder = TextDecoder;
const deadline = setTimeout(() => { dom.window.close(); throw Error('Parser timed out'); }, 5000);
window.Instant = { done(raw) {
 try {
  const article = JSON.parse(raw);
  assert.ok(article && Array.isArray(article.content));
  const serialized = JSON.stringify(article.content);
  assert.ok(serialized.includes('FINAL_DEL_ARTICULO'));
  assert.ok(serialized.includes('segundo párrafo'));
  assert.ok(serialized.includes('foto.jpg'));
  assert.ok(!serialized.includes('javascript:'));
  assert.equal(window.REMOTE_SCRIPT_EXECUTED, undefined);
  assert.ok(serialized.length > article.excerpt.length * 4);
  // Optional isolated test output, consumed by the local snapshot JVM test.
  if (process.argv[2]) fs.writeFileSync(process.argv[2], raw, 'utf8');
  console.log('AMP full article extracted; paragraphs/image preserved; remote scripts not executed');
 } finally { clearTimeout(deadline); dom.window.close(); }
}};
window.eval(fs.readFileSync(path.join(root, 'TMessagesProj/src/main/res/raw/instant.js'),'utf8').replaceAll('$DEBUG$', 'false'));
