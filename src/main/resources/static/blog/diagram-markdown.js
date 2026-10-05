// Reader-facing filtering only. Keep the original source in workflow data for editing/review.
export function withoutDiagramSource(markdown) {
  const lines=markdown.replace(/\r\n/g,'\n').split('\n'), result=[];
  const languages=new Set(['dot','graphviz','mermaid','plantuml','puml']);
  for(let i=0;i<lines.length;i++) {
    const opening=lines[i].match(/^ {0,3}(`{3,}|~{3,})[ \t]*(.*)$/);
    if(!opening) {result.push(lines[i]);continue;}
    const fence=opening[1], language=opening[2].trim().toLowerCase().split(/\s+/)[0];
    const closing=new RegExp('^ {0,3}'+fence[0]+'{'+fence.length+',}[ \\t]*$');
    let end=i+1;
    while(end<lines.length && !closing.test(lines[end]))end++;
    const body=lines.slice(i+1,end).join('\n').trimStart();
    const diagram=languages.has(language) || (!language && /^(?:(?:strict\s+)?(?:digraph|graph)\b|@startuml\b|flowchart\s|sequenceDiagram\b)/.test(body));
    if(!diagram)result.push(...lines.slice(i,Math.min(end+1,lines.length)));
    i=end;
  }
  return result.join('\n');
}
