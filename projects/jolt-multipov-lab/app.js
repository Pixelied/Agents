/* Jolt Multi-POV Lab browser UI. Works offline using local File APIs. */
(() => {
  'use strict';
  const C=window.JoltPOV;
  if(!C) throw Error('core.js did not load');
  const $=id=>document.getElementById(id);
  const state={recordings:[],anchors:[],masterId:null,selected:null,cursor:0,
    videos:new Map(),recordSeq:0,anchorSeq:0,editAnchorId:null};
  const make=(tag,cls='',content='')=>{const e=document.createElement(tag);if(cls)e.className=cls;if(content!==undefined)e.textContent=content;return e;};
  const findRec=id=>state.recordings.find(r=>r.id===id);
  const gameName=(r,id)=>r?.games.find(g=>g.number===id)?.label||'Game '+id;
  const warning=message=>{const e=$('errorBox');e.textContent=message;e.classList.add('show');};
  const clearWarning=()=>{$('errorBox').classList.remove('show');$('errorBox').textContent='';};
  const mappings=()=>C.allMappings(state.recordings,state.masterId,state.anchors);
  const clampTo=(x,r)=>C.clamp(x,r.span[0],r.span[1]);
  const printable=time=>C.timeLabel(time,2);
  const timeValue=value=>{
    if(typeof value!=='string'||!value.trim())return null;
    const chunks=value.trim().split(':');
    if(chunks.length>3)return null;
    let total=0;
    for(const part of chunks){
      if(!/^\d+(?:\.\d+)?$/.test(part))return null;
      const n=Number(part);if(!Number.isFinite(n))return null;
      total=total*60+n;
    }
    return total;
  };
  function download(filename,body,mime){
    const blob=new Blob([body],{type:mime}),url=URL.createObjectURL(blob),a=make('a');
    a.href=url;a.download=filename;document.body.append(a);a.click();a.remove();
    setTimeout(()=>URL.revokeObjectURL(url),120000);
  }
  function selectOptions(element, current, allowEmpty=false){
    element.replaceChildren();
    if(allowEmpty){const opt=make('option','', 'None');opt.value='';element.append(opt);}
    for(const r of state.recordings){
      const o=make('option','',r.name);o.value=r.id;element.append(o);
    }
    element.value=state.recordings.some(r=>r.id===current)?current:(allowEmpty?'':state.recordings[0]?.id||'');
  }
  function adjustCursor(){
    const master=findRec(state.masterId);
    if(!master){state.cursor=0;return;}
    state.cursor=clampTo(state.cursor,master);
  }

  async function loadFiles(files){
    clearWarning();let added=0;
    for(const file of files){
      if(file.size>125*1024*1024){warning(`${file.name}: over the 125 MB safety limit.`);continue;}
      try{
        const data=JSON.parse(await file.text());
        if(data?.format==='jolt-multipov-case-1'){
          loadCase(data);added++;
        }else{
          const id='r'+(++state.recordSeq);
          const rec=C.parseJolt(data,file.name,id);
          state.recordings.push(rec);
          if(!state.masterId){state.masterId=id;state.cursor=rec.span[0];}
          added++;
        }
      }catch(err){warning(`${file.name}: ${err.message||'Could not read the JSON file'}`);}
    }
    if(added) renderAll();
  }
  function loadCase(data){
    if(!Array.isArray(data.recordings)||!Array.isArray(data.anchors)||data.recordings.length>50)
      throw Error('Not a valid saved Multi-POV case.');
    // Saved cases contain normalized Jolt data, not arbitrary script/code.
    for(const r of data.recordings){
      if(typeof r?.id!=='string'||!Array.isArray(r.games)||!Array.isArray(r.runs)||
         !Array.isArray(r.breaks)||!Array.isArray(r.span)||r.span.length!==2)
        throw Error('The saved case has malformed recording data.');
    }
    state.recordings=data.recordings;
    state.anchors=data.anchors.filter(a=>a&&typeof a.id==='string'&&a.times&&typeof a.times==='object'&&!Array.isArray(a.times));
    state.masterId=state.recordings.some(r=>r.id===data.masterId)?data.masterId:state.recordings[0]?.id||null;
    state.recordSeq=Math.max(state.recordings.length,...state.recordings.map(r=>Number(String(r.id).replace(/^r/,''))||0));
    state.anchorSeq=state.anchors.length;
    state.selected=null;state.cursor=findRec(state.masterId)?.span?.[0]||0;
    for(const video of state.videos.values())URL.revokeObjectURL(video.url);
    state.videos.clear();
  }
  function renderRecordings(){
    const list=$('recordList');list.replaceChildren();
    for(const [i,r] of state.recordings.entries()){
      const wrap=make('div','record'),icon=make('span','record-index',String(i+1)),info=make('div','record-info');
      const name=make('input','record-name');name.type='text';name.value=r.name;name.title='Rename recording';
      name.style.cssText='width:100%;padding:1px 3px;border:1px solid transparent;background:transparent;';
      name.addEventListener('change',()=>{r.name=name.value.trim()||r.filename;renderAll();});
      const sub=make('span','record-sub',`${r.games.length} ${r.games.length===1?'game':'games'} · ${C.timeLabel(r.span[1]-r.span[0])}`);
      info.append(name,sub);
      const remove=make('button','iconbtn','×');remove.type='button';remove.title='Remove this recording';
      remove.addEventListener('click',()=>{
        const video=state.videos.get(r.id);if(video)URL.revokeObjectURL(video.url);state.videos.delete(r.id);
        state.recordings=state.recordings.filter(x=>x.id!==r.id);
        for(const a of state.anchors)delete a.times[r.id];
        state.anchors=state.anchors.filter(a=>Object.keys(a.times).length>=2);
        if(state.masterId===r.id)state.masterId=state.recordings[0]?.id||null;
        state.selected=null;adjustCursor();renderAll();
      });
      wrap.append(icon,info,remove);list.append(wrap);
      if(r.warnings?.length){const p=make('div','hint warning',r.warnings.join(' '));list.append(p);}
    }
  }
  function renderAnchorInputs(prefill={}){
    const inputs=$('anchorInputs');inputs.replaceChildren();
    for(const r of state.recordings){
      const container=make('div','field'),label=make('label','',r.name),input=make('input');
      input.type='text';input.dataset.record=r.id;input.placeholder='0:12:35.50';
      if(prefill[r.id]!==undefined)input.value=printable(prefill[r.id]);
      container.append(label,input);inputs.append(container);
    }
  }
  function renderAnchors(){
    const list=$('anchorList');list.replaceChildren();
    for(const a of state.anchors){
      const row=make('div','anchor-row'),content=make('div');content.style.flex='1';
      content.append(make('div','anchor-title',a.label));
      content.append(make('div','anchor-values',state.recordings.filter(r=>typeof a.times[r.id]==='number').map(r=>r.name+' '+printable(a.times[r.id])).join(' / ')));
      const edit=make('button','btn slim','Edit');edit.addEventListener('click',()=>{
        state.editAnchorId=a.id;$('anchorLabel').value=a.label;renderAnchorInputs(a.times);
        $('anchorForm').querySelector('button[type=submit]').textContent='Update event';
      });
      const del=make('button','iconbtn','×');del.title='Delete anchor';del.addEventListener('click',()=>{
        state.anchors=state.anchors.filter(x=>x.id!==a.id);
        if(state.editAnchorId===a.id)resetAnchorForm();renderAll();
      });
      row.append(content,edit,del);list.append(row);
    }
    const errors=$('alignmentWarnings');errors.replaceChildren();
    const maps=mappings();
    for(const rec of state.recordings){
      if(rec.id===state.masterId)continue;
      const m=maps[rec.id];
      if(m?.mode==='conflict'){errors.append(make('p','hint warning',`${rec.name}: anchor order conflicts. Fix the timestamps before comparing cuts.`));continue;}
      for(const d of m?.drift||[])if(Math.abs(d.delta)>=0.5)
        errors.append(make('p','hint warning',`${rec.name}: ${d.from} → ${d.to} differs by ${Math.abs(d.delta).toFixed(2)} s. This may reflect edits, speed changes, or inaccurate anchors.`));
    }
  }
  function resetAnchorForm(){state.editAnchorId=null;$('anchorLabel').value='';renderAnchorInputs();$('anchorForm').querySelector('button[type=submit]').textContent='Add shared event';}
  function onAnchorSubmit(event){
    event.preventDefault();clearWarning();
    const label=$('anchorLabel').value.trim(),times={};
    for(const input of $('anchorInputs').querySelectorAll('input[data-record]')){
      if(!input.value.trim())continue;
      const t=timeValue(input.value);
      const rec=findRec(input.dataset.record);
      if(t===null||!rec||t<rec.span[0]||t>rec.span[1]){
        warning(`Invalid timestamp for ${rec?.name||input.dataset.record}; use seconds, MM:SS or HH:MM:SS.ms inside the video's scan span.`);
        return;
      }
      times[input.dataset.record]=t;
    }
    if(!label || Object.keys(times).length<2 || !Object.prototype.hasOwnProperty.call(times,state.masterId)){
      warning('Give the same event a timestamp in the reference recording and at least one other recording.');return;
    }
    if(state.editAnchorId){const a=state.anchors.find(a=>a.id===state.editAnchorId);if(a){a.label=label;a.times=times;}}
    else state.anchors.push({id:'a'+(++state.anchorSeq),label,times});
    resetAnchorForm();renderAll();
  }
  function renderStats(){
    const s=C.summary(state.recordings,state.masterId,state.anchors);
    for(const [name,value] of Object.entries({statRecording:s.recordings,statTracks:s.playerTracks,statReadings:s.tickReadings,statCuts:s.reportedCuts,statAligned:s.alignedRecordings}))
      $(name).textContent=value.toLocaleString();
    const enabled=state.recordings.length>0;
    ['downloadCase','exportCSV','exportHTML'].forEach(id=>$(id).disabled=!enabled);
    $('cutCount').textContent=s.reportedCuts+' '+(s.reportedCuts===1?'cut':'cuts');
  }
  function layoutDomain(rec,m,allMaps){
    const master=findRec(state.masterId);
    if(m.aligned && master){
      const left=Math.min(master.span[0],...state.recordings.filter(r=>allMaps[r.id].aligned).map(r=>allMaps[r.id].forward(r.span[0])));
      const right=Math.max(master.span[1],...state.recordings.filter(r=>allMaps[r.id].aligned).map(r=>allMaps[r.id].forward(r.span[1])));
      return [left,right];
    }
    return rec.span;
  }
  function renderTimeline(){
    const root=$('timeline'),axis=$('axis');root.replaceChildren();axis.replaceChildren();
    if(!state.recordings.length){root.append(make('p','empty','Add a raw result to see each player’s game timeline.'));return;}
    const maps=mappings();
    for(const rec of state.recordings){
      const m=maps[rec.id],domain=layoutDomain(rec,m,maps),length=domain[1]-domain[0]||1;
      const pos=t=>100*C.clamp(((m.aligned?m.forward(t):t)-domain[0])/length,0,1);
      const section=make('section','timeline-record'),head=make('div','track-head');
      head.append(make('span','track-header-text',rec.name),make('span','track-header-meta',m.aligned?
        (m.mode==='master'?'Reference clock':m.points.length+' linked event'+(m.points.length===1?'':'s')):'Not synchronized (own clock)'));
      section.append(head);
      const windows=C.observedWindows(rec);
      for(const game of rec.games){
        const row=make('div','track-row'),label=make('input','track-label');
        label.type='text';label.value=game.label||`Game ${game.number}`;
        label.title='Rename this game / POV';label.setAttribute('aria-label',`Rename game ${game.number} in ${rec.name}`);
        label.style.cssText='background:transparent;border:1px solid transparent;padding:3px;';
        label.addEventListener('change',()=>{game.label=label.value.trim()||`Game ${game.number}`;renderAll();});
        const track=make('div','track');track.dataset.start=domain[0];track.dataset.end=domain[1];track.dataset.aligned=m.aligned;
        track.title=m.aligned?'Click to seek the reference timeline':'Add shared event anchors to align this recording';
        for(const w of windows.filter(w=>w.game===game.number)){
          const fill=make('span','track-fill');const left=pos(w.from),right=pos(w.to);
          fill.style.left=left+'%';fill.style.width=Math.max(0,right-left)+'%';
          fill.title=`${w.count} tick readings, ${printable(w.from)} – ${printable(w.to)} local`;
          track.append(fill);
        }
        for(const [a,b] of rec.unread){const el=make('span','track-unknown');const left=pos(a),right=pos(b);el.style.left=left+'%';el.style.width=Math.max(0,right-left)+'%';track.append(el);}
        for(const br of rec.breaks.filter(b=>b.game===game.number)){
          const marker=make('span',br.kind==='removed'?'track-cut':'track-change');marker.style.left=pos(br.from)+'%';
          marker.title=`${C.KIND[br.kind]||br.kind} at ${printable(br.from)}`;track.append(marker);
        }
        const cursor=make('span','track-cursor');track.append(cursor);
        track.addEventListener('click',evt=>{
          const rect=track.getBoundingClientRect(),x=C.clamp((evt.clientX-rect.left)/rect.width,0,1);
          if(!m.aligned){warning('This recording is not aligned to the reference. Add a shared event first.');return;}
          setCursor(domain[0]+x*length,true);
        });
        row.append(label,track);section.append(row);
      }
      const ticks=make('div','tick-labels');
      for(const t of [0,.25,.5,.75,1])ticks.append(make('span','',C.timeLabel(domain[0]+t*length,0)));
      section.append(ticks);root.append(section);
    }
    updateCursors();
  }
  function updateCursors(){
    for(const el of $('timeline').querySelectorAll('.track-cursor')){
      const track=el.parentElement;if(track.dataset.aligned!=='true'){el.style.display='none';continue;}
      const from=Number(track.dataset.start),to=Number(track.dataset.end),pct=100*(state.cursor-from)/(to-from||1);
      el.style.display=pct>=0&&pct<=100?'block':'none';el.style.left=pct+'%';
    }
  }
  function getCuts(){
    const candidate=C.crossPovCandidates(state.recordings,state.masterId,state.anchors),maps=mappings();
    const cuts=[];
    for(const rec of state.recordings)for(const br of rec.breaks.filter(b=>b.kind==='removed')){
      const c=candidate.find(c=>c.recordingId===rec.id&&c.breakIndex===br.index);
      cuts.push({key:rec.id+':'+br.index,rec,break:br,candidates:c?.candidates||[],
        globalFrom:maps[rec.id].aligned?maps[rec.id].forward(br.from):null,alignmentMode:maps[rec.id].mode});
    }
    cuts.sort((a,b)=>{
      if(a.globalFrom!==null&&b.globalFrom!==null)return a.globalFrom-b.globalFrom;
      return a.rec.name.localeCompare(b.rec.name)||a.break.from-b.break.from;
    });
    return cuts;
  }
  function renderCuts(){
    const list=$('cutList');list.replaceChildren();
    const cuts=getCuts();
    if(!cuts.length){list.append(make('div','empty',state.recordings.length?'No Jolt-reported removed-time breaks in these results. This does not guarantee unedited video.':'No reports loaded yet.'));renderDetail();return;}
    for(const cut of cuts.slice(0,800)){
      const row=make('button','event-row'+(state.selected===cut.key?' active':''));row.type='button';
      const left=make('span','event-time',printable(cut.break.from));
      const body=make('span','event-body'),title=make('span','event-title',cut.rec.name+' · '+gameName(cut.rec,cut.break.game));
      const desc=make('span','event-description',`${cut.break.jumpSeconds===null?'Unknown':cut.break.jumpSeconds.toFixed(2)+' s'} estimated missing / sped up`+(cut.candidates.length?` · ${cut.candidates.length} other view${cut.candidates.length===1?'':'s'} near transition`:''));
      body.append(title,desc);
      const kind=make('span','event-kind','CUT');row.append(left,body,kind);
      row.addEventListener('click',()=>{
        state.selected=cut.key;
        if(cut.globalFrom!==null)setCursor(cut.globalFrom,true);
        renderCuts();renderDetail();
      });
      list.append(row);
    }
    if(cuts.length>800)list.append(make('p','hint',`First 800 of ${cuts.length} cuts shown. Export CSV for all.`));
    renderDetail();
  }
  function textRow(label,value){
    const p=make('p','');p.style.cssText='display:flex;gap:16px;justify-content:space-between;margin:7px 0;font-size:12px;';
    const a=make('span','',label),b=make('strong','',value);a.style.color='var(--muted)';b.style.textAlign='right';p.append(a,b);return p;
  }
  function renderDetail(){
    const root=$('detail');root.replaceChildren();
    const item=getCuts().find(c=>c.key===state.selected);
    if(!item){root.append(make('p','empty','Choose a reported cut to inspect it.'));return;}
    const b=item.break,r=item.rec;
    root.append(make('div','tiny','Jolt-reported discontinuity'));
    const name=make('h2','',r.name+' / '+gameName(r,b.game));name.style.marginTop='9px';root.append(name);
    root.append(textRow('Video boundaries',`${printable(b.from)} → ${printable(b.to)}`));
    root.append(textRow('Missing / accelerated',b.jumpSeconds===null?'Unknown':b.jumpSeconds.toFixed(2)+' s (Jolt estimate)'));
    root.append(textRow('Aligned video time',item.globalFrom===null?'No shared anchor':printable(item.globalFrom)));
    root.append(textRow('Alignment',item.alignmentMode));
    const title=make('h3','','Other recorded viewpoints');title.style.margin='17px 0 9px';root.append(title);
    if(item.globalFrom===null){root.append(make('p','hint','Add shared-event anchors between this recording and another one to compare independent coverage.'));return;}
    if(!item.candidates.length){root.append(make('p','hint','No other aligned recording has sufficient Jolt-observed coverage close to the transition. This does not prove the footage is unavailable.'));return;}
    for(const candidate of item.candidates){
      const rec=findRec(candidate.recordingId);
      const div=make('div','record');div.style.marginBottom='7px';
      const content=make('div','record-info');content.append(make('div','record-name',(rec?.name||'Recording')+' / '+gameName(rec,candidate.game)));
      content.append(make('div','record-sub',`${Math.round(candidate.fraction*100)}% of the nearby review interval lies inside matched-observation runs`));
      const button=make('button','btn slim','Review');button.addEventListener('click',()=>{
        $('selectB').value=candidate.recordingId;updatePlayer('B');seekBoth();$('videoB').scrollIntoView({behavior:'smooth',block:'center'});
      });div.append(content,button);root.append(div);
    }
    root.append(make('p','hint','These are lead candidates near the boundary, not proof they capture the missing in-game seconds. Intervening time may not exist on the reference video clock.'));
  }
  function setCursor(value,seek=false){
    const master=findRec(state.masterId);if(!master)return;
    state.cursor=clampTo(value,master);
    $('scrub').min=master.span[0];$('scrub').max=master.span[1];$('scrub').value=state.cursor;
    $('scrubLabel').value=printable(state.cursor);$('scrubLabel').textContent=printable(state.cursor);
    updateCursors();if(seek)seekBoth();
  }
  function populatePlayerSelects(){
    selectOptions($('selectA'),$('selectA').value||state.masterId);
    const other=state.recordings.find(r=>r.id!==state.masterId)?.id||state.masterId;
    const priorB=$('selectB').value;
    selectOptions($('selectB'),priorB&&priorB!==$('selectA').value?priorB:other);
    updatePlayer('A');updatePlayer('B');
  }
  function updatePlayer(side){
    const recId=$('select'+side).value,rec=findRec(recId),video=$('video'+side);
    const f=state.videos.get(recId);
    if(video.dataset.record!==recId || (!!f&&video.dataset.file!==f.url)){
      video.pause();video.removeAttribute('src');video.load();video.dataset.record=recId||'';video.dataset.file=f?.url||'';
      if(f){video.src=f.url;video.load();}
    }
    const m=mappings()[recId];
    $('videoStatus'+side).textContent=!rec?'Add a recording first.':!f?'Choose the matching original video file.':
      (m?.aligned?'Attached: '+f.file.name+' · '+m.mode+' alignment':'Attached: '+f.file.name+' · not aligned to reference');
  }
  function attachVideo(side,file){
    const id=$('select'+side).value;if(!id||!file)return;
    const old=state.videos.get(id);if(old)URL.revokeObjectURL(old.url);
    const url=URL.createObjectURL(file);state.videos.set(id,{file,url});
    updatePlayer('A');updatePlayer('B');seekBoth();
  }
  function seekBoth(){
    const maps=mappings();
    for(const side of ['A','B']){
      const video=$('video'+side),id=$('select'+side).value,map=maps[id];
      if(!video.src || !map?.aligned)continue;
      const t=map.inverse(state.cursor);
      if(typeof t!=='number'||!Number.isFinite(t))continue;
      try{const maximum=Number.isFinite(video.duration)?video.duration:Infinity;video.currentTime=Math.max(0,Math.min(t,maximum));}catch(_){/* metadata not ready yet */}
    }
  }
  function syncPlayback(){
    const left=$('videoA'),right=$('videoB');
    if(left.paused||right.paused||!left.src||!right.src)return;
    const maps=mappings(),a=maps[$('selectA').value],b=maps[$('selectB').value];
    if(!a?.aligned||!b?.aligned)return;
    const global=a.forward(left.currentTime),target=b.inverse(global);
    if(typeof target==='number'&&Math.abs(right.currentTime-target)>0.35 && target>=0 && target<=(right.duration||Infinity)){
      try{right.currentTime=target;}catch(_){/* seeking unavailable */}
    }
  }
  function renderAll(){
    adjustCursor();renderRecordings();selectOptions($('masterSelect'),state.masterId);
    renderAnchorInputs();
    const editing=state.anchors.find(a=>a.id===state.editAnchorId);
    if(editing){$('anchorLabel').value=editing.label;renderAnchorInputs(editing.times);$('anchorForm').querySelector('button[type=submit]').textContent='Update event';}
    renderAnchors();renderStats();renderTimeline();renderCuts();populatePlayerSelects();
    setCursor(state.cursor);
  }
  const escapeHTML=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function exportCase(){
    if(!state.recordings.length)return;
    const caseData={format:'jolt-multipov-case-1',version:1,exportedAt:new Date().toISOString(),
      masterId:state.masterId,recordings:state.recordings,anchors:state.anchors,
      caution:'Matched observations and anchor alignment do not prove video authenticity.'};
    download('jolt-multipov-case.json',JSON.stringify(caseData,null,2),'application/json');
  }
  function exportReport(){
    const cuts=getCuts(),s=C.summary(state.recordings,state.masterId,state.anchors);
    const rows=cuts.map(c=>{
      const b=c.break;return '<tr>'+[
        c.rec.name,gameName(c.rec,b.game),printable(b.from),printable(b.to),b.jumpSeconds===null?'n/a':b.jumpSeconds.toFixed(2)+' s',
        c.globalFrom===null?'not aligned':printable(c.globalFrom),
        c.candidates.map(x=>(findRec(x.recordingId)?.name||'?')+' / '+gameName(findRec(x.recordingId),x.game)).join('; ')||'none'
      ].map(v=>'<td>'+escapeHTML(v)+'</td>').join('')+'</tr>';
    }).join('');
    const anchorRows=state.anchors.map(a=>'<li>'+escapeHTML(a.label)+': '+escapeHTML(state.recordings.filter(r=>typeof a.times[r.id]==='number').map(r=>r.name+' at '+printable(a.times[r.id])).join('; '))+'</li>').join('');
    const html=`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Jolt Multi-POV Case Summary</title><style>body{font:14px/1.55 system-ui,sans-serif;background:#101215;color:#eef0ef;margin:0 auto;max-width:1200px;padding:32px}h1{font-size:26px}h2{margin-top:32px}p{color:#b7babc}table{border-collapse:collapse;width:100%;min-width:720px}th,td{padding:10px 12px;text-align:left;border-bottom:1px solid #3d4247}th{color:#f2c94c}section{overflow:auto}.caution{border-left:3px solid #f2c94c;padding:12px;background:#22221c}</style></head><body><h1>Jolt Multi-POV Case Summary</h1><p>${s.recordings} recordings · ${s.playerTracks} game tracks · ${s.tickReadings} observations · ${s.reportedCuts} Jolt-reported cuts · ${s.alignedRecordings} aligned recordings</p><p class="caution">This companion consumes Jolt's reported readings and human-provided alignment events. A reported time gap does not establish intent, and an alternative view near a cut does not reconstruct the missing frames.</p><h2>Shared event anchors</h2><ul>${anchorRows||'<li>No matched events.</li>'}</ul><h2>Reported cuts and review leads</h2><section><table><thead><tr><th>Recording</th><th>POV</th><th>From</th><th>To</th><th>Jolt-estimated gap</th><th>Mapped time</th><th>Alternate review leads</th></tr></thead><tbody>${rows||'<tr><td colspan="7">No Jolt-reported removed-time breaks.</td></tr>'}</tbody></table></section><p>Prepared with unofficial Jolt Multi-POV Lab. Original JoltCracker: https://splosh.icu/jolt</p></body></html>`;
    download('jolt-multipov-summary.html',html,'text/html');
  }

  $('fileInput').addEventListener('change',event=>{loadFiles([...event.target.files]);event.target.value='';});
  const drop=$('dropZone');
  drop.addEventListener('dragover',e=>{e.preventDefault();drop.classList.add('drag');});
  drop.addEventListener('dragleave',()=>drop.classList.remove('drag'));
  drop.addEventListener('drop',e=>{e.preventDefault();drop.classList.remove('drag');loadFiles([...e.dataTransfer.files].filter(f=>/\.json$/i.test(f.name)));});
  $('masterSelect').addEventListener('change',e=>{state.masterId=e.target.value;state.selected=null;state.cursor=findRec(state.masterId)?.span[0]||0;renderAll();});
  $('anchorForm').addEventListener('submit',onAnchorSubmit);
  $('downloadCase').addEventListener('click',exportCase);
  $('exportCSV').addEventListener('click',()=>download('jolt-multipov-events.csv',C.exportBreakCSV(state.recordings,state.masterId,state.anchors),'text/csv'));
  $('exportHTML').addEventListener('click',exportReport);
  $('scrub').addEventListener('input',e=>setCursor(Number(e.target.value),true));
  for(const side of ['A','B']){
    $('select'+side).addEventListener('change',()=>{updatePlayer(side);seekBoth();});
    $('videoInput'+side).addEventListener('change',e=>{attachVideo(side,e.target.files[0]);e.target.value='';});
    $('video'+side).addEventListener('loadedmetadata',seekBoth);
  }
  $('videoA').addEventListener('timeupdate',()=>{
    const left=$('videoA'),a=mappings()[$('selectA').value];
    if(a?.aligned && !left.seeking){setCursor(a.forward(left.currentTime));syncPlayback();}
  });
  $('seekBoth').addEventListener('click',seekBoth);
  $('pauseBoth').addEventListener('click',()=>{for(const side of ['A','B'])$('video'+side).pause();});
  $('playBoth').addEventListener('click',async()=>{
    seekBoth();for(const side of ['A','B']){const video=$('video'+side);if(video.src && mappings()[$('select'+side).value]?.aligned)try{await video.play();}catch(_){warning('The browser refused playback. Use each player’s Play button.');}}
  });
  setInterval(syncPlayback,400);
  renderAll();
})();
