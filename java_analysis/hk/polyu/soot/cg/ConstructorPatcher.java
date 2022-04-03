package hk.polyu.soot.cg;

import java.util.HashSet;
import java.util.Iterator;

import soot.Scene;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class ConstructorPatcher {
	
	public static void init() {}
	
	public static void patchPost() {
		CallGraph cg = Scene.v().getCallGraph();
		
		HashSet<Edge> removes = new HashSet<Edge>();
		
		Iterator<Edge> edgeIter = cg.iterator();
		while (edgeIter.hasNext()) {
			Edge edge = edgeIter.next();
			SootMethod tgtSM = edge.tgt();
			if (tgtSM.getName().equals("<init>")) {
				// System.out.println(tgt.getSignature());
				removes.add(edge);
				continue;
			}
			if (tgtSM.getName().equals("<clinit>")) {
				// System.out.println(tgt.getSignature());
				removes.add(edge);
				continue;
			}
		}
		
		for (Edge remove : removes) {
			cg.removeEdge(remove);
		}
		
		Scene.v().setCallGraph(cg);
	}

}
