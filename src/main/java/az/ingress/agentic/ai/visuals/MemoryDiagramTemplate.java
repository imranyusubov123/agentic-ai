package az.ingress.ai_exprementation.visuals;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MemoryDiagramTemplate {
    public DiagramRequest build(MemoryDiagram diagram) {
        Objects.requireNonNull(diagram, "diagram is required");
        if(diagram.frames()==null || diagram.objects()==null || diagram.frames().isEmpty()
                || diagram.frames().size()>10 || diagram.objects().size()>20)
            throw new IllegalArgumentException("Supply 1–10 frames and at most 20 objects");
        var targets=new HashMap<String,String>();
        for(var object:diagram.objects()) {
            text(object.id()); text(object.type());
            if(targets.put(object.id(),"object"+targets.size())!=null) throw new IllegalArgumentException("Duplicate object ID");
        }
        var source=new StringBuilder("digraph Memory {\nrankdir=LR; node [shape=box];\n");
        var edges=new StringBuilder();
        source.append("subgraph cluster_stack { label=\"Stack (conceptual frames)\";\n");
        int index=0;
        for(var frame:diagram.frames()) {
            var prefix="frame"+index++;
            source.append("subgraph cluster_").append(prefix).append(" { label=").append(quote(frame.name())).append(";\n");
            variables(source,edges,prefix,frame.variables(),targets); source.append("}\n");
        }
        source.append("}\nsubgraph cluster_heap { label=\"Heap objects\";\n");
        for(var object:diagram.objects()) {
            var id=targets.get(object.id());
            source.append("subgraph cluster_").append(id).append(" { label=").append(quote(object.type())).append(";\n")
                    .append(id).append(" [label=").append(quote(object.id())).append("];\n");
            variables(source,edges,id,object.fields(),targets);source.append("}\n");
        }
        return new DiagramRequest(DiagramEngine.GRAPHVIZ,source.append("}\n").append(edges).append("}\n").toString());
    }
    private void variables(StringBuilder source,StringBuilder edges,String prefix,List<MemoryDiagram.Variable> vars,Map<String,String> targets) {
        if(vars==null || vars.size()>20)throw new IllegalArgumentException("Supply at most 20 variables per frame/object");
        var names=new HashSet<String>();int i=0;
        for(var variable:vars) {
            if(!names.add(text(variable.name())))throw new IllegalArgumentException("Duplicate variable name");
            var id=prefix+"v"+i++;
            boolean reference=variable.targetObjectId()!=null && !variable.targetObjectId().isBlank();
            if(reference && variable.value()!=null && !variable.value().isBlank())throw new IllegalArgumentException("Supply a value OR targetObjectId");
            source.append(id).append(" [label=").append(quote(variable.name()+(reference?" (reference)":" = "+text(variable.value())))).append("];\n");
            if(reference) {
                String target=targets.get(variable.targetObjectId());
                if(target==null)throw new IllegalArgumentException("Reference points to an unknown object ID");
                edges.append(id).append(" -> ").append(target).append(";\n");
            }
        }
    }
    private static String text(String value) {
        if(value==null || value.isBlank() || value.length()>200)throw new IllegalArgumentException("Labels must contain 1–200 characters");
        return value;
    }
    private static String quote(String value) { return "\""+text(value).replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","")+"\""; }
}
