package hk.polyu.soot.cg;

import java.util.HashSet;
import java.util.Iterator;

import soot.Scene;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class ParcelPatcher {
	
	public static void init() {}
	
	public static void patchPost() {
		CallGraph cg = Scene.v().getCallGraph();
		
		HashSet<Edge> removes = new HashSet<Edge>();
		
		Iterator<Edge> edgeIter = cg.iterator();
		while (edgeIter.hasNext()) {
			Edge edge = edgeIter.next();
			SootMethod tgtSM = edge.tgt();
			// case-1
			if (tgtSM.getDeclaringClass().getName().equals("android.os.Parcel")) {
				// System.out.println(tgtSM.getSignature());
				removes.add(edge);
			}
			// case-2
			if (tgtSM.getName().equals("readFromParcel")) {
				// System.out.println(tgtSM.getSignature());
				removes.add(edge);
			}
			// case-3
			if (tgtSM.getName().equals("writeToParcel")) {
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
