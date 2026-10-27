const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
let html = fs.readFileSync('Tools/tests/fixtures/amp/full-article.html', 'utf8')
  .replace(/(<amp-iframe\b[^>]*>)[\s\S]*?<\/amp-iframe\s*>/gi, '$1</amp-iframe>')
  .replace(/<(\/?)amp-img(?=[\s/>])/gi, '<$1img')
  .replace(/<(\/?)amp-iframe(?=[\s/>])/gi, '<$1iframe');
const dom = new JSDOM('', {url: 'https://example.org/', runScripts: 'outside-only'});
let completed = false;
dom.window.XMLHttpRequest = class {
  open(method, url) { assert.equal(method, 'GET'); assert.equal(url, '/index.html'); }
  send() { this.responseText = html; this.onload(); }
};
dom.window.Instant = {done(raw) {
  const doc = JSON.parse(raw);
  const content = JSON.stringify(doc.content);
  for (const token of ['FINAL_DEL_ARTICULO', 'DATOS_DEL_GRAFICO', 'GRAFICO_INTERACTIVO', 'foto.jpg', 'table', 'visualisation/12345/embed?auto=1']) assert.ok(content.includes(token), token);
  assert.ok(!content.includes('javascript:'));
  assert.equal(dom.window.REMOTE_SCRIPT_EXECUTED, undefined);
  completed = true;
}};
dom.window.eval(fs.readFileSync('TMessagesProj/src/main/res/raw/instant_legacy.js', 'utf8'));
assert.ok(completed);
dom.window.close();
console.log('Legacy reader: article, figure, table and lazy iframe preserved without publisher script execution.');
