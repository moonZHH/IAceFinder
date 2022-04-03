package hk.polyu.soot.cg;

import java.util.HashSet;
import java.util.Iterator;

import soot.Body;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class EdgePatcher {
	
	public static void init() {}
	
	public static void patchPost() {
		CallGraph cg = Scene.v().getCallGraph();
		
		// 1. collect source SootMethods from callgraph
		HashSet<SootMethod> srcSMs = new HashSet<SootMethod>();
		
		Iterator<Edge> edgeIter = cg.iterator();
		while (edgeIter.hasNext()) {
			Edge edge = edgeIter.next();
			SootMethod srcSM = edge.src();
			srcSMs.add(srcSM);
		}
		
		// 2. heuristically add the missing callgraph edges
		for (SootMethod srcSM : srcSMs) {
			if (!srcSM.isConcrete())
				continue;
			if (!cg.edgesInto(srcSM).hasNext())
				continue;
			
			Body body = srcSM.retrieveActiveBody();
			for (Unit unit : body.getUnits()) {
				Stmt stmt = (Stmt) unit;
				if (!stmt.containsInvokeExpr())
					continue;
				
				if (cg.edgesOutOf(unit).hasNext())
					continue;
				
				SootMethod src = srcSM;
				SootMethod tgt = stmt.getInvokeExpr().getMethod();
				if (tgt.getName().equals("<init>") || tgt.getName().equals("<clinit>"))
					continue;
				Edge newEdge = new Edge(src, stmt, tgt);
				// System.out.println("[Add] " + newEdge);
				cg.addEdge(newEdge);
			}
		}
		
		Scene.v().setCallGraph(cg);
	}

}
