package hk.polyu.soot.cg;

import java.util.HashSet;
import java.util.Iterator;

import soot.Scene;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class RunPatcher {
	
	public static void init() {}
	
	public static void patchPost() {
		CallGraph cg = Scene.v().getCallGraph();
		
		HashSet<Edge> removes = new HashSet<Edge>();
		
		// 1. collect source methods from the callgraph
		HashSet<SootMethod> srcs = new HashSet<SootMethod>();
		Iterator<Edge> edgeIter = cg.iterator();
		while (edgeIter.hasNext()) {
			Edge edge = edgeIter.next();
			SootMethod srcSM = edge.src();
			srcs.add(srcSM);
		}
		
		// 2. analyze the callee methods of each source method
		for (SootMethod srcSM : srcs) {
			HashSet<Edge> edges = new HashSet<Edge>(); // store the edges that call the "run" method
			
			Iterator<Edge> outIter = cg.edgesOutOf(srcSM);
			while (outIter.hasNext()) {
				Edge outEdge = outIter.next();
				SootMethod tgtSM = outEdge.tgt();
				if (tgtSM.getName().equals("run")) {
					edges.add(outEdge);
				}
			}
			
			if (edges.size() > 1) {
				removes.addAll(edges);
			}
		}
		
		for (Edge remove : removes) {
			cg.removeEdge(remove);
		}
		
		Scene.v().setCallGraph(cg);
	}

}
