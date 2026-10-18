'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const C=require('../core.js');
const example=p=>JSON.parse(fs.readFileSync(path.join(__dirname,'../examples',p),'utf8'));
const a=C.parseJolt(example('speedrunner.json'),'speedrunner.json','a');
const b=C.parseJolt(example('hunter.json'),'hunter.json','b');
const anchors=[{id:'a1',label:'Portal',times:{a:20,b:22}},{id:'a2',label:'Finish',times:{a:40,b:47}}];

test('parses real v2.4 schema fields, multiple POV games, breaks, and Jolt point triples',()=>{
  assert.equal(a.version,'2.4');
  assert.equal(a.games.length,2);
  assert.equal(a.runs.length,4);
  assert.equal(a.breaks.length,3);
  assert.equal(a.runs[0].points[1].tick,5080);
  assert.equal(a.runs[3].points[2].jolt,11);
  assert.deepEqual(a.games.map(g=>g.screen),[0,1]);
  assert.equal(a.breaks[2].jumpSeconds,5);
  assert.equal(a.warnings.length,0);
});

test('rejects other JSON and invalid spans, avoids inventing games',()=>{
  assert.throws(()=>C.parseJolt({foo:'bar'}),/missing games/);
  assert.throws(()=>C.parseJolt({...example('speedrunner.json'),span:[20,2]}),/time span/);
  assert.throws(()=>C.parseJolt({...example('hunter.json'),games:[]}),/missing game/);
});

test('collects separate observed windows instead of treating unobserved time as proven',()=>{
  assert.deepEqual(C.observedWindows(a).map(w=>[w.game,w.from,w.to]),[[1,0,8],[2,9,15],[1,16,25],[1,26,40]]);
  const c=C.coverage(a);
  assert.equal(c.observedSeconds,37);
  assert.equal(Math.round(c.percent*10)/10,61.7);
  assert.equal(c.unreadSeconds,11);
  assert.equal(C.secondsIn([[1,5],[3,6],[9,10]]),6);
});

test('one shared event gives offset-only mapping, not a made-up clock-rate correction',()=>{
  const m=C.buildMapping('b','a',anchors.slice(0,1));
  assert.equal(m.mode,'offset');
  assert.equal(m.forward(27),25);
  assert.equal(m.inverse(25),27);
  assert.equal(m.drift.length,0);
});

test('two events reveal temporal mismatch, interpolate, invert, and never extrapolate speed ramps',()=>{
  const m=C.buildMapping('b','a',anchors);
  assert.equal(m.mode,'piecewise');
  assert.equal(m.drift.length,1);
  assert.equal(m.drift[0].delta,-5);
  assert.equal(m.forward(22),20);
  assert.equal(m.forward(34.5),30);
  assert.equal(m.inverse(30),34.5);
  assert.equal(m.forward(50),43); // end uses unit-speed extrapolation
  assert.equal(m.forward(12),10); // start uses unit-speed extrapolation
});

test('refuses inconsistent event order instead of silently reversing time',()=>{
  const reversed=[{label:'first',times:{a:20,b:30}},{label:'second',times:{a:10,b:35}}];
  const m=C.buildMapping('b','a',reversed);
  assert.equal(m.aligned,false);
  assert.equal(m.mode,'conflict');
  assert.equal(m.forward(32),null);
});

test('cross-POV candidates require aligned independently observed runs',()=>{
  assert.equal(C.crossPovCandidates([a,b],'a',[])[0].candidates.length,0);
  const candidates=C.crossPovCandidates([a,b],'a',anchors);
  assert.equal(candidates.length,1);
  assert.equal(candidates[0].reportedMissingSeconds,5);
  assert.equal(candidates[0].candidates[0].recordingId,'b');
  assert.ok(candidates[0].candidates[0].fraction>0.5);
  const sum=C.summary([a,b],'a',anchors);
  assert.equal(sum.reportedCuts,1);
  assert.equal(sum.playerTracks,3);
  assert.equal(sum.alignedRecordings,2);
});

test('CSV escapes filenames and preserves both aligned and unaligned events',()=>{
  const aa={...a,name:'Speedrunner, "Test"'};
  const csv=C.exportBreakCSV([aa,b],'a',anchors);
  assert.match(csv,/"Speedrunner, ""Test"""/);
  assert.match(csv,/"removed"/);
  assert.match(csv,/"5"/);
  assert.match(csv,/hunter/i);
});

test('support small and multi-hour timestamps',()=>{
  assert.equal(C.timeLabel(5.5),'0:05.50');
  assert.equal(C.timeLabel(3665.123),'1:01:05.12');
  assert.equal(C.timeLabel(-3,0),'-0:03');
});
