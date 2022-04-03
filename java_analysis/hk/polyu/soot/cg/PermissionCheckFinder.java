package hk.polyu.soot.cg;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import soot.Body;
import soot.Local;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

public class PermissionCheckFinder {
	
	// find the permission check methods in CallGraph
	public static HashSet<SootMethod> permissionCheckMethodCGSet = new HashSet<SootMethod>();
	
	public static void find(CallGraph cg) {
		// case-1
		HashSet<SootMethod> cgSMs = new HashSet<SootMethod>();
		for (Edge edge : cg) {
			SootMethod src = edge.src();
			if (src.isConcrete())
				cgSMs.add(src);
			SootMethod tgt = edge.tgt();
			if (tgt.isConcrete())
				cgSMs.add(tgt);
		}
		
		for (SootMethod sm : cgSMs) {
			boolean isPermissionCheckMethod = false;
			
			if (!sm.isConcrete())
				continue;
			if (!sm.getReturnType().toString().equals("int"))
				continue;
			
			Body body = sm.retrieveActiveBody();
			BriefUnitGraph cfg = new BriefUnitGraph(body);
			SimpleLocalDefs localDefs = new SimpleLocalDefs(cfg);
			
			for (Unit tail : cfg.getTails()) {
				if (!(tail instanceof ReturnStmt))
					continue;
				
				Value retValue = ((ReturnStmt) tail).getOp();
				if (!(retValue instanceof Local))
					continue;
				
				Local retLocal = (Local) retValue;
				for (Unit defRetLocalUnit : localDefs.getDefsOf(retLocal)) {
					Stmt defRetLocalStmt = (Stmt) defRetLocalUnit;
					if (!(defRetLocalStmt instanceof AssignStmt))
						continue;
					if (!defRetLocalStmt.containsInvokeExpr())
						continue;
					if (!defRetLocalStmt.getInvokeExpr().getMethod().getSignature().equals("<java.lang.Integer: int intValue()>"))
						continue;
					
					Value baseValue = ((VirtualInvokeExpr) defRetLocalStmt.getInvokeExpr()).getBase();
					if (!(baseValue instanceof Local))
						continue;
					
					Local baseLocal = (Local) baseValue;
					for (Unit defBaseLocalUnit : localDefs.getDefsOf(baseLocal)) {
						if (!(defBaseLocalUnit instanceof AssignStmt))
							continue;
						Value rightValue = ((AssignStmt) defBaseLocalUnit).getRightOp();
						if (!(rightValue instanceof FieldRef))
							continue;
						
						String fieldName = ((FieldRef) rightValue).getField().getName();
						if ((fieldName.startsWith("PERMISSION_") && fieldName.endsWith("_GRANTED"))
						 || (fieldName.startsWith("PERMISSION_") && fieldName.endsWith("_DENIED"))) {
							isPermissionCheckMethod = true;
							break;
						}
					}
				}
			}
			
			if (isPermissionCheckMethod) {
				// System.out.println("[INFO] [CG-PermissionChecker][case-1]: " + sm.getSignature()); // debug
				permissionCheckMethodCGSet.add(sm);
			}
		}
		
		// patch
		HashSet<SootMethod> extraSMs = new HashSet<SootMethod>();
		for (SootMethod sm : permissionCheckMethodCGSet) {
			String className = sm.getDeclaringClass().getName();
			String methodName = sm.getName();
			if (className.equals("com.android.server.pm.permission.PermissionManagerService")) {
				SootClass tgtSC = Scene.v().getSootClass("android.permission.PermissionManager");
				for (SootMethod tgtSM : tgtSC.getMethods()) {
					if (tgtSM.getName().equals(methodName))
						extraSMs.add(tgtSM);
				}
			}
		}
		permissionCheckMethodCGSet.addAll(extraSMs);
		
		// case-2
		HashSet<SootMethod> potentialSMs = new HashSet<SootMethod>();
		for (Edge edge : cg) {
			SootMethod callee = edge.tgt();
			if (callee.getDeclaringClass().toString().startsWith("android.util."))
				continue;
			if (callee.getDeclaringClass().toString().startsWith("java.util."))
				continue;
			
			Stmt stmt = edge.srcStmt();
			if (stmt == null)
				continue;
			if (!stmt.containsInvokeExpr())
				continue;
			
			InvokeExpr expr = stmt.getInvokeExpr();
			
			boolean candidateFound = false;
			for (int argIdx = 0; argIdx < stmt.getInvokeExpr().getArgCount(); argIdx++) {
				Value arg = expr.getArg(argIdx);
				if ((arg instanceof StringConstant) && arg.toString().contains(".permission.")) {
					candidateFound = true;
					// System.out.println(edge);
					break;
				}
				if (arg instanceof StaticFieldRef) {
					SootField field = ((StaticFieldRef) arg).getField();
					if (StaticFieldResolver.field2permission.containsKey(field)) {
						candidateFound = true;
						// System.out.println("polyu == polyu == polyu == polyu");
						break;
					}
				}
			}
			
			if (!candidateFound)
				continue;
			if (potentialSMs.contains(callee))
				continue;
			// System.out.println(stmt);
			// System.out.println("    " + callee.getSignature());
			
			HashSet<SootMethod> reachableSMs = new HashSet<SootMethod>();
			Queue<SootMethod> queue = new LinkedList<SootMethod>();
			queue.add(callee);
			
			while (!queue.isEmpty()) {
				SootMethod curSM = queue.poll();
				reachableSMs.add(curSM);
				
				Iterator<Edge> edgeIterator = cg.edgesOutOf(curSM);
				while (edgeIterator.hasNext()) {
					Edge curEdge = edgeIterator.next();
					SootMethod nxtSM = curEdge.tgt();
					if (!queue.contains(nxtSM) && !reachableSMs.contains(nxtSM))
						queue.add(nxtSM);
				}
			}
			
			// for (SootMethod s : reachableSMs)
				// System.out.println("        " + s.getSignature());
			
			if (reachableSMs.size() == 1) {
				// System.out.println("[INFO] [CG-PermissionChecker][case-2]: " + callee.getSignature()); // debug
				potentialSMs.add(callee); // to keep *permissionCheckMethodCGSet* unchanged, do not add *callee* here
			} else {
				reachableSMs.retainAll(permissionCheckMethodCGSet);
				if (!reachableSMs.isEmpty()) {
					// System.out.println("[INFO] [CG-PermissionChecker][case-2]: " + callee.getSignature()); // debug
					potentialSMs.add(callee); // to keep *permissionCheckMethodCGSet* unchanged, do not add *callee* here
				}
			}
		}
		
		// case-3
		HashSet<SootMethod> suspiciousSMs = new HashSet<SootMethod>();
		for (Edge edge : cg) {
			SootMethod caller = edge.src();
			if (!caller.getReturnType().toString().equals("void") && !caller.getReturnType().toString().equals("boolean"))
				continue;
			if (!caller.getName().contains("Permission"))
				continue; // ??
			if (suspiciousSMs.contains(caller))
				continue;
			// System.out.println("\t" + callee.getSignature());
			
			int isSuspicious = 0;
			Iterator<Edge> edgeIter = cg.edgesOutOf(caller);
			while (edgeIter.hasNext()) {
				SootMethod tgtSM = edgeIter.next().tgt();
				if (tgtSM.getName().equals("<clinit>") || tgtSM.getName().equals("<init>")) {
					isSuspicious += 0; // normal cases
					if (tgtSM.getDeclaringClass().getName().equals("java.lang.SecurityException")) {
						// critical cases
						isSuspicious += 100;
					}
					continue;
				} else if (permissionCheckMethodCGSet.contains(tgtSM)) {
					// special cases
					isSuspicious += 1;
					continue;
				} else if (potentialSMs.contains(tgtSM)) {
					// special cases
					isSuspicious += 1;
					continue;
				} else if (tgtSM.getName().contains("uid") || tgtSM.getName().contains("pid")) {
					isSuspicious += 0; // normal cases
					continue;
				} else if (tgtSM.getDeclaringClass().getName().equals("android.util.Log") || tgtSM.getDeclaringClass().getName().equals("android.util.Slog")) {
					isSuspicious += 0; // normal cases
					continue;
				} else {
					isSuspicious -= 10000;
					break;
				}
			}
			
			if ((isSuspicious > 100) && (isSuspicious % 100) > 0) {
				// System.out.println("[INFO] [CG-PermissionChecker][case-3]: " + caller.getSignature()); // debug
				suspiciousSMs.add(caller);
			}
		}
		
		permissionCheckMethodCGSet.addAll(potentialSMs); // add potential methods here
		permissionCheckMethodCGSet.addAll(suspiciousSMs); // add suspicious methods here
		
		//
		// for (SootMethod pmSM : permissionCheckMethodCGSet) {
			// System.out.println(pmSM.getSignature());
		// }
		//
			
		System.out.println(" -->> Find Permission Check Methods in Callgraph Finish");
	}

}
