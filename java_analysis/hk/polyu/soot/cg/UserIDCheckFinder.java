package hk.polyu.soot.cg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import soot.Body;
import soot.Local;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.EqExpr;
import soot.jimple.IfStmt;
import soot.jimple.NeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.toolkits.scalar.SimpleLocalUses;
import soot.toolkits.scalar.UnitValueBoxPair;

public class UserIDCheckFinder {
	
	// find the UID check methods in CallGraph
	public static HashSet<SootMethod> useridCheckMethodCGSet = new HashSet<SootMethod>();
	public static HashMap<SootMethod, HashSet<Stmt>> useridCheckMethod2Stmt = new HashMap<SootMethod, HashSet<Stmt>>();
	
	public static void find(CallGraph cg) {
		SootMethod getUserIDSM = Scene.v().getMethod("<android.os.UserHandle: int getCallingUserId()>");
		assert !getUserIDSM.isPhantom();
		
		HashSet<SootMethod> potentialSMs = new HashSet<SootMethod>();
		
		Iterator<Edge> edgeIter = cg.edgesInto(getUserIDSM);
		while (edgeIter.hasNext()) {
			Edge edge = edgeIter.next();
			SootMethod srcSM = edge.src();
			potentialSMs.add(srcSM);
		}
		
		for (SootMethod sm : potentialSMs) {
			if (!sm.isConcrete())
				continue;
			
			boolean isUserIDCheckMethod = false;
			
			Body body = sm.retrieveActiveBody();
			for (Unit unit : body.getUnits()) {
				Stmt stmt = (Stmt) unit;
				if (!stmt.containsInvokeExpr())
					continue;
				if (!(stmt instanceof AssignStmt))
					continue;
				
				SootMethod callee = stmt.getInvokeExpr().getMethod();
				if (!callee.getName().equals("getCallingUserId"))
					continue;
				
				Local useridLocal = (Local) ((AssignStmt) stmt).getLeftOp();
				List<Unit> useUnits = findUses(body, stmt, useridLocal);
				for (Unit useUnit : useUnits) {
					Stmt useStmt = (Stmt) useUnit;
					if (!(useStmt instanceof IfStmt))
						continue;
					
					Value ifCondition = ((IfStmt) useStmt).getCondition();
					if (ifCondition instanceof EqExpr) {
						Value op1 = ((EqExpr) ifCondition).getOp1();
						Value op2 = ((EqExpr) ifCondition).getOp2();
						if (op1 == useridLocal || op2 == useridLocal) {
							isUserIDCheckMethod = true;
							
							if (!useridCheckMethod2Stmt.containsKey(sm))
								useridCheckMethod2Stmt.put(sm, new HashSet<Stmt>());
							useridCheckMethod2Stmt.get(sm).add(useStmt);
						}
					} else if (ifCondition instanceof NeExpr) {
						Value op1 = ((NeExpr) ifCondition).getOp1();
						Value op2 = ((NeExpr) ifCondition).getOp2();
						if (op1 == useridLocal || op2 == useridLocal) {
							isUserIDCheckMethod = true;
							
							if (!useridCheckMethod2Stmt.containsKey(sm))
								useridCheckMethod2Stmt.put(sm, new HashSet<Stmt>());
							useridCheckMethod2Stmt.get(sm).add(useStmt);
						}
					} else {
						// pass
					}
				}
			}
			
			if (isUserIDCheckMethod == true) {
				// System.out.println("[INFO] [CG-UIDChecker]: " + sm.getSignature()); // debug
				useridCheckMethodCGSet.add(sm);
			}
		}
		
		System.out.println(" -->> Find UserID Check Methods in Callgraph Finish");
	}
	
	// ---- //
	
	/*
	private static List<Unit> findDefs(Body body, Stmt stmt, Local local) {
		Unit unit = (Unit) stmt;
		UnitGraph cfg = new BriefUnitGraph(body);
		SimpleLocalDefs defsResolver = new SimpleLocalDefs(cfg);
		List<Unit> defs = defsResolver.getDefsOfAt(local, unit);
		
		return defs;
	}
	*/
		
	private static List<Unit> findUses(Body body, Stmt stmt, Local local) {
		Unit unit = (Unit) stmt;
		UnitGraph cfg = new BriefUnitGraph(body);
		SimpleLocalDefs defsResolver = new SimpleLocalDefs(cfg);
		SimpleLocalUses usesResolver = new SimpleLocalUses(cfg, defsResolver);
		
		List<Unit> uses = new ArrayList<Unit>();
		List<Unit> defs = defsResolver.getDefsOfAt(local, unit);
		for (Unit defUnit : defs) {
			List<UnitValueBoxPair> pairs = usesResolver.getUsesOf(defUnit);
			for (UnitValueBoxPair pair : pairs) {
				uses.add(pair.unit);
			}
		}
		
		return uses;
	}

}
