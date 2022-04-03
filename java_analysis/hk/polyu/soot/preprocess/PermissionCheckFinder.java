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
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.VirtualInvokeExpr;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

public class PermissionCheckFinder {
	
	// find the permission check methods in Scene
	private static HashSet<SootMethod> permissionCheckMethodSet = new HashSet<SootMethod>();
	
	public static void find() {
		for (SootClass sc : Scene.v().getClasses()) {
			if (sc.isPhantom())
				continue;
			
			for (SootMethod sm : sc.getMethods()) {
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
							if (fieldName.equals("PERMISSION_DENIED") || fieldName.equals("PERMISSION_GRANTED")) {
								isPermissionCheckMethod = true;
								break;
							}
						}
					}
				}
				
				if (isPermissionCheckMethod)
					permissionCheckMethodSet.add(sm);
			}
		}
		
		// for (SootMethod sm : permissionCheckMethodSet)
			// System.out.println("INFO [PermissionChecker]: " + sm.getSignature()); // debug
		// System.out.println(" -->> Find Permission Check Methods Finish");
		
		save();
	}
	
	private static void save() {
		try {
			File PermissionCheckFile = new File(Config.OutputPermissionCheckFile);
			if (PermissionCheckFile.exists())
				PermissionCheckFile.delete();
			PermissionCheckFile.createNewFile();
			
			FileWriter fw = new FileWriter(PermissionCheckFile);
			for (SootMethod sm : permissionCheckMethodSet)
				fw.write(sm.getSignature() + "\n");
			
			fw.flush();
			fw.close();
		} catch (IOException ioe) {
			System.err.println("[error] file " + Config.OutputPermissionCheckFile + " fail to be created !");
			System.exit(0);
		}
	}

}
