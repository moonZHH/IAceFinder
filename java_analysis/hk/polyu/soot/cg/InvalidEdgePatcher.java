package hk.polyu.soot.cg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import soot.Scene;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class InvalidEdgePatcher {
	
	public static void init() {}
	
	public static void patchPost() {
		CallGraph cg = Scene.v().getCallGraph();
		
		HashSet<Edge> removes = new HashSet<Edge>();
		
		// case-1
		
		// 1. collect the source methods from the callgraph
		HashSet<SootMethod> srcs = new HashSet<SootMethod>();
		Iterator<Edge> edgeIter = cg.iterator();
		while (edgeIter.hasNext()) {
			Edge edge = edgeIter.next();
			if ((edge.srcStmt() != null) && (edge.srcStmt().toString().contains(".permission.")))
				continue;
			
			SootMethod srcSM = edge.src();
			if (srcSM.getName().contains("permission") || srcSM.getName().contains("Permission"))
				continue;
			
			srcs.add(srcSM);
		}
		
		// 2. analyze each source method
		for (SootMethod srcSM : srcs) {
			HashMap<String, HashSet<Edge>> callees = new HashMap<String, HashSet<Edge>>();
			
			Iterator<Edge> outIter = cg.edgesOutOf(srcSM);
			while (outIter.hasNext()) {
				Edge outEdge = outIter.next();
				if ((outEdge.srcStmt() != null) && (outEdge.srcStmt().toString().contains(".permission.")))
					continue;
				
				SootMethod tgtSM = outEdge.tgt();
				if (tgtSM.getName().contains("permission") || tgtSM.getName().contains("Permission"))
					continue;
				
				if (!callees.containsKey(tgtSM.getName())) {
					callees.put(tgtSM.getName(), new HashSet<Edge>());
				}
				callees.get(tgtSM.getName()).add(outEdge);
			}
			
			for (String tgtName : callees.keySet()) {
				HashSet<Edge> outEdges = callees.get(tgtName);
				if (outEdges.size() > 1) {
					removes.addAll(outEdges);
				}
			}
		}
		
		for (Edge remove : removes) {
			// System.out.println("[Remove] " + remove);
			cg.removeEdge(remove);
		}
		
		Scene.v().setCallGraph(cg);
	}

}
