package hk.polyu.soot.cg;

import java.util.HashSet;

import soot.Body;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;

public class CallgraphPatcherPre {
	
	public static void patch() {
		// patch SPART analysis
		SPARKPatcher.init();
		SPARKPatcher.patch();
		System.out.println(" -->> Patch SPARK Finish");
		// patch Binder-related issue
		BinderPatcher.init();
		BinderPatcher.patch();
		System.out.println(" -->> Patch IPC (Binder) Finish");
	}
	
	public static SootMethod fetchInitMethod(SootClass sc) throws RuntimeException {
		HashSet<SootMethod> initSMs = new HashSet<SootMethod>();
		
		for (SootMethod sm : sc.getMethods()) {
			if (!sm.isConcrete())
				continue;
			if (sm.getName().equals("<init>"))
				initSMs.add(sm);
		}
		
		if (initSMs.size() == 1)
			return sc.getMethodByName("<init>");
		
		HashSet<SootMethod> indirectInitSMs = new HashSet<SootMethod>();
		for (SootMethod candidate : initSMs) {
			Body body = candidate.retrieveActiveBody();
			for (Unit unit : body.getUnits()) {
				Stmt stmt = (Stmt) unit;
				if (!stmt.containsInvokeExpr())
					continue;
				
				SootMethod callee = stmt.getInvokeExpr().getMethod();
				if ((callee.getDeclaringClass() == sc) && callee.getName().equals("<init>"))
					indirectInitSMs.add(callee);
			}
		}
		
		for (SootMethod candidate : initSMs)
			if (!indirectInitSMs.contains(candidate))
				return candidate; // randomly select one
		
		throw new RuntimeException();
	}

}
