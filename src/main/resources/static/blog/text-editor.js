/** Small Markdown editing toolkit; article storage stays plain Markdown. */
(function (root) {
  function format(value, start, end, action) {
    const selection=value.slice(start,end);
    let from=start,to=end,text,selectStart,selectEnd;
    const wrap=(left,right,placeholder)=>{const content=selection || placeholder;text=left+content+right;selectStart=from+left.length;selectEnd=selectStart+content.length;};
    if(action==='bold')wrap('**','**','bold text');
    else if(action==='italic')wrap('*','*','italic text');
    else if(action==='inline-code')wrap('`','`','code');
    else if(action==='link') {wrap('[','](https://example.com)','link text');selectStart=from+text.indexOf('https://');selectEnd=from+text.length-1;}
    else if(['h2','h3','bullet','numbered','quote'].includes(action)) {
      from=value.lastIndexOf('\n',Math.max(0,start-1))+1;if(start===0)from=0;
      const last=end>start && value[end-1]==='\n'?end-1:end;
      const next=value.indexOf('\n',last);to=next<0?value.length:next;
      const lines=value.slice(from,to).split('\n');
      text=lines.map((line,index)=>{
        if(action==='h2'||action==='h3')return '#'.repeat(action==='h2'?2:3)+' '+line.replace(/^#{1,6}\s+/,'');
        if(action==='bullet')return '- '+line.replace(/^\s*(?:[-*+] |\d+\. )/,'');
        if(action==='numbered')return (index+1)+'. '+line.replace(/^\s*(?:[-*+] |\d+\. )/,'');
        return '> '+line.replace(/^>\s?/,'');
      }).join('\n');selectStart=from;selectEnd=from+text.length;
    } else {
      const snippets={code:'```java\n'+(selection || '// Your code here')+'\n```',table:'| Column 1 | Column 2 |\n| --- | --- |\n| Value | Value |',divider:'---'};
      if(!(action in snippets))throw new Error('Unknown editor action');
      const prefix=from>0?'\n\n':'',suffix=to<value.length?'\n\n':'';
      text=prefix+snippets[action]+suffix;selectStart=from+prefix.length;selectEnd=selectStart+snippets[action].length;
    }
    const result=value.slice(0,from)+text+value.slice(to);
    if(result.length>200000)throw new Error('The article has reached the 200,000 character limit.');
    return {value:result,start:selectStart,end:selectEnd};
  }
  root.BlogTextEditor={format};
  if(typeof document==='undefined')return;
  const area=document.getElementById('manual-markdown'), toolbar=document.getElementById('text-tools');
  const preview=document.getElementById('text-preview'), message=document.getElementById('editor-feedback');
  let past=[],future=[],last=null,revision=0,previewing=false,libraries;
  const snapshot=()=>({value:area.value,start:area.selectionStart,end:area.selectionEnd});
  function counts(){const text=area.value.trim();document.getElementById('text-count').textContent=`${text?text.split(/\s+/).length:0} words · ${area.value.length} characters`;}
  function record(){const now=snapshot();if(last && last.value!==now.value){past.push(last);if(past.length>100)past.shift();future=[];}last=now;counts();revision++;}
  function apply(state){area.value=state.value;area.focus();area.setSelectionRange(state.start,state.end);last=state;counts();revision++;}
  function act(action){
    if(area.disabled || previewing)return;
    message.textContent='';
    if(action==='undo'){if(past.length){future.push(snapshot());apply(past.pop());}return;}
    if(action==='redo'){if(future.length){past.push(snapshot());apply(future.pop());}return;}
    try{const before=snapshot(),after=format(before.value,before.start,before.end,action);past.push(before);if(past.length>100)past.shift();future=[];apply(after);}
    catch(error){message.textContent=error.message;}
  }
  toolbar.addEventListener('mousedown',event=>{if(event.target.closest('button'))event.preventDefault();});
  toolbar.addEventListener('click',event=>{const button=event.target.closest('[data-format]');if(button)act(button.dataset.format);});
  area.addEventListener('input',record);
  area.addEventListener('keydown',event=>{
    if(!(event.ctrlKey||event.metaKey)||event.altKey)return;
    const key=event.key.toLowerCase();const action={b:'bold',i:'italic',k:'link',z:event.shiftKey?'redo':'undo',y:'redo'}[key];
    if(action){event.preventDefault();act(action);}
  });
  const toggle=document.getElementById('text-preview-toggle');
  toggle.onclick=async()=>{
    if(area.disabled)return;
    previewing=!previewing;area.hidden=previewing;preview.hidden=!previewing;
    toggle.textContent=previewing?'Back to editing':'Preview formatting';toggle.setAttribute('aria-pressed',String(previewing));
    if(!previewing){revision++;area.focus();return;}
    const token=++revision;preview.textContent='Loading preview…';
    try {
      libraries ??= Promise.all([import('https://cdn.jsdelivr.net/npm/marked@15.0.12/lib/marked.esm.js'),import('https://cdn.jsdelivr.net/npm/dompurify@3.2.6/dist/purify.es.mjs'),import('/blog/diagram-markdown.js')]);
      const [marked,purify,filter]=await Promise.race([libraries,new Promise((_,reject)=>setTimeout(()=>reject(new Error('Preview could not load. You can continue editing.')),12000))]);
      if(token!==revision)return;
      preview.innerHTML=purify.default.sanitize(marked.marked.parse(filter.withoutDiagramSource(area.value)),{USE_PROFILES:{html:true},FORBID_TAGS:['img','iframe','form','input','button','style'],FORBID_ATTR:['style']});
    }catch(error){if(token===revision)preview.textContent=error.message;libraries=null;}
  };
  root.BlogTextEditor.reset=()=>{past=[];future=[];last=snapshot();previewing=false;revision++;area.hidden=false;preview.hidden=true;preview.replaceChildren();toggle.textContent='Preview formatting';toggle.setAttribute('aria-pressed','false');message.textContent='';counts();};
})(globalThis);
