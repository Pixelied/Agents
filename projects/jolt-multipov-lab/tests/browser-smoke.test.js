'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const dir=path.resolve(__dirname,'..');
const core=require('../core.js');
const html=fs.readFileSync(path.join(dir,'index.html'),'utf8');
class FakeNode{
  constructor(tag='div'){
    this.tag=tag;this.children=[];this.events={};this.dataset={};this.style={};
    this.value='';this.textContent='';this.className='';this.type='';this.src='';
    this.classList={add(){},remove(){}};
  }
  append(...nodes){for(const node of nodes){if(node&&typeof node==='object'){node.parentElement=this;this.children.push(node);}}}
  replaceChildren(...nodes){this.children=[];this.append(...nodes);}
  addEventListener(type,fn){this.events[type]=fn;}
  setAttribute(){}
  removeAttribute(name){if(name==='src')this.src='';}
  load(){} pause(){} scrollIntoView(){}
  querySelector(sel){return sel.includes('submit')?this.querySelectorAll('button')[0]||null:null;}
  querySelectorAll(sel){
    const found=[];
    function visit(node){for(const child of node.children){
      if(sel==='.track-cursor'&&child.className==='track-cursor')found.push(child);
      if(sel==='input[data-record]'&&child.tag==='input'&&child.dataset.record)found.push(child);
      if(sel==='button'&&child.tag==='button')found.push(child);
      visit(child);
    }}visit(this);return found;
  }
}
function boot(){
  const ids={};for(const [,id] of html.matchAll(/\bid="([^"]+)"/g))ids[id]=new FakeNode();
  for(const id of ['videoA','videoB'])ids[id].paused=true;
  ids.anchorForm.querySelector=()=>new FakeNode('button');
  const sandbox={window:{JoltPOV:core},document:{getElementById:id=>ids[id],createElement:tag=>new FakeNode(tag),body:new FakeNode('body')},
    console,URL:{createObjectURL:()=>'',revokeObjectURL(){}},Blob,setInterval:()=>0,setTimeout:()=>0};
  vm.runInNewContext(fs.readFileSync(path.join(dir,'app.js'),'utf8'),sandbox,{filename:'app.js'});
  return ids;
}
const sample=filename=>JSON.parse(fs.readFileSync(path.join(dir,'examples',filename),'utf8'));
test('browser UI initializes without real DOM/browser service or network',()=>{
  const ids=boot();
  assert.equal(ids.statRecording.textContent,'0');
  assert.equal(ids.statTracks.textContent,'0');
  assert.equal(ids.statCuts.textContent,'0');
});
test('file input imports two actual-schema fixtures and populates statistics and perspective tracks',async()=>{
  const ids=boot();
  const files=['speedrunner.json','hunter.json'].map(name=>({name,size:2000,text:async()=>JSON.stringify(sample(name))}));
  ids.fileInput.events.change({target:{files,value:''}});
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(ids.statRecording.textContent,'2');
  assert.equal(ids.statTracks.textContent,'3');
  assert.equal(ids.statCuts.textContent,'1');
  assert.equal(ids.cutCount.textContent,'1 cut');
  assert.equal(ids.errorBox.textContent,'');
  assert.equal(ids.recordList.children.filter(n=>n.className==='record').length,2);
  assert.equal(ids.timeline.children.length,2);
});

test('shared-event controls align two recordings and surface an alternate view',async()=>{
  const ids=boot();
  const files=['speedrunner.json','hunter.json'].map(name=>({name,size:2000,text:async()=>JSON.stringify(sample(name))}));
  ids.fileInput.events.change({target:{files,value:''}});
  await new Promise(resolve=>setImmediate(resolve));
  for(const [label,first,second] of [['Portal','20','22'],['Finish','40','47']]){
    ids.anchorLabel.value=label;
    for(const input of ids.anchorInputs.querySelectorAll('input[data-record]'))input.value=input.dataset.record==='r1'?first:second;
    ids.anchorForm.events.submit({preventDefault(){}});
  }
  assert.equal(ids.statAligned.textContent,'2');
  assert.ok(ids.alignmentWarnings.children.some(x=>x.textContent.includes('5.00 s')));
  assert.equal(ids.cutList.children[0].className,'event-row');
  ids.cutList.children[0].events.click();
  const descriptions=ids.detail.children.map(n=>n.textContent).join(' | ');
  assert.ok(descriptions.includes('Sample manhunt edit'));
  assert.ok(ids.detail.children.some(n=>n.className==='record'),'expected alternative-POV candidate');
});
