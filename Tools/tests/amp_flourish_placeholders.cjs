// Both production parser paths must emit the same inert native-chart descriptors.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {JSDOM} = require('jsdom');
const root = path.resolve(__dirname, '../..');
const html = fs.readFileSync(path.join(__dirname, 'fixtures/amp/flourish-placeholders.html'), 'utf8');
const expected = ['98701', '98702', '98703', '98704'].map(id => `https://flo.uri.sh/visualisation/${id}/embed`);
function parse(filename) {
 return new Promise((resolve, reject) => {
  const dom = new JSDOM('', {url: 'https://publisher.org/article', runScripts: 'outside-only'});
  const window = dom.window;
  const timer = setTimeout(() => {dom.window.close(); reject(Error('Parser timeout: ' + filename));}, 5000);
  const finish = error => {clearTimeout(timer); dom.window.close(); if(error) reject(error); else resolve();};
  window.TextDecoder = TextDecoder;
  window.fetch = async url => {assert.equal(url, '/index.html'); return {text: async () => html, arrayBuffer: async () => new TextEncoder().encode(html).buffer};};
  window.XMLHttpRequest = class {
   open(method, url) {assert.equal(method, 'GET'); assert.equal(url, '/index.html');}
   send() {this.responseText = html; this.onload();}
  };
  window.Instant = {done(raw) {
   try {
    const article = JSON.parse(raw);
    assert.ok(article && Array.isArray(article.content));
    const frames = [];
    function visit(content) {for(const node of content || []) if(node && typeof node === 'object') {if(node.tag === 'iframe') frames.push(node); visit(node.content);}}
    visit(article.content);
    assert.deepEqual(frames.map(frame => frame.src), expected, filename + ': exactly the declared supported charts, no duplicate nested frame');
    assert.equal(frames[1].title, 'Published chart with numeric suffix');
    assert.ok(JSON.stringify(article.content).includes('END_OF_PLACEHOLDER_ARTICLE'));
    assert.equal(window.REMOTE_SCRIPT_EXECUTED, undefined);
    console.log(filename + ': four public descriptors preserved; eight unsupported declarations rejected; scripts inert');
    finish();
   } catch(error) {finish(error);}
  }};
  try {window.eval(fs.readFileSync(path.join(root, 'TMessagesProj/src/main/res/raw', filename), 'utf8').replaceAll('$DEBUG$', 'false'));} catch(error) {finish(error);}
 });
}
(async () => {await parse('instant.js'); await parse('instant_legacy.js');})().catch(error => {console.error(error); process.exitCode = 1;});
