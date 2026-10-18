/* Jolt Multi-POV Lab: pure analysis functions, usable in browsers and Node.
 * Consumes the v2.4 --json schema shipped with JoltCracker by Splosh.
 * No proprietary Jolt code is copied or executed here.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.JoltPOV = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const TPS = 20;
  const KIND = Object.freeze({
    removed: 'Tick counter jumped ahead', stalled: 'Counter stalled / lag',
    restart: 'New game instance', back: 'Returned to prior game',
    screen: 'Changed player screen',
  });
  const number = value => typeof value === 'number' && Number.isFinite(value);
  const finite = (value, fallback = 0) => number(value) ? value : fallback;
  const nameOf = (value, fallback) => typeof value === 'string' && value.trim() ? value.trim() : fallback;
  const safeArray = x => Array.isArray(x) ? x : [];
  const trimNum = n => Math.round(n * 1000) / 1000;
  const clamp = (x, a, b) => Math.max(a, Math.min(x, b));

  function parseJolt(input, filename = 'Jolt result.json', id = filename) {
    if (!input || typeof input !== 'object' || Array.isArray(input)) throw Error('This file is not a Jolt JSON object.');
    if (!Array.isArray(input.runs) || !Array.isArray(input.games) || !Array.isArray(input.breaks))
      throw Error('Not a supported Jolt raw result: missing games, runs or breaks. Use Jolt --json FILE.');
    const span = safeArray(input.span);
    if (span.length !== 2 || !number(span[0]) || !number(span[1]) || span[1] <= span[0])
      throw Error('Jolt result has an invalid video time span.');
    const warnings = [];
    const games = input.games.map((g, i) => {
      const n = Number(g?.number);
      if (!Number.isInteger(n) || n < 1) throw Error('Invalid game number in Jolt result.');
      return {number: n, screen: finite(g.screen, 0), uptimeAtVideoStart: number(g.uptime_at_video_start) ? g.uptime_at_video_start : null, label: `Game ${n}`};
    });
    const gameIds = new Set(games.map(g => g.number));
    if (gameIds.size !== games.length) throw Error('Duplicate game numbers in Jolt result.');
    const jolts = safeArray(input.jolts);
    let discarded = 0;
    const runs = input.runs.map((r, i) => {
      const game = Number(r?.game);
      if (!gameIds.has(game)) throw Error(`Run ${i + 1} references a missing game.`);
      const points = safeArray(r.points).filter(p => {
        const valid = Array.isArray(p) && p.length >= 3 && number(p[0]) && number(p[1]) && p[1] >= 0 && Number.isInteger(p[2]) && p[2] >= 0;
        if (!valid) discarded++;
        return valid;
      }).map(p => ({time: p[0], tick: p[1], jolt: p[2]})).sort((a,b)=>a.time-b.time);
      return {index: i, game, points, first: points[0] || null, last: points[points.length-1] || null};
    });
    if (discarded) warnings.push(`${discarded} malformed tick observations ignored.`);
    const breaks = input.breaks.map((b, i) => {
      const from = b?.from, to = b?.to;
      if (!number(from) || !number(to) || to < from) {
        warnings.push(`Break ${i + 1} had invalid times and was ignored.`);
        return null;
      }
      return {index:i, from, to, since: number(b.since) ? b.since : null,
        kind: nameOf(b.kind,'unknown'), game: number(b.game) ? b.game : null,
        jumpSeconds: number(b.jump_seconds) ? b.jump_seconds : null,
        framePositions:safeArray(b.frame_positions), screenshots:safeArray(b.frames)};
    }).filter(Boolean);
    if (breaks.length !== runs.length - 1 && runs.length) warnings.push('Break/run counts differ: not all transitions may be displayed.');
    const unread = safeArray(input.unread).filter(x=>Array.isArray(x)&&x.length===2&&number(x[0])&&number(x[1])&&x[1]>x[0]).map(x=>[x[0],x[1]]);
    if (typeof input.version !== 'string' && typeof input.version !== 'number') warnings.push('Jolt version missing from JSON.');
    const src = typeof input.source === 'string' ? input.source : (input.source?.url || input.source?.path || input.source?.file || '');
    return {id:String(id), name:nameOf(input.title,filename.replace(/\.json$/i,'')), filename, source:String(src || ''),
      version:String(input.version ?? 'unknown'), span:[span[0],span[1]], fps:finite(input.fps,0),
      reportedReadable:number(input.readable)?input.readable:null,
      games,runs,breaks,unread,joltCount:jolts.length,warnings};
  }

  function observedWindows(rec) {
    return rec.runs.filter(r=>r.first && r.last && r.last.time>r.first.time).map(r=>({
      recordingId:rec.id, game:r.game, from:r.first.time, to:r.last.time,
      count:r.points.length, run:r.index,
      firstTick:r.first.tick,lastTick:r.last.tick,
    }));
  }

  function unionIntervals(ranges) {
    const sorted = ranges.filter(p=>p&&number(p[0])&&number(p[1])&&p[1]>p[0]).map(p=>[p[0],p[1]]).sort((a,b)=>a[0]-b[0]);
    const result=[];
    for(const [from,to] of sorted){
      if(result.length && from<=result[result.length-1][1]) result[result.length-1][1]=Math.max(to,result[result.length-1][1]);
      else result.push([from,to]);
    }
    return result;
  }
  const secondsIn = ranges => unionIntervals(ranges).reduce((s,[a,b])=>s+b-a,0);
  function coverage(rec) {
    const observed=unionIntervals(observedWindows(rec).map(w=>[w.from,w.to]));
    const unread=unionIntervals(rec.unread || []);
    const duration=rec.span[1]-rec.span[0];
    return {observed, observedSeconds:secondsIn(observed), percent:duration>0?100*secondsIn(observed)/duration:0,
      unread, unreadSeconds:secondsIn(unread), duration};
  }

  // Anchors: [{id, label, times:{recording-id:time-in-video}}].
  // A single matched event establishes a translation, not a proof of synchronization.
  // Two or more events permit interpolation between anchors. Outside them, extrapolate
  // at 1:1 speed (never extend a suspected speed ramp indefinitely).
  function buildMapping(recordingId, masterId, anchors) {
    if (recordingId === masterId) return {
      aligned:true, mode:'master', points:[], drift:[], forward:t=>t, inverse:t=>t, confidence:'reference'
    };
    const matches=safeArray(anchors).filter(a=>number(a?.times?.[masterId])&&number(a?.times?.[recordingId]))
      .map(a=>({label:nameOf(a.label,'Shared event'), local:a.times[recordingId], global:a.times[masterId]}))
      .sort((a,b)=>a.local-b.local);
    if(!matches.length) return {aligned:false, mode:'unaligned', points:[], drift:[], forward:()=>null, inverse:()=>null, confidence:'none'};
    const issues=[];
    for(let i=1;i<matches.length;i++){
      if(matches[i].local<=matches[i-1].local || matches[i].global<=matches[i-1].global){
        issues.push('Anchor order is inconsistent. Review the event names and timestamps.');
        return {aligned:false, mode:'conflict', points:matches, issues, drift:[],forward:()=>null,inverse:()=>null,confidence:'none'};
      }
    }
    const drift=[];
    for(let i=1;i<matches.length;i++){
      const previous=matches[i-1],current=matches[i];
      drift.push({from:previous.label,to:current.label,
        localDuration:trimNum(current.local-previous.local),
        referenceDuration:trimNum(current.global-previous.global),
        delta:trimNum((current.global-previous.global)-(current.local-previous.local)),
        ratio:(current.global-previous.global)/(current.local-previous.local)});
    }
    const forward = time => {
      if(!number(time))return null;
      if(matches.length===1) return time+matches[0].global-matches[0].local;
      if(time<=matches[0].local)return time+matches[0].global-matches[0].local;
      const last=matches[matches.length-1];
      if(time>=last.local)return time+last.global-last.local;
      for(let i=1;i<matches.length;i++){
        if(time<=matches[i].local){
          const a=matches[i-1],b=matches[i];
          return a.global+(time-a.local)*(b.global-a.global)/(b.local-a.local);
        }
      }
      return null;
    };
    const inverse = time => {
      if(!number(time))return null;
      if(matches.length===1)return time+matches[0].local-matches[0].global;
      if(time<=matches[0].global)return time+matches[0].local-matches[0].global;
      const last=matches[matches.length-1];
      if(time>=last.global)return time+last.local-last.global;
      for(let i=1;i<matches.length;i++){
        if(time<=matches[i].global){
          const a=matches[i-1],b=matches[i];
          return a.local+(time-a.global)*(b.local-a.local)/(b.global-a.global);
        }
      }
      return null;
    };
    return {aligned:true,mode:matches.length===1?'offset':'piecewise',points:matches,drift,issues,forward,inverse,
      confidence:matches.length===1?'single-anchor':'multi-anchor'};
  }

  function allMappings(recordings, masterId, anchors) {
    return Object.fromEntries(recordings.map(r=>[r.id,buildMapping(r.id,masterId,anchors)]));
  }
  function mappedWindows(rec, mapping) {
    if(!mapping?.aligned) return [];
    return observedWindows(rec).map(w=>({...w,from:mapping.forward(w.from),to:mapping.forward(w.to)}));
  }
  function intersectionSeconds(windows, from, to) {
    if (!number(from)||!number(to)||to<=from)return 0;
    return secondsIn(windows.map(w=>[Math.max(from,w.from),Math.min(to,w.to)]));
  }
  function crossPovCandidates(recordings, masterId, anchors, padding=2) {
    const maps=allMappings(recordings,masterId,anchors);
    const all=recordings.flatMap(rec=>mappedWindows(rec,maps[rec.id]));
    const found=[];
    for(const rec of recordings){
      const map=maps[rec.id];
      for(const b of rec.breaks.filter(b=>b.kind==='removed')){
        const first=map.aligned?map.forward(b.from):null;
        const last=map.aligned?map.forward(b.to):null;
        const from=number(first)?Math.min(first,last)-padding:null;
        const to=number(last)?Math.max(first,last)+padding:null;
        const candidates=[];
        if (number(from)&&number(to)) {
          const groups=new Map();
          for(const w of all) {
            if(w.recordingId===rec.id) continue;
            const key=w.recordingId+'\u0000'+w.game;
            if(!groups.has(key))groups.set(key,[]);
            groups.get(key).push(w);
          }
          for(const windows of groups.values()){
            const visible=intersectionSeconds(windows,from,to);
            const proportion=visible/(to-from);
            if(visible>=0.5 && proportion>=0.35)
              candidates.push({recordingId:windows[0].recordingId,game:windows[0].game,
                observedSeconds:trimNum(visible),windowSeconds:trimNum(to-from),fraction:proportion});
          }
          candidates.sort((a,b)=>b.fraction-a.fraction);
        }
        found.push({recordingId:rec.id,breakIndex:b.index,from:b.from,to:b.to,
          globalFrom:first,globalTo:last,reportedMissingSeconds:b.jumpSeconds,
          aligned:map.aligned,alignmentMode:map.mode,candidates});
      }
    }
    return found;
  }

  function summary(recordings, masterId, anchors) {
    const maps=allMappings(recordings,masterId,anchors);
    return {recordings:recordings.length,playerTracks:recordings.reduce((s,r)=>s+r.games.length,0),
      tickReadings:recordings.reduce((s,r)=>s+r.runs.reduce((a,run)=>a+run.points.length,0),0),
      reportedCuts:recordings.reduce((s,r)=>s+r.breaks.filter(b=>b.kind==='removed').length,0),
      alignedRecordings:recordings.filter(r=>maps[r.id]?.aligned).length,
      crossPovCandidates:crossPovCandidates(recordings,masterId,anchors).reduce((s,c)=>s+c.candidates.length,0)};
  }

  function timeLabel(sec, decimals=2) {
    if(!number(sec))return '--:--';
    const neg=sec<0?'-':'';sec=Math.abs(sec);
    const h=Math.floor(sec/3600),m=Math.floor(sec%3600/60),s=(sec%60).toFixed(decimals).padStart(decimals?decimals+3:2,'0');
    return neg+(h?h+':'+String(m).padStart(2,'0'):String(m))+':'+s;
  }
  function csvEscape(value){let s=String(value??'');return '"'+s.replace(/"/g,'""')+'"';}
  function exportBreakCSV(recordings, masterId, anchors) {
    const candidates=crossPovCandidates(recordings,masterId,anchors);
    const rows=[['recording','game','kind','video_from','video_to','missing_seconds','aligned_from','aligned_to','other_recording','other_game','other_observed_fraction']];
    for (const r of recordings) for(const b of r.breaks){
      const c=candidates.find(x=>x.recordingId===r.id&&x.breakIndex===b.index);
      const first=c?.candidates[0];
      rows.push([r.name,b.game,b.kind,b.from,b.to,b.jumpSeconds,
        c?.globalFrom??'',c?.globalTo??'',
        first?recordings.find(other=>other.id===first.recordingId)?.name||'':'',
        first?.game??'',first?trimNum(first.fraction):'']);
    }
    return rows.map(row=>row.map(csvEscape).join(',')).join('\r\n')+'\r\n';
  }
  return {TPS,KIND,parseJolt,observedWindows,unionIntervals,secondsIn,coverage,buildMapping,allMappings,
    mappedWindows,intersectionSeconds,crossPovCandidates,summary,timeLabel,exportBreakCSV,clamp};
});
