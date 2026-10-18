'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const dir=path.resolve(__dirname,'..');
const html=fs.readFileSync(path.join(dir,'index.html'),'utf8');
const app=fs.readFileSync(path.join(dir,'app.js'),'utf8');
test('every UI ID used by the browser app exists exactly once in HTML',()=>{
  const ids=[...new Set([...app.matchAll(/\$\('([\w-]+)'\)/g)].map(x=>x[1]))];
  assert.ok(ids.length>=20,'smoke test should inspect substantial UI');
  for(const id of ids){
    const matched=[...html.matchAll(new RegExp('\\bid="'+id+'"','g'))];
    assert.equal(matched.length,1,`${id} appears ${matched.length} times`);
  }
});
test('all external scripts are local project files',()=>{
  assert.match(html,/<script src="core\.js"><\/script>/);
  assert.match(html,/<script src="app\.js"><\/script>/);
  assert.doesNotMatch(html,/<script[^>]+src="https?:/);
});
