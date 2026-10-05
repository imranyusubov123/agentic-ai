"""Smoke-test a running visuals-profile app + Kroki. No LLM/provider calls."""
import json, sys, urllib.request
base = sys.argv[1] if len(sys.argv)>1 else 'http://127.0.0.1:8083'
headers={'Content-Type':'application/json','Accept':'application/json, text/event-stream'}
sequence=0
def call(method, params=None, notification=False):
    global sequence
    sequence+=1
    data={'jsonrpc':'2.0','method':method}
    if not notification:data['id']=sequence
    if params is not None:data['params']=params
    request=urllib.request.Request(base+'/mcp',json.dumps(data).encode(),headers)
    with urllib.request.urlopen(request, timeout=60) as response:
        if response.headers.get('Mcp-Session-Id'):headers['Mcp-Session-Id']=response.headers['Mcp-Session-Id']
        if notification:return
        if 'text/event-stream' in response.headers.get('Content-Type',''):
            for line in response:
                if line.startswith(b'data:'):
                    result=json.loads(line[5:])
                    if result.get('id')==sequence:break
        else:result=json.load(response)
        if 'error' in result:raise RuntimeError(result['error'])
        return result['result']
initial=call('initialize',{'protocolVersion':'2025-03-26','capabilities':{},'clientInfo':{'name':'visuals-smoke-test','version':'1.0'}})
headers['MCP-Protocol-Version']=initial['protocolVersion']
call('notifications/initialized',notification=True)
names={t['name'] for t in call('tools/list')['tools']}
expected={'render_graph','render_uml','render_flowchart','render_memory_diagram'}
assert names==expected,names
print('Discovered:',', '.join(sorted(names)))
cases=[('render_graph',{'source':'digraph G { Variable -> Object }'}),
       ('render_uml',{'source':'@startuml\nClient -> Service: request\nService --> Client: response\n@enduml'}),
       ('render_flowchart',{'source':'flowchart LR\n A[Request] --> B[Response]'}),
       ('render_memory_diagram',{'diagram':{'frames':[{'name':'main()','variables':[{'name':'person','value':None,'targetObjectId':'person1'},{'name':'count','value':'1','targetObjectId':None}]}], 'objects':[{'id':'person1','type':'Person','fields':[{'name':'age','value':'27','targetObjectId':None}]}]}})]
for name,arguments in cases:
    result=call('tools/call',{'name':name,'arguments':arguments})
    assert not result.get('isError'),result
    asset=json.loads(next(c['text'] for c in result['content'] if c['type']=='text'))
    with urllib.request.urlopen(base+asset['url']) as response:
        assert response.headers['Content-Type']=='image/png'
        assert response.read(8)==b'\x89PNG\r\n\x1a\n'
    print(name,base+asset['url'])
invalid=call('tools/call',{'name':'render_graph','arguments':{'source':'digraph G {a [image="/etc/passwd"]}'}})
assert invalid.get('isError'),invalid
print('Unsafe source rejected. MCP discovery, all four renders, and asset fetches passed.')
