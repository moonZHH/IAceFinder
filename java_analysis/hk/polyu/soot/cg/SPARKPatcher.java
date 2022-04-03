package hk.polyu.soot.cg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import soot.ArrayType;
import soot.Body;
import soot.BooleanType;
import soot.ByteType;
import soot.CharType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.Local;
import soot.LongType;
import soot.NullType;
import soot.RefType;
import soot.Scene;
import soot.ShortType;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NewExpr;
import soot.jimple.Stmt;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

public class SPARKPatcher {
	
	public static HashMap<SootField, SootClass> fieldInitMap = new HashMap<SootField, SootClass>();
	public static HashSet<SootMethod> fieldInitSet = new HashSet<SootMethod>();
	
	public static void init() {
		for (SootClass sc : Scene.v().getClasses()) {
			if (sc.isPhantom())
				continue;
			
			for (SootMethod sm : sc.getMethods()) {
				if (!sm.isConcrete())
					continue;
				
				boolean willInitField = false;
				
				Body body = sm.retrieveActiveBody();
				BriefUnitGraph cfg = new BriefUnitGraph(body);
				SimpleLocalDefs localDefs = new SimpleLocalDefs(cfg);
				
				for (Unit unit : body.getUnits()) {
					Stmt stmt = (Stmt) unit;
					if (!(stmt instanceof AssignStmt))
						continue;
					
					AssignStmt assignStmt = (AssignStmt) stmt;
					Value leftOp = assignStmt.getLeftOp();
					Value rightOp = assignStmt.getRightOp();
					if (!((leftOp instanceof FieldRef) && (rightOp instanceof Local)))
						continue;
					
					willInitField = true; // indicate the method will initialize class field
					
					FieldRef leftRef = (FieldRef) leftOp;
					SootField leftField = leftRef.getField();
					String leftTypeRaw = leftField.getType().toString();
					SootClass leftTypeSC = Scene.v().getSootClassUnsafe(leftTypeRaw, false);
					if (leftTypeSC == null) {
						// for machine types, just ignore
						continue;
					}
					
					for (Unit defUnit : localDefs.getDefsOf((Local) rightOp)) {
						if (!(defUnit instanceof AssignStmt)) 
							continue;
						
						AssignStmt defStmt = (AssignStmt) defUnit;
						Value defRightOp = defStmt.getRightOp();
						
						SootClass concreteSC = null;
						if (defRightOp instanceof NewExpr) {
							concreteSC = ((NewExpr) defRightOp).getBaseType().getSootClass();
						}
						if (defRightOp instanceof InvokeExpr) {
							String concreteRaw = ((InvokeExpr) defRightOp).getMethod().getReturnType().toString();
							concreteSC = Scene.v().getSootClassUnsafe(concreteRaw, false);
						}
						
						if (concreteSC == null) {
							// for machine types, just ignore
							continue;
						}
						
						if (!concreteSC.isConcrete() || concreteSC.isPhantom()) {
							if (!concreteSC.getName().startsWith("java.")) {
								// System.err.println(stmt);
								// System.err.println("\t" + defStmt);
								// System.err.println("\t\t" + concreteSC.getName());
							}
							continue;
						}
							
						if (fieldInitMap.containsKey(leftField) && fieldInitMap.get(leftField) != concreteSC) {
							// System.err.println("ERR: " + leftField.toString() + " -> <" + fieldInitMap.get(leftField).getName() + ">, <" + concreteSC.getName() + ">");
							fieldInitMap.remove(leftField); // TODO: incorrect
						} else {
							fieldInitMap.put(leftField, concreteSC);
						}
					}
				}
				
				if (willInitField)
					fieldInitSet.add(sm);
			}
		}
	}
	
	public static void patch() {
		for (SootClass sc : Scene.v().getClasses()) {
			if (sc.isPhantom())
				continue;
			/* test
			if (!sc.getName().equals("com.android.server.display.DisplayManagerService")
			 && !sc.getName().equals("com.android.server.display.VirtualDisplayAdapter"))
				continue;
			*/
			
			for (SootMethod sm : sc.getMethods()) {
				if (!sm.isConcrete())
					continue;
				if (fieldInitSet.contains(sm))
					continue;
				// System.out.println(sm.getSignature()); // debug
				
				HashMap<Unit, Unit> swapMap = new HashMap<Unit, Unit>();
				HashMap<Unit, List<Unit>> injectMap = new HashMap<Unit, List<Unit>>();
				
				Body body = sm.retrieveActiveBody();
				
				for (Unit unit : body.getUnits()) {
					Stmt stmt = (Stmt) unit;
					if (!(stmt instanceof AssignStmt))
						continue;
					
					AssignStmt assignStmt = (AssignStmt) stmt;
					Value leftOp = assignStmt.getLeftOp();
					Value rightOp = assignStmt.getRightOp();
					if (!((leftOp instanceof Local) && (rightOp instanceof FieldRef)))
						continue;
					
					// System.out.println("\t" + stmt); // debug
					SootField rightField = ((FieldRef) rightOp).getField();
					String rightFieldRaw = rightField.getType().toString();
					SootClass rightFieldSC = Scene.v().getSootClassUnsafe(rightFieldRaw, false);
					if (rightFieldSC == null) {
						// for machine types, just ignore
						continue;
					}
					
					if (fieldInitMap.containsKey(rightField)) {
						NewExpr newExpr = Jimple.v().newNewExpr(fieldInitMap.get(rightField).getType());
						AssignStmt newStmt = Jimple.v().newAssignStmt(leftOp, newExpr);
						// System.out.println("\t\t" + newStmt); // debug
						swapMap.put(stmt, newStmt);
						
						List<Unit> injectUnits = new ArrayList<Unit>();
							
						try {
							SootMethod clinitSM = rightFieldSC.getMethodByName("<clinit>");
							InvokeExpr clinitInvokeExpr = Jimple.v().newStaticInvokeExpr(clinitSM.makeRef());
							InvokeStmt clinitInvokeStmt = Jimple.v().newInvokeStmt(clinitInvokeExpr);
							injectUnits.add(clinitInvokeStmt);
						} catch (RuntimeException e) { }
							
						try {
							LocalGenerator lg = new LocalGenerator(body);
							
							Local boolLocal = lg.generateLocal(BooleanType.v());
							Local byteLocal = lg.generateLocal(ByteType.v());
							Local charLocal = lg.generateLocal(CharType.v());
							Local doubleLocal = lg.generateLocal(DoubleType.v());
							Local floatLocal = lg.generateLocal(FloatType.v());
							Local intLocal = lg.generateLocal(IntType.v());
							Local longLocal = lg.generateLocal(LongType.v());
							Local nullLocal = lg.generateLocal(NullType.v());
							Local shortLocal = lg.generateLocal(ShortType.v());
							Local voidLocal = lg.generateLocal(VoidType.v());
							
							SootMethod initSM = CallgraphPatcherPre.fetchInitMethod(rightFieldSC);
							
							List<Value> params = new ArrayList<Value>();
							for (int paramIdx = 0; paramIdx < initSM.getParameterCount(); paramIdx++) {
								Type paramType = initSM.getParameterType(paramIdx);
								if (paramType instanceof ArrayType) {
									Type arrayBaseType = ((ArrayType) paramType).baseType;
									int arrayDimension = ((ArrayType) paramType).numDimensions;
									Local arrayLocal = lg.generateLocal(ArrayType.v(arrayBaseType, arrayDimension));
									params.add(arrayLocal);
								} else if (paramType instanceof BooleanType) {
									params.add(boolLocal);
								} else if (paramType instanceof ByteType) {
									params.add(byteLocal);
								} else if (paramType instanceof CharType) {
									params.add(charLocal);
								} else if (paramType instanceof DoubleType) {
									params.add(doubleLocal);
								} else if (paramType instanceof FloatType) {
									params.add(floatLocal);
								} else if (paramType instanceof IntType) {
									params.add(intLocal);
								} else if (paramType instanceof LongType) {
									params.add(longLocal);
								} else if (paramType instanceof NullType) {
									params.add(nullLocal);
								} else if (paramType instanceof ShortType) {
									params.add(shortLocal);
								} else if (paramType instanceof VoidType) { 
									params.add(voidLocal);
								} else if (paramType instanceof RefType) {
									RefType paramRef = ((RefType) paramType);
									if (paramRef.hasSootClass()) {
										Local refLocal = lg.generateLocal(paramRef.getSootClass().getType());
										params.add(refLocal);
										
										NewExpr newRefExpr = Jimple.v().newNewExpr(paramRef.getSootClass().getType());
										AssignStmt newRefStmt = Jimple.v().newAssignStmt(refLocal, newRefExpr);
										injectUnits.add(newRefStmt);
									} else {
										System.out.println("WARN [SPARKPatcher]: RefType -> " + paramType); // debug
										params.add(nullLocal);
									}
								} else {
									System.out.println("WARN [SPARKPatcher]: Special Type -> " + paramType); // debug
									params.add(nullLocal);
								}
							}
						
							InvokeExpr exprInvoke = Jimple.v().newVirtualInvokeExpr((Local) leftOp, initSM.makeRef(), params);
							InvokeStmt stmtInvoke = Jimple.v().newInvokeStmt(exprInvoke);
							injectUnits.add(stmtInvoke);
						} catch (RuntimeException e) { }
						
						injectMap.put(newStmt, injectUnits);
					}
				}
				
				// update Body
				for (Entry<Unit, Unit> each : swapMap.entrySet()) {
					Unit out = each.getKey();
					Unit in = each.getValue();
					body.getUnits().swapWith(out, in);
				}
				
				for (Entry<Unit, List<Unit>> each : injectMap.entrySet()) {
					Unit tgtUnit = each.getKey();
					List<Unit> injectUnits = each.getValue();
					body.getUnits().insertAfter(injectUnits, tgtUnit);
				}
				
				sm.setActiveBody(body);
			}
		}
	}

}
