package hk.polyu.soot.cg;

import java.util.HashSet;
import java.util.Iterator;

import soot.Scene;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class HandlerPatcher {
	
	public static void init() {}
	
	public static void patchPost() {
		CallGraph cg = Scene.v().getCallGraph();
		
		HashSet<Edge> removes = new HashSet<Edge>();
		
		Iterator<Edge> edgeIter = cg.iterator();
		while (edgeIter.hasNext()) {
			Edge edge = edgeIter.next();
			SootMethod tgtSM = edge.tgt();
			if (tgtSM.getDeclaringClass().getName().equals("android.os.Handler")) {
				// System.out.println(tgtSM.getSignature());
				removes.add(edge);
			}
		}
		
		for (Edge remove : removes) {
			cg.removeEdge(remove);
		}
		
		Scene.v().setCallGraph(cg);
	}

}
