package hk.polyu.soot.preprocess;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;

import hk.polyu.Config;
import soot.Body;
import soot.Local;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.EqExpr;
import soot.jimple.FieldRef;
import soot.jimple.IfStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.NewExpr;
import soot.jimple.Stmt;
import soot.jimple.VirtualInvokeExpr;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.scalar.SimpleLocalDefs;

public class UidCheckFinder {
	
	// find the uid check methods in Scene
	private static HashSet<SootMethod> uidCheckMethodSet = new HashSet<SootMethod>();
	
	public static void find() {
		for (SootClass sc : Scene.v().getClasses()) {
			if (sc.isPhantom())
				continue;
			
			for (SootMethod sm : sc.getMethods()) {
				boolean isUidCheckMethod = false;
				
				if (!sm.isConcrete())
					continue;
				if (!sm.getReturnType().toString().equals("void"))
					continue;
				
				Body body = sm.retrieveActiveBody();
				BriefUnitGraph cfg = new BriefUnitGraph(body);
				SimpleLocalDefs localDefs = new SimpleLocalDefs(cfg);
				
				for (Unit unit : body.getUnits()) {
					if (!(unit instanceof AssignStmt))
						continue;
					
					AssignStmt assignStmt = (AssignStmt) unit;
					Value rightOp = assignStmt.getRightOp();
					if (!(rightOp instanceof NewExpr))
						continue;
					if (!((NewExpr) rightOp).getType().toString().equals("java.lang.SecurityException"))
						continue;
					
					// System.out.println(unit);
					MHGDominatorsFinder<Unit> cfgDom = new MHGDominatorsFinder<Unit>(cfg);
					for (Unit domUnit : cfgDom.getDominators(unit)) {
						if (!(domUnit instanceof IfStmt))
							continue;
						Value ifCondition = ((IfStmt) domUnit).getCondition();
						if (!(ifCondition instanceof EqExpr))
							continue;
						
						// System.out.println("\t" + domUnit);
						
						Value ifOp1 = ((EqExpr) ifCondition).getOp1();
						if (ifOp1 instanceof Local) {
							for (Unit defUnit : localDefs.getDefsOf((Local) ifOp1)) {
								// System.out.println("\t\t" + "Definition Unit: " + defUnit);
								
								Stmt defStmt = (Stmt) defUnit;
								if (!defStmt.containsInvokeExpr())
									continue;
								InvokeExpr invokeExpr = defStmt.getInvokeExpr();
								if (!invokeExpr.getMethod().getSignature().equals("<java.lang.Integer: int intValue()>"))
									continue;
								
								Value baseValue = ((VirtualInvokeExpr) invokeExpr).getBase();
								if (!(baseValue instanceof Local))
									continue;
								for (Unit baseUnit : localDefs.getDefsOf((Local) baseValue)) {
									if (!(baseUnit instanceof AssignStmt))
										continue;
									Value rhsOp = ((AssignStmt) baseUnit).getRightOp();
									if (!(rhsOp instanceof FieldRef))
										continue;
									
									SootField refField = ((FieldRef) rhsOp).getField();
									if (refField.toString().equals("<android.os.Process: java.lang.Integer ROOT_UID>") 
									 || refField.toString().equals("<android.os.Process: java.lang.Integer SYSTEM_UID>")
									 || refField.toString().equals("<android.os.Process: java.lang.Integer SHELL_UID>")) {
										isUidCheckMethod = true;
										break;
									}
								}
								
								if (isUidCheckMethod)
									break;
							}
						}
						
						if (isUidCheckMethod)
							break;
						
						Value ifOp2 = ((EqExpr) ifCondition).getOp2();
						if (ifOp2 instanceof Local) {
							for (Unit defUnit : localDefs.getDefsOf((Local) ifOp2)) {
								// System.out.println("\t\t" + "Definition Unit: " + defUnit);
								
								Stmt defStmt = (Stmt) defUnit;
								if (!defStmt.containsInvokeExpr())
									continue;
								InvokeExpr invokeExpr = defStmt.getInvokeExpr();
								if (!invokeExpr.getMethod().getSignature().equals("<java.lang.Integer: int intValue()>"))
									continue;
								
								Value baseValue = ((VirtualInvokeExpr) invokeExpr).getBase();
								if (!(baseValue instanceof Local))
									continue;
								for (Unit baseUnit : localDefs.getDefsOf((Local) baseValue)) {
									if (!(baseUnit instanceof AssignStmt))
										continue;
									Value rhsOp = ((AssignStmt) baseUnit).getRightOp();
									if (!(rhsOp instanceof FieldRef))
										continue;
									
									SootField refField = ((FieldRef) rhsOp).getField();
									if (refField.toString().equals("<android.os.Process: java.lang.Integer ROOT_UID>") 
									 || refField.toString().equals("<android.os.Process: java.lang.Integer SYSTEM_UID>")
									 || refField.toString().equals("<android.os.Process: java.lang.Integer SHELL_UID>")) {
										isUidCheckMethod = true;
										break;
									}
								}
								
								if (isUidCheckMethod)
									break;
							}
						}
						
						if (isUidCheckMethod)
							break;
					}
					
					if (isUidCheckMethod)
						break;
				}
				
				if (isUidCheckMethod)
					uidCheckMethodSet.add(sm);
			}
		}
		
		for (SootMethod sm : uidCheckMethodSet) 
			System.out.println("INFO [UidChecker]: " + sm.getSignature()); // debug
		System.out.println(" -->> Find Uid Check Methods Finish");
		
		save();
	}
	
	private static void save() {
		try {
			File UidCheckFile = new File(Config.OutputUidCheckFile);
			if (UidCheckFile.exists())
				UidCheckFile.delete();
			UidCheckFile.createNewFile();
			
			FileWriter fw = new FileWriter(UidCheckFile);
			for (SootMethod sm : uidCheckMethodSet)
				fw.write(sm.getSignature() + "\n");
			
			fw.flush();
			fw.close();
		} catch (IOException ioe) {
			System.err.println("[error] file " + Config.OutputUidCheckFile + " fail to be created !");
			System.exit(0);
		}
	}

}
