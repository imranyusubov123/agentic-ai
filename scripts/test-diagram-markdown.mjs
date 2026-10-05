import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const source=readFileSync(new URL('../src/main/resources/static/blog/diagram-markdown.js',import.meta.url),'utf8');
const {withoutDiagramSource}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
assert.equal(withoutDiagramSource('Before\n```dot\ndigraph MethodCall { first -> box }\n```\nAfter\n```java\nint amount = 1;\n```'), 'Before\nAfter\n```java\nint amount = 1;\n```');
for(const language of ['dot','graphviz','mermaid','plantuml','puml','']) {
  assert.equal(withoutDiagramSource('Before\n~~~~'+language+'\ndigraph G {a -> b}\n~~~~~\nAfter'),'Before\nAfter');
}
assert.equal(withoutDiagramSource('```dot\ndigraph G {}'),'');
const java='````java\nString s = "digraph G {}";\n// ```dot\n````';
assert.equal(withoutDiagramSource(java),java);
console.log('Reader filtering checks passed; diagram source removed, ordinary code preserved.');
