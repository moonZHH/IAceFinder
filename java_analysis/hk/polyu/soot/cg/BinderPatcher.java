package hk.polyu.soot.cg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import hk.polyu.Config;
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
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NewExpr;
import soot.jimple.NullConstant;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.jimple.ThisRef;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class BinderPatcher {
	
	// public static HashMap<String, SootClass> serviceManagerMap = new HashMap<String, SootClass>();
	// public static HashSet<SootClass> serviceManagerSet = new HashSet<SootClass>();
	
	// public static HashSet<SootClass> serviceInterfaceSet = new HashSet<SootClass>();
	public static HashSet<SootClass> binderProxySet = new HashSet<SootClass>();
	public static HashSet<SootClass> binderStubSet = new HashSet<SootClass>();
	public static HashSet<SootClass> binderRemoteSet = new HashSet<SootClass>();
	
	// public static HashMap<SootClass, SootClass> serviceManagerInterfaceMap = new HashMap<SootClass, SootClass>();
	// public static HashMap<SootClass, SootClass> serviceInterfaceProxyMap = new HashMap<SootClass, SootClass>();
	// public static HashMap<SootClass, SootClass> serviceInterfaceStubMap = new HashMap<SootClass, SootClass>();
	public static HashMap<SootClass, SootClass> binderStubProxyMap = new HashMap<SootClass, SootClass>();
	public static HashMap<SootClass, HashSet<SootClass>> binderStubRemoteMap = new HashMap<SootClass, HashSet<SootClass>>();
	public static HashMap<SootClass, HashSet<SootClass>> binderProxyRemoteMap = new HashMap<SootClass, HashSet<SootClass>>();
	
	public static void init() {
		// fetchServiceManager();
		// serviceManagerSet = new HashSet<SootClass>(serviceManagerMap.values());
		
		// collect Binder proxy and stub classes
		for (SootClass sc : Scene.v().getClasses()) {
			if (!sc.getName().endsWith("$Stub$Proxy"))
				continue;
				
			SootClass binderProxyClass = sc;
			binderProxySet.add(binderProxyClass);
				
			String binderStubName = binderProxyClass.getName().replace("$Stub$Proxy", "$Stub");
			SootClass binderStubClass = Scene.v().getSootClassUnsafe(binderStubName, false);
			if (binderStubClass == null) {
				if (Config.DEBUG)
					System.err.println("[ERR]: Can *not* find the SootClass for Binder stub <" + binderStubName + ">");
				throw new RuntimeException();
			}
			binderStubSet.add(binderStubClass);
			
			binderStubProxyMap.put(binderStubClass, binderProxyClass);
		}
		
		// collect Binder remote classes
		for (SootClass sc : Scene.v().getClasses()) {
			if (!sc.hasSuperclass())
				continue;
			
			SootClass superClass = sc.getSuperclass();
			if (binderStubSet.contains(superClass)) {
				// handle DevicePolicyManagerService
				if (sc.getName().equals("com.android.server.devicepolicy.BaseIDevicePolicyManager")) {
					sc = Scene.v().getSootClassUnsafe("com.android.server.devicepolicy.DevicePolicyManagerService", false);
					assert sc != null;
				}
				/*
				// handle AbstractAccessibilityServiceConnection
				if (sc.getName().equals("com.android.server.accessibility.AbstractAccessibilityServiceConnection")) {
					sc = Scene.v().getSootClassUnsafe("com.android.server.accessibility.AccessibilityServiceConnection", false);
					assert sc != null;
				}
				*/
				// handle PeopleServiceInternal
				if (sc.getName().equals("com.android.server.people.PeopleServiceInternal")) {
					sc = Scene.v().getSootClassUnsafe("com.android.server.people.PeopleService$LocalService", false);
					assert sc != null;
				}
				
				binderRemoteSet.add(sc);
				
				if (!binderStubRemoteMap.containsKey(superClass))
					binderStubRemoteMap.put(superClass, new HashSet<SootClass>());
				binderStubRemoteMap.get(superClass).add(sc);
				
				SootClass pClass = binderStubProxyMap.get(superClass);
				if (!binderProxyRemoteMap.containsKey(pClass))
					binderProxyRemoteMap.put(pClass, new HashSet<SootClass>());
				binderProxyRemoteMap.get(pClass).add(sc);
			}
		}
		
		// debug
		/*
		for (Entry<SootClass, HashSet<SootClass>> each : binderStubRemoteMap.entrySet()) {
			SootClass binderStubClass = each.getKey();
			HashSet<SootClass> binderRemoteClasses = each.getValue();
			if (binderRemoteClasses.isEmpty())
				continue;
			if (binderRemoteClasses.size() > 1)
				continue;
				
			for (SootClass binderRemoteClass : binderRemoteClasses) {
				System.out.println(binderStubClass.getName() + " <- " + binderRemoteClass.getName());
			}
		}
		*/
		//
		for (Entry<SootClass, HashSet<SootClass>> each : binderProxyRemoteMap.entrySet()) {
			SootClass binderProxyClass = each.getKey();
			HashSet<SootClass> binderRemoteClasses = each.getValue();
			if (binderRemoteClasses.isEmpty())
				continue;
			if (binderRemoteClasses.size() > 1)
				continue;
				
			for (SootClass binderRemoteClass : binderRemoteClasses) {
				System.out.println(binderProxyClass.getName() + " <- " + binderRemoteClass.getName());
			}
		}
		//
	}
	
	/*
	private static void fetchServiceManager() {
		SootClass ServiceRegistrySC = Scene.v().getSootClass("android.app.SystemServiceRegistry");
		SootMethod ClinitSM = ServiceRegistrySC.getMethodByName("<clinit>");
		SootMethod RegisterServiceSM = ServiceRegistrySC.getMethodByName("registerService");
		
		Body ClinitBD = ClinitSM.retrieveActiveBody();
		for (Unit unit : ClinitBD.getUnits()) {
			Stmt stmt = (Stmt) unit;
			if (stmt.containsInvokeExpr()) {
				SootMethod callee = stmt.getInvokeExpr().getMethod();
				if (callee.getSignature().equals(RegisterServiceSM.getSignature())) {
					InvokeExpr expr = stmt.getInvokeExpr();
					String serviceName = ((StringConstant) expr.getArg(0)).value;
					String serviceClassName = ((ClassConstant) expr.getArg(1)).value;
					if (serviceClassName.equals("Landroid/os/IBinder;")) {
						// special case
						System.err.println("[ERR]: service manager <" + serviceClassName + "> for <" + serviceName + "> is strange");
						continue;
					}
					
					SootClass serviceSC = Scene.v().getSootClass(serviceClassName.substring(1, serviceClassName.length() - 1).replace("/", "."));
					serviceManagerMap.put(serviceName, serviceSC);
					// System.out.println(serviceName + " -> " + serviceSC.getName()); // debug
				}
			}
		}
	}
	*/
	
	public static void patch() {
		// main logic
		for (Entry<SootClass, HashSet<SootClass>> each : binderProxyRemoteMap.entrySet()) {
			SootClass proxySC = each.getKey();
			HashSet<SootClass> remoteSCs = each.getValue();
			
			HashSet<String> handledMethods = new HashSet<String>();
			// override methods
			for (SootMethod proxySM : proxySC.getMethods()) {
				// System.out.println(proxySM.getSignature());
				handledMethods.add(proxySM.getSubSignature());
				
				if (!proxySM.isConcrete())
					continue;
				if (proxySM.isConstructor())
					continue;
				if (proxySM.getName().equals("asBinder"))
					continue;
				if (proxySM.getName().equals("getInterfaceDescriptor"))
					continue;
				if (!willCallTransact(proxySM))
					continue;
				
				String subsignature = proxySM.getSubSignature();
				for (SootClass remoteSC : remoteSCs) {
					SootMethod remoteSM = remoteSC.getMethodUnsafe(subsignature);
					if (remoteSM == null) {
						System.out.println("[ERR]: can *not* find <" + subsignature + "> in " + remoteSC.getName());
						continue;
					}
				
					// do the patch
					// System.out.println("[Patch] " + proxySM.getSignature() + " -->> " + remoteSM.getSignature());
					patchBody(proxySM, remoteSM);
				}
			}
		}
		
		// additional logic
		SootClass binderSC = Scene.v().getSootClass("android.os.IBinder");
		SootMethod transactSM = binderSC.getMethod("boolean transact(int,android.os.Parcel,android.os.Parcel,int)");
		
		// ==>> [FIX]: Binder.dump will not be overrode by $Stub$Proxy classes, causing miss of edge problem
		for (Entry<SootClass, HashSet<SootClass>> each : binderProxyRemoteMap.entrySet()) {
			SootClass proxySC = each.getKey();
			HashSet<SootClass> remoteSCs = each.getValue();
			
			for (SootClass remoteSC : remoteSCs) {
				for (SootMethod remoteSM : remoteSC.getMethods()) {
					if (remoteSM.getName().equals("dump")) {
						// do the patch
						SootMethod proxySM = proxySC.getMethodUnsafe(remoteSM.getSubSignature());
						if (proxySM != null && !proxySM.isPhantom()) {
							// do nothing
						} else {
							proxySM = new SootMethod("dump", remoteSM.getParameterTypes(), remoteSM.getReturnType());
							proxySC.addMethod(proxySM);
							Body body = Jimple.v().newBody(proxySM);
							proxySM.setActiveBody(body);
							
							ArrayList<Stmt> stmts = new ArrayList<Stmt>();
							
							LocalGenerator lg = new LocalGenerator(body);
							
							ThisRef thisRef = Jimple.v().newThisRef(proxySC.getType());
							Local thisLocal = lg.generateLocal(proxySC.getType());
							IdentityStmt thisStmt = Jimple.v().newIdentityStmt(thisLocal, thisRef);
							stmts.add(thisStmt);
							
							for (int paraIdx = 0; paraIdx < proxySM.getParameterCount(); paraIdx++) {
								Type paraType = proxySM.getParameterType(paraIdx);
								ParameterRef paraRef = Jimple.v().newParameterRef(paraType, paraIdx);
								Local paraLocal = lg.generateLocal(paraType);
								IdentityStmt idStmt = Jimple.v().newIdentityStmt(paraLocal, paraRef);
								stmts.add(idStmt);
							}
							
							InvokeExpr invExpr = Jimple.v().newInterfaceInvokeExpr(thisLocal, transactSM.makeRef(), IntConstant.v(0), NullConstant.v(), NullConstant.v(), IntConstant.v(0));
							InvokeStmt invStmt = Jimple.v().newInvokeStmt(invExpr);
							stmts.add(invStmt);
							
							ReturnVoidStmt retStmt = Jimple.v().newReturnVoidStmt();
							stmts.add(retStmt);
							
							body.getUnits().addAll(stmts);
						}
						
						// System.out.println("[Patch] " + proxySM.getSignature() + " -->> " + remoteSM.getSignature());
						patchBody(proxySM, remoteSM);
					}
				}
			}
		}
	}
	
	private static boolean willCallTransact(SootMethod proxySM) {
		boolean ret = false;
		
		Body body = proxySM.retrieveActiveBody();
		// System.out.println(body); // debug
		for (Unit unit : body.getUnits()) {
			Stmt stmt = (Stmt) unit;
			if (!stmt.containsInvokeExpr())
				continue;
			
			SootMethod calleeSM = stmt.getInvokeExpr().getMethod();
			if (calleeSM.getName().equals("transact")) {
				// System.out.println(stmt);
				ret = true;
			}
		}
		
		return ret;
	}
	
	private static void patchBody(SootMethod proxySM, SootMethod remoteSM) {
		// System.out.println(proxySM.getSignature() + " -> " + remoteSM.getSignature()); // debug
		
		// adjust the Body of the Binder proxy method
		Unit tgtUnit = null;
		ArrayList<Unit> injectUnits = new ArrayList<Unit>();
		
		Body body = proxySM.retrieveActiveBody();
		for (Unit unit : body.getUnits()) {
			Stmt stmt = (Stmt) unit;
			if (!stmt.containsInvokeExpr())
				continue;
			
			SootMethod calleeSM = stmt.getInvokeExpr().getMethod();
			if (calleeSM.getName().equals("transact")) {
				tgtUnit = stmt;
				
				// construct inject Units
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
				ArrayList<Local> constantLocals = new ArrayList<Local>();
				constantLocals.add(boolLocal);
				constantLocals.add(byteLocal);
				constantLocals.add(charLocal);
				constantLocals.add(doubleLocal);
				constantLocals.add(floatLocal);
				constantLocals.add(intLocal);
				constantLocals.add(longLocal);
				constantLocals.add(nullLocal);
				constantLocals.add(shortLocal);
				constantLocals.add(voidLocal);
				
				SootClass remoteSC = remoteSM.getDeclaringClass();
				SootClass outerSC = remoteSC.getOuterClassUnsafe();
				
				Local remoteLocal = lg.generateLocal(remoteSC.getType());
				NewExpr newExpr = Jimple.v().newNewExpr(remoteSC.getType());
				AssignStmt newStmt = Jimple.v().newAssignStmt(remoteLocal, newExpr);
				injectUnits.add(newStmt);
				
				try {
					SootMethod clinitSM = remoteSC.getMethodByName("<clinit>");
					InvokeExpr clinitInvokeExpr = Jimple.v().newStaticInvokeExpr(clinitSM.makeRef());
					InvokeStmt clinitInvokeStmt = Jimple.v().newInvokeStmt(clinitInvokeExpr);
					injectUnits.add(clinitInvokeStmt);
				} catch (RuntimeException e) { }
				
				try {
					SootMethod initSM = CallgraphPatcherPre.fetchInitMethod(remoteSC);
					
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
								String typeRaw = paramRef.getSootClass().getType().toString();
								if (typeRaw.equals("android.content.Context"))
									typeRaw = "android.app.ContextImpl";
								SootClass typeSC = Scene.v().getSootClassUnsafe(typeRaw, false);
								
								Local refLocal = lg.generateLocal(typeSC.getType());
								params.add(refLocal);
								
								NewExpr newRefExpr = Jimple.v().newNewExpr(typeSC.getType());
								AssignStmt newRefStmt = Jimple.v().newAssignStmt(refLocal, newRefExpr);
								injectUnits.add(newRefStmt);
								
								// deal with outer class
								if (outerSC != null && typeSC == outerSC) {
									List<Unit> initUnits = handleOuterClass(outerSC, refLocal, lg, constantLocals);
									injectUnits.addAll(initUnits);
								}
							} else {
								System.out.println("[WARN] [BinderPatcher]: RefType -> " + paramType); // debug
								params.add(nullLocal);
							}
						} else {
							System.out.println("[WARN] [BinderPatcher]: Special Type -> " + paramType); // debug
							params.add(nullLocal);
						}
					}
				
					InvokeExpr exprInvoke = Jimple.v().newVirtualInvokeExpr(remoteLocal, initSM.makeRef(), params);
					InvokeStmt stmtInvoke = Jimple.v().newInvokeStmt(exprInvoke);
					injectUnits.add(stmtInvoke);
				} catch (RuntimeException e) { }
				
				ArrayList<Value> params = new ArrayList<Value>();
				for (int paramIdx = 0; paramIdx < body.getParameterLocals().size(); paramIdx++) {
					Local param = body.getParameterLocal(paramIdx);
					params.add(param);
				}
				InvokeExpr remoteInvokeExpr = Jimple.v().newVirtualInvokeExpr(remoteLocal, remoteSM.makeRef(), params);
				InvokeStmt remoteInvokeStmt = Jimple.v().newInvokeStmt(remoteInvokeExpr);
				injectUnits.add(remoteInvokeStmt);
				
				break;
			}
		}
		
		if (tgtUnit != null) {
			body.getUnits().insertAfter(injectUnits, tgtUnit);
		}
		
		// update the Body of the Binder proxy method
		proxySM.setActiveBody(body);
		// System.out.println(body); // debug
	}
	
	private static List<Unit> handleOuterClass(SootClass outerSC, Local outerLocal, LocalGenerator lg, ArrayList<Local> constantLocals) {
		ArrayList<Unit> injectUnits = new ArrayList<Unit>();
		
		Local boolLocal = constantLocals.get(0);
		Local byteLocal = constantLocals.get(1);
		Local charLocal = constantLocals.get(2);
		Local doubleLocal = constantLocals.get(3);
		Local floatLocal = constantLocals.get(4);
		Local intLocal = constantLocals.get(5);
		Local longLocal = constantLocals.get(6);
		Local nullLocal = constantLocals.get(7);
		Local shortLocal = constantLocals.get(8);
		Local voidLocal = constantLocals.get(9);
		
		try {
			SootMethod clinitSM = outerSC.getMethodByName("<clinit>");
			InvokeExpr clinitInvokeExpr = Jimple.v().newStaticInvokeExpr(clinitSM.makeRef());
			InvokeStmt clinitInvokeStmt = Jimple.v().newInvokeStmt(clinitInvokeExpr);
			injectUnits.add(clinitInvokeStmt);
		} catch (RuntimeException e) { }
		
		try {
			SootMethod initSM = CallgraphPatcherPre.fetchInitMethod(outerSC);
			
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
						String typeRaw = paramRef.getSootClass().getType().toString();
						if (typeRaw.equals("android.content.Context"))
							typeRaw = "android.app.ContextImpl";
						SootClass typeSC = Scene.v().getSootClassUnsafe(typeRaw, false);
						
						Local refLocal = lg.generateLocal(typeSC.getType());
						params.add(refLocal);
						
						NewExpr newRefExpr = Jimple.v().newNewExpr(typeSC.getType());
						AssignStmt newRefStmt = Jimple.v().newAssignStmt(refLocal, newRefExpr);
						injectUnits.add(newRefStmt);
					} else {
						System.out.println("[WARN] [BinderPatcher]: RefType -> " + paramType); // debug
						params.add(nullLocal);
					}
				} else {
					System.out.println("[WARN] [BinderPatcher]: Special Type -> " + paramType); // debug
					params.add(nullLocal);
				}
			}
		
			InvokeExpr exprInvoke = Jimple.v().newVirtualInvokeExpr(outerLocal, initSM.makeRef(), params);
			InvokeStmt stmtInvoke = Jimple.v().newInvokeStmt(exprInvoke);
			injectUnits.add(stmtInvoke);
		} catch (RuntimeException e) { }
		
		return injectUnits;
	}
	
	// ---- //
	
	public static void patchPost() {
		CallGraph cg = Scene.v().getCallGraph();
		
		HashSet<Edge> removes = new HashSet<Edge>();
		
		// case-0
		Iterator<Edge> edgeIter = cg.iterator();
		while (edgeIter.hasNext()) {
			Edge edge = edgeIter.next();
			SootMethod srcSM = edge.src();
			SootClass srcSC = srcSM.getDeclaringClass();
			if (binderProxyRemoteMap.containsKey(srcSC)) {
				SootMethod tgtSM = edge.tgt();
				SootClass tgtSC = tgtSM.getDeclaringClass();
				if (binderProxyRemoteMap.get(srcSC).contains(tgtSC) && tgtSM.getName().equals("<init>")) {
					removes.add(edge);
				}
			}
		}
		
		// case-1
		edgeIter = cg.iterator();
		while (edgeIter.hasNext()) {
			Edge edge = edgeIter.next();
			SootMethod tgtSM = edge.tgt();
			if (tgtSM.getName().equals("onTransact")) {
				// System.out.println(tgtSM.getSignature());
				removes.add(edge);
			}
		}
		
		// case-2
		edgeIter = cg.iterator();
		while (edgeIter.hasNext()) {
			Edge edge = edgeIter.next();
			SootMethod tgtSM = edge.tgt();
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
