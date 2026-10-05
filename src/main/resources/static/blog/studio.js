'use strict';
const api = '/api/blog/workflows';
const $ = id => document.getElementById(id);
let current = null, entries = [], busy = false;
const statuses = {CREATED:'Created', RESEARCHING:'Researching', WRITING:'Writing', REVISING:'Revising', PLANNING_VISUALS:'Planning visuals', REVIEWING:'Technical review', AWAITING_HUMAN_APPROVAL:'Awaiting your review', FAILED:'Needs attention'};
const label = value => statuses[value] || value.replaceAll('_', ' ').toLowerCase();
const date = value => new Date(value).toLocaleString();
function node(tag, text, className) { const el = document.createElement(tag); if (text != null) el.textContent = text; if (className) el.className = className; return el; }
function notice(text, error = false) { $('notice').hidden = !text; $('notice').textContent = text; $('notice').className = error ? 'error' : ''; }
async function request(path = '', method = 'GET', body) {
  let response;
  try { response = await fetch(api + path, {method, headers: body ? {'Content-Type':'application/json'} : {}, body:body ? JSON.stringify(body) : undefined}); }
  catch { throw new Error('Cannot reach the server. If an operation was running, it may still finish. Refresh before retrying.'); }
  if (!response.ok) {
    const messages = {405:'The running server needs a restart to load the new editing endpoints. Export your articles first; workflow storage is still in memory.',400:'Check the form values and try again.',404:'This article is no longer available. Restarting the server clears saved workflows.',409:'This article changed or is not ready for editing. Keep a copy of your edits, close the editor and refresh before trying again.',422:'Diagram could not render. Check its syntax and Kroki availability. Your previous image is unchanged.'};
    throw new Error(messages[response.status] || `The server returned an error (${response.status}). Refresh to check whether your operation completed.`);
  }
  return response.json();
}
function setBusy(value) {
  busy = value;
  document.querySelectorAll('button, input, textarea, select').forEach(el => { el.disabled = value; });
  $('main').setAttribute('aria-busy', String(value));
  if (!value) {
    const editable = current?.draft && current?.research && ['AWAITING_HUMAN_APPROVAL','FAILED'].includes(current.status);
    document.querySelectorAll('.editor button,.editor textarea,.editor select').forEach(el => { el.disabled = !editable; });
  }
}
async function action(message, work) {
  if (busy) return;
  setBusy(true); notice(message);
  try { await work(); }
  catch (error) { notice(error.message, true); }
  finally { setBusy(false); }
}
function renderList() {
  const list = $('articles'); list.replaceChildren();
  const visible = entries.filter(entry => entry.title.toLowerCase().includes($('search').value.toLowerCase()));
  if (!visible.length) list.append(node('p', entries.length ? 'No matching articles.' : 'Your articles will appear here.', 'muted'));
  for (const entry of visible) {
    const button = node('button', entry.title, 'article-item' + (current?.id === entry.id ? ' active' : ''));
    button.append(node('small', label(entry.status))); button.disabled = busy;
    if (current?.id === entry.id) button.setAttribute('aria-current','page');
    button.onclick = () => action('Opening article…', async () => { show(await request('/' + entry.id)); notice(''); });
    list.append(button);
  }
}
async function refreshList() { entries = await request(); renderList(); }
function show(workflow) {
  if (current?.id !== workflow.id) { $('instructions').value=''; $('visual-instructions').value=''; }
  current = workflow; history.replaceState(null, '', '#' + workflow.id);
  $('create').hidden = true; $('workspace').hidden = false;
  $('article-title').textContent = workflow.draft?.title || workflow.topic.title;
  $('status').textContent = label(workflow.status);
  $('updated').textContent = 'Updated ' + date(workflow.updatedAt);
  $('request-id').textContent = 'Request ' + workflow.id;
  $('failure').hidden = !workflow.error;
  $('failure').textContent = workflow.error ? `${workflow.failedStage ? label(workflow.failedStage) + ': ' : ''}${workflow.error}` : '';
  const base = api + '/' + workflow.id;
  $('preview').hidden = !workflow.draft; $('no-draft').hidden = !!workflow.draft;
  for (const id of ['download','preview-link']) $(id).hidden = !workflow.draft;
  if (workflow.draft) {
    $('preview').src = base + '/preview?edit=1&v=' + encodeURIComponent(workflow.updatedAt);
    $('preview-link').href = base + '/preview'; $('download').href = base + '/markdown';
  } else $('preview').removeAttribute('src');
  renderResearch(workflow.research); renderReview(workflow.review); renderVisuals(workflow.layout);
  $('history').replaceChildren(node('h2','Workflow activity'));
  for (const event of workflow.history) $('history').append(node('p', `${date(event.at)} — ${label(event.status)}`));
  renderList();
}
function renderResearch(research) {
  const box = $('research'); box.replaceChildren(node('h2', 'Research & sources'));
  if (!research) { box.append(node('p','Research is not available yet.')); return; }
  box.append(node('p', research.summary), node('h3','Key concepts'));
  const concepts = node('ul'); for (const concept of research.keyConcepts) concepts.append(node('li',concept)); box.append(concepts);
  box.append(node('h3','Sources'), node('p','These sources are model-suggested and have not been verified by web browsing.', 'muted'));
  for (const source of research.sources) {
    const item = node('p');
    let url; try { url = new URL(source.url); } catch { /* Render invalid URLs as text. */ }
    const link = node(url && ['http:','https:'].includes(url.protocol) ? 'a' : 'span', source.title);
    if (link.tagName === 'A') { link.href = url.href; link.target='_blank'; link.rel='noopener noreferrer'; }
    item.append(link, node('br'), node('span', source.relevance)); box.append(item);
  }
  if (research.gaps.length) { box.append(node('h3','Needs verification')); const list=node('ul'); for(const gap of research.gaps) list.append(node('li',gap)); box.append(list); }
}
function renderReview(review) {
  const box = $('review'); box.replaceChildren(node('h2','Technical review'));
  if (!review) { box.append(node('p','A technical review is not available yet.')); return; }
  box.append(node('p',review.summary));
  if (!review.issues.length) box.append(node('p','No issues flagged by the model. Your review is still required.'));
  for (const issue of review.issues) {
    const item = node('div',null,'issue' + (issue.severity === 'BLOCKER' ? ' blocker' : ''));
    item.append(node('strong',`${issue.severity} · ${issue.location}`),node('p',issue.description),node('p',issue.recommendation)); box.append(item);
  }
}
function renderVisuals(layout) {
  const box=$('visuals'); box.replaceChildren();
  if (!layout?.visuals?.length) box.append(node('p','No diagrams in this draft.', 'muted'));
  for (const visual of layout?.visuals || []) {
    const item=node('div',null,'visual'), id='placement-' + visual.spec.visualId;
    item.append(node('strong',visual.spec.caption),node('p',`Version ${visual.version} · ${label(visual.status)}`));
    const text=node('label','Place after'); text.htmlFor=id; const select=node('select'); select.id=id;
    for (const [index,block] of layout.blocks.entries()) { const option=node('option',`${index+1}. ${block.markdown.slice(0,95)}`); option.value=block.id; select.append(option); }
    select.value=visual.spec.afterBlockId;
    if (select.selectedIndex < 0) { const option=node('option','Choose a paragraph…'); option.value=''; option.disabled=true; option.selected=true; select.prepend(option); }
    const button=node('button','Apply placement & review'); button.onclick=()=>{
      if (!select.value) { notice('Choose a paragraph for this diagram.',true); select.focus(); return; }
      action('Moving the diagram and reviewing the article…',async()=>{
        const result=await request(`/${current.id}/visuals/${visual.spec.visualId}/placement`,'PATCH',{afterBlockId:select.value}); await finish(result,'Diagram placement updated.');
      });
    };
    const edit=node('button','Edit diagram script'); edit.onclick=()=>openManualEditor(visual.spec.visualId);
    item.append(edit,text,select,button);
    if (visual.error) item.append(node('p',visual.error,'error'));
    if (visual.status === 'RENDER_FAILED') {
      const retry=node('button','Retry rendering');
      retry.onclick=()=>action('Retrying this diagram and reviewing the article…',async()=>{
        await finish(await request(`/${current.id}/visuals/${visual.spec.visualId}/retry`,'POST'), 'Visual retry completed. Check its status below.');
      });
      item.append(retry);
    }
    box.append(item);
  }
}
async function finish(result, message) {
  show(result); notice(result.status === 'FAILED' ? result.error : message, result.status === 'FAILED');
  try { await refreshList(); } catch { notice('The article was saved, but the article list could not refresh. Use Refresh to try again.',true); }
}
$('create-form').onsubmit=event=>{event.preventDefault(); const body=Object.fromEntries(new FormData(event.currentTarget));
  action('Researching, writing, planning visuals, and reviewing. This can take several minutes; keep this page open.', async()=>{ await finish(await request('','POST',body),'Draft ready for your review.'); });
};
$('edit-form').onsubmit=event=>{event.preventDefault();action('Updating the draft and running a technical review. This may take a few minutes…',async()=>{
  const result=await request('/'+current.id+'/revisions','POST',{instructions:$('instructions').value}); await finish(result,'Draft updated. Check the review and any diagrams needing attention.'); if(result.status !== 'FAILED') $('instructions').value='';
});};
$('visual-form').onsubmit=event=>{event.preventDefault();action('Planning diagrams and reviewing the article. This may take a few minutes…',async()=>{
  const result=await request('/'+current.id+'/visuals/plan','POST',{instructions:$('visual-instructions').value}); await finish(result,'Visual plan updated.'); if(result.status !== 'FAILED') $('visual-instructions').value='';
});};
$('new').onclick=()=>{current=null;history.replaceState(null,'',location.pathname);$('workspace').hidden=true;$('create').hidden=false;notice('');renderList();$('title').focus();};
$('refresh').onclick=()=>action('Refreshing articles…',async()=>{await refreshList();notice('');});
$('reload').onclick=()=>action('Refreshing article…',async()=>{await finish(await request('/'+current.id),'Article refreshed.');});
$('search').oninput=renderList;
$('copy-id').onclick=async()=>{try{await navigator.clipboard.writeText(current.id);notice('Request ID copied.');}catch{notice('Copy the request ID shown under the article title.',true);}};
$('tabs').onclick=event=>{const button=event.target.closest('button[data-panel]');if(!button)return;document.querySelectorAll('.panel').forEach(panel=>panel.hidden=panel.id!==button.dataset.panel);document.querySelectorAll('[data-panel]').forEach(tab=>tab.setAttribute('aria-pressed',String(tab===button)));};
async function openHash(){
  const id=location.hash.slice(1);if(!id || id === 'main')return;
  if(!/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id))throw new Error('Invalid article ID in the URL. Choose an article from the list.');
  show(await request('/'+id));
}
window.addEventListener('hashchange',()=>{if(!busy)action('Opening article…',async()=>{await openHash();notice('');});});
window.addEventListener('beforeunload',event=>{if(busy){event.preventDefault();event.returnValue='';}});
action('Loading workspace…',async()=>{await refreshList();await openHash();notice('');});

let manualEdit=null;
function openManualEditor(visualId=null) {
  if(busy || !current?.draft)return;
  const visual=visualId ? current.layout.visuals.find(v=>v.spec.visualId===visualId) : null;
  if(visualId && !visual)return;
  manualEdit={id:current.id,expectedUpdatedAt:current.updatedAt,visualId};
  $('manual-heading').textContent=visual?'Edit diagram':'Edit article';
  $('manual-help').textContent=visual?'Change the script and rerender only this diagram. No AI rewrite is performed. The script stays out of the blog.':'Edit the title and Markdown directly. Diagrams remain separate. Changes may require checking their placement.';
  $('manual-save').textContent=visual?'Render & save diagram':'Save content';
  $('manual-error').hidden=true;
  $('content-fields').hidden=!!visual;$('diagram-fields').hidden=!visual;
  $('manual-title').value=current.draft.title;$('manual-markdown').value=current.draft.markdown;
  window.BlogTextEditor?.reset();
  if(visual){$('manual-engine').value=visual.spec.engine || 'MERMAID';$('manual-source').value=visual.spec.source || visual.spec.mermaid || ''; $('manual-caption').value=visual.spec.caption;$('manual-alt').value=visual.spec.altText;}
  $('manual-editor').showModal();
  $(visual?'manual-source':'manual-markdown').focus();
}
$('edit-content').onclick=()=>openManualEditor();
$('manual-close').onclick=()=>{if(!busy)$('manual-editor').close();};
$('manual-editor').addEventListener('cancel',event=>{if(busy)event.preventDefault();});
window.addEventListener('message',event=>{
  if(event.origin!==location.origin || event.source!==$('preview').contentWindow || event.data?.type!=='edit-blog-diagram' || event.data.workflowId!==current?.id)return;
  openManualEditor(event.data.visualId);
});
$('manual-form').onsubmit=async event=>{
  event.preventDefault();if(busy || !manualEdit)return;
  const edit=manualEdit;
  const body=edit.visualId ? {engine:$('manual-engine').value,source:$('manual-source').value,caption:$('manual-caption').value,altText:$('manual-alt').value,expectedUpdatedAt:edit.expectedUpdatedAt}
    : {title:$('manual-title').value,markdown:$('manual-markdown').value,expectedUpdatedAt:edit.expectedUpdatedAt};
  if(Object.values(body).some(value=>typeof value==='string' && !value.trim())){$('manual-error').hidden=false;$('manual-error').textContent='Complete all fields before saving.';return;}
  setBusy(true);$('manual-error').hidden=true;$('manual-save').textContent=edit.visualId?'Rendering…':'Saving…';
  try {
    const result=await request('/'+edit.id+(edit.visualId?'/visuals/'+edit.visualId:'/content'),'PATCH',body);
    $('manual-editor').close();await finish(result,edit.visualId?'Diagram updated. Article text and other diagrams are unchanged.':'Content saved. Review any diagrams whose paragraphs changed.');
  } catch(error){$('manual-error').textContent=error.message;$('manual-error').hidden=false;}
  finally{setBusy(false);$('manual-save').textContent=edit.visualId?'Render & save diagram':'Save content';}
};
