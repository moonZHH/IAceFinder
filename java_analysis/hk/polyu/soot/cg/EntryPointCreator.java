package hk.polyu.soot.cg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import soot.Modifier;
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
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NewExpr;

public class EntryPointCreator {
	
	public static String specBinderProxyName = "";
	
	public static SootMethod create() {
		// create dummy main class
		SootClass dummyMainClass = Scene.v().makeSootClass("hk.polyu.Main");
		dummyMainClass.setResolvingLevel(SootClass.BODIES);
		Scene.v().addClass(dummyMainClass);
		
		// create entry-point method
		Type stringArrayType = ArrayType.v(RefType.v("java.lang.String"), 1);
		SootMethod dummyMainMethod = Scene.v().makeSootMethod("main", Collections.singletonList(stringArrayType), VoidType.v());
		dummyMainMethod.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
		
		// create method body
		createBody(dummyMainMethod);
		// System.out.println(dummyMainMethod.retrieveActiveBody()); // debug
		
		// add entry-point method to dummy main class
		dummyMainClass.addMethod(dummyMainMethod);
		
		// add dummy main class to soot
		dummyMainClass.setApplicationClass();
		
		return dummyMainMethod;
	}
	
	private static void createBody(SootMethod dummyMainMethod) {
		Body body = Jimple.v().newBody();
		body.setMethod(dummyMainMethod);
		dummyMainMethod.setActiveBody(body);
		
		LocalGenerator lg = new LocalGenerator(body);
		
		List<Unit> bodyStmtList = new ArrayList<Unit>();
		
		// add the parameter reference to the body
		Local paramLocal = lg.generateLocal(ArrayType.v(RefType.v("java.lang.String"), 1));
		IdentityStmt stmtParam = Jimple.v().newIdentityStmt(paramLocal, Jimple.v().newParameterRef(ArrayType.v(RefType.v("java.lang.String"), 1), 0));
		bodyStmtList.add(stmtParam);
		
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
	
		{
			String binderProxy = specBinderProxyName;
			// System.out.println(binderProxy); // debug
			SootClass binderProxySC = Scene.v().getSootClassUnsafe(binderProxy, false);
			assert binderProxySC != null && !binderProxySC.isPhantom();
			
			// constructors
			Local serviceManagerLocal = lg.generateLocal(binderProxySC.getType());
			NewExpr newExpr = Jimple.v().newNewExpr(binderProxySC.getType());
			AssignStmt newStmt = Jimple.v().newAssignStmt(serviceManagerLocal, newExpr);
			bodyStmtList.add(newStmt);
			
			try {
				SootMethod clinitSM = binderProxySC.getMethodByName("<clinit>");
				InvokeExpr clinitInvokeExpr = Jimple.v().newStaticInvokeExpr(clinitSM.makeRef());
				InvokeStmt clinitInvokeStmt = Jimple.v().newInvokeStmt(clinitInvokeExpr);
				bodyStmtList.add(clinitInvokeStmt);
			} catch (RuntimeException e) {  }
			
			try {
				SootMethod initSM = binderProxySC.getMethodByName("<init>");
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
							bodyStmtList.add(newRefStmt);
						} else {
							System.out.println("\t\t" + "RefType -> " + paramType); // debug
							params.add(nullLocal);
						}
					} else {
						System.out.println("\t\t" + "Special Type -> " + paramType); // debug
						params.add(nullLocal);
					}
				}
				
				InvokeExpr exprInvoke = Jimple.v().newVirtualInvokeExpr(serviceManagerLocal, initSM.makeRef(), params);
				InvokeStmt stmtInvoke = Jimple.v().newInvokeStmt(exprInvoke);
				bodyStmtList.add(stmtInvoke);
			} catch (RuntimeException e) {  }
			
			// other methods
			for (SootMethod calleeSM : binderProxySC.getMethods()) {
				String calleeName = calleeSM.getName();
				if (calleeName.equals("<init>") || calleeName.equals("<clinit>") || calleeName.startsWith("access$") || calleeName.startsWith("lambda$"))
					continue; // ignore special cases of methods
				
				// System.out.println("\t" + calleeSM.getSignature());
				// System.out.println(calleeSM.retrieveActiveBody());
				
				List<Value> params = new ArrayList<Value>();
				for (int paramIdx = 0; paramIdx < calleeSM.getParameterCount(); paramIdx++) {
					Type paramType = calleeSM.getParameterType(paramIdx);
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
							bodyStmtList.add(newRefStmt);
						} else {
							System.out.println("\t\t" + "RefType -> " + paramType); // debug
							params.add(nullLocal);
						}
					} else {
						System.out.println("\t\t" + "Special Type -> " + paramType); // debug
						params.add(nullLocal);
					}
				}
				
				if (calleeSM.isStatic()) {
					InvokeExpr exprInvoke = Jimple.v().newStaticInvokeExpr(calleeSM.makeRef(), params);
					InvokeStmt stmtInvoke = Jimple.v().newInvokeStmt(exprInvoke);
					bodyStmtList.add(stmtInvoke);
				} else {
					try {
						InvokeExpr exprInvoke = Jimple.v().newVirtualInvokeExpr(serviceManagerLocal, calleeSM.makeRef(), params);
						InvokeStmt stmtInvoke = Jimple.v().newInvokeStmt(exprInvoke);
						bodyStmtList.add(stmtInvoke);
					} catch (Exception e) {
						// pass
					}
				}
			}
		}
		
		body.getUnits().addAll(bodyStmtList);
	}

}
