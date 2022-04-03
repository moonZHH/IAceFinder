package hk.polyu.soot.cg;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import hk.polyu.Config;
import soot.Body;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;

public class JNIAnalyzer {
	
	public static HashSet<ArrayList<SootMethod>> paths =  CallgraphWrapper.findJNIPaths();
	
	public static void analyze(CallGraph cg) {
		HashSet<SootMethod> jniSMs = collectJNIMethods(cg);
		
		// Phase-1: we get the permission-guarded JNI methods
		System.out.println(" -->> " + "Phase-1: Start");
		HashMap<SootMethod, HashSet<String>> jni2permissions = checkPermission(cg, jniSMs);
		System.out.println(" <<-- " + "Phase-1: Finish");
		// Phase-2: we get the UID-guarded JNI methods
		System.out.println(" -->> " + "Phase-2: Start");
		HashMap<SootMethod, HashSet<String>> jni2uids = checkUID(cg, jniSMs);
		System.out.println(" <<-- " + "Phase-2: Finish");
		// Phase-3 [IGNORE]: we get the UserID-guarded JNI methods
		// System.out.println(" -->> " + "Phase-3: Start");
		// HashSet<SootMethod> jni1userid = checkUserID(cg, jniSMs);
		// System.out.println(" <<-- " + "Phase-3: Finish");
		
		HashSet<SootMethod> guardJNISMs = new HashSet<SootMethod>();
		guardJNISMs.addAll(jni2permissions.keySet());
		guardJNISMs.addAll(jni2uids.keySet());
		// guardJNISMs.addAll(jni1userid);
		
		// write to file "jnimethods.txt"
		File jniFile = new File(Config.OutputJNIMethodFile);
		try {
			if (!jniFile.exists())
				jniFile.createNewFile();
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(jniFile, true));
			for (SootMethod jniSM : guardJNISMs) {
				System.out.println("[JNI method] " + jniSM.getSignature());
				bw.write(jniSM.getSignature() + "\n");
				if (jni2permissions.containsKey(jniSM)) {
					for (String permission : jni2permissions.get(jniSM)) {
						System.out.println("    " + "[Permission] " + permission);
						bw.write("    " + permission + "\n");
					}
				}
				if (jni2uids.containsKey(jniSM)) {
					for (String uid : jni2uids.get(jniSM)) {
						System.out.println("    " + "[UID] " + uid);
						bw.write("    " + uid + "\n");
					}
				}
				// if (jni1userid.contains(jniSM)) {
					// System.out.println("    " + "[UserId] " + "True");
					// bw.write("    " + "UserID" + "\n");
				// }
			}
			bw.flush();
			bw.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
	private static HashSet<SootMethod> collectJNIMethods(CallGraph cg) {
		// collect reachable JNI methods in CallGraph
		HashSet<SootMethod> jniSMs = new HashSet<SootMethod>(); // output
		
		for (ArrayList<SootMethod> path : paths) {
			SootMethod jniMethod = path.get(path.size() - 1);
			if (jniMethod.getDeclaringClass().getName().startsWith("android.util.")
			 || jniMethod.getDeclaringClass().getName().startsWith("android.os.")
			 || jniMethod.getDeclaringClass().getName().startsWith("android.net.")
			 || jniMethod.getDeclaringClass().getName().startsWith("android.content.res.")
			 || jniMethod.getDeclaringClass().getName().startsWith("android.graphics.")
			 || jniMethod.getDeclaringClass().getName().startsWith("android.database.")
			 || jniMethod.getDeclaringClass().getName().startsWith("com.android.internal.os.")
			 || jniMethod.getDeclaringClass().getName().startsWith("com.android.internal.content.")
			 || jniMethod.getDeclaringClass().getName().startsWith("com.android.server.security.")
			 || jniMethod.getDeclaringClass().getName().equals("android.view.Surface")
			 || jniMethod.getDeclaringClass().getName().equals("android.view.MotionEvent")
			 || jniMethod.getDeclaringClass().getName().equals("android.app.admin.SecurityLog")
			 || jniMethod.getDeclaringClass().getName().equals("android.hardware.HardwareBuffer"))
				continue;
			
			if (jniMethod.isPhantom())
				continue;
			if (jniMethod.isAbstract())
				continue;
			if (jniMethod.isNative())
				jniSMs.add(jniMethod);
		}
		
		// for (SootMethod sm : jniSMs)
			// System.out.println(sm.getSignature());
		
		return jniSMs;
	}
	
	// ---- //
			
	private static HashMap<SootMethod, HashSet<String>> checkPermission(CallGraph cg, HashSet<SootMethod> jniSMs) {
		HashMap<SootMethod, HashSet<String>> jni2permissions = new HashMap<SootMethod, HashSet<String>>(); // output
		
		for (SootMethod jniSM : jniSMs) {
			System.out.println("[JNI] " + jniSM.getSignature());
			
			HashSet<ArrayList<SootMethod>> jniPaths = new HashSet<ArrayList<SootMethod>>();
			for (ArrayList<SootMethod> path : paths) {
				SootMethod jniMethod = path.get(path.size() - 1);
				if (!jniMethod.getSignature().equals(jniSM.getSignature()))
					continue;
				if (!path.get(1).getDeclaringClass().getName().endsWith("$Stub$Proxy"))
					continue;
				if (path.size() == CallgraphWrapper.PATH_MAX_LENGTH) {
					// just ignore the abnormal cases
					continue;
				}
				{
					boolean ignore = false;
					HashSet<SootClass> safeSCs = new HashSet<SootClass>();
					SootClass serviceSC = path.get(2).getDeclaringClass();
					safeSCs.add(serviceSC);
					while (serviceSC.hasOuterClass()) {
						serviceSC = serviceSC.getOuterClass();
						safeSCs.add(serviceSC);
					}
					for (int i = 3; i < path.size(); i++) {
						SootClass other = path.get(i).getDeclaringClass();
						while (other.hasOuterClass()) {
							other = other.getOuterClass();
						}
						
						if (safeSCs.contains(other))
							continue;
						if ((other.getName().contains("Service"))
						&& !other.getName().contains("GnssManagerService")
						&& !other.getName().contains("BroadcastRadioService")) {
							ignore = true;
							break;
						}
					}
					
					if (ignore == true)
						continue;
				}
				
				jniPaths.add(path);
			}
			
			if (jniPaths.size() > 4) {
				// heuristic filter (??)
				continue;
			}
			
			int pathIdx = 0;
			for (ArrayList<SootMethod> path : jniPaths) {
				boolean ignore = false;
				for (SootMethod sm : path) {
					if (sm.getDeclaringClass().getName().startsWith("android.util.")) {
						ignore = true;
						break;
					}
				}
				if (ignore == true)
					continue;
				
				System.out.println(String.format("    " + "[Path-%d] length: %d", pathIdx, path.size()));
				for (SootMethod sm : path) {
					System.out.println("    " + "    " + sm.getSignature());
				}
				
				pathIdx += 1;
				
				// ---- //
				
				HashSet<String> permissions = new HashSet<String>();
				
				for (int srcIdx = 2; srcIdx < (path.size() - 1); srcIdx++) {
					SootMethod srcSM = path.get(srcIdx);
					SootMethod tgtSM = path.get(srcIdx + 1);
					Iterator<Edge> edgeIter = cg.edgesOutOf(srcSM);
					
					HashSet<Stmt> stmts = new HashSet<Stmt>();
					while (edgeIter.hasNext()) {
						Edge curEdge = edgeIter.next();
						if (curEdge.tgt().getSignature().equals(tgtSM.getSignature())) {
							Stmt curStmt = curEdge.srcStmt();
							stmts.add(curStmt);
						}
					}
					
					for (Stmt stmt : stmts) {
						for (Unit domUnit : getDominators(srcSM, stmt)) {
							// System.out.println("    " + "    " + "[Dom] " + domUnit);
							if (!((Stmt) domUnit).containsInvokeExpr())
								continue;
							
							Iterator<Edge> domEdgeIterator = cg.edgesOutOf(domUnit);
							
							while (domEdgeIterator.hasNext()) {
								Edge domEdge = domEdgeIterator.next();
								SootMethod domCalleeSM = domEdge.tgt();
								// System.out.println("    " + "    " + "     " + "[Dom-Callee] " + domCalleeSM.getSignature());
								// case-1
								if (PermissionCheckFinder.permissionCheckMethodCGSet.contains(domCalleeSM)) {
									if (!BinderPatcher.binderRemoteSet.contains(srcSM.getDeclaringClass())) {
										// System.out.println("    " + "    " + "[Ignore0] " + srcSM.getSignature());
										continue;
									}
									
									// System.out.println("    " + "    " + "[Found0] " + srcSM.getSignature());
									
									HashSet<String> localPermissions = new HashSet<String>();
									for (ValueBox pValue : domEdge.srcStmt().getUseAndDefBoxes()) {
										if (pValue.getValue() instanceof StringConstant) {
											String permission = ((StringConstant) pValue.getValue()).value;
											if (permission.contains(".permission.")) {
												localPermissions.add(permission);
											}
										}
										if (pValue.getValue() instanceof StaticFieldRef) {
											SootField permissionSF = ((StaticFieldRef) pValue.getValue()).getField();
											if (StaticFieldResolver.field2permission.containsKey(permissionSF)) {
												String permission = StaticFieldResolver.field2permission.get(permissionSF);
												localPermissions.add(permission);
											}
										}
									}
									if (domCalleeSM.isConcrete()) {
										for (Unit unit : domCalleeSM.retrieveActiveBody().getUnits()) {
											for (ValueBox pValue : unit.getUseAndDefBoxes()) {
												if (pValue.getValue() instanceof StringConstant) {
													String permission = ((StringConstant) pValue.getValue()).value;
													if (permission.contains(".permission.")) {
														localPermissions.add(permission);
													}
												}
												if (pValue.getValue() instanceof StaticFieldRef) {
													SootField permissionSF = ((StaticFieldRef) pValue.getValue()).getField();
													if (StaticFieldResolver.field2permission.containsKey(permissionSF)) {
														String permission = StaticFieldResolver.field2permission.get(permissionSF);
														localPermissions.add(permission);
													}
												}
											}
										}
									}
									
									for (String permission : localPermissions) {
										// System.out.println("    " + "    " + "    " + "[Permission] " + permission);
									}
									
									permissions.addAll(localPermissions);
									
									continue;
								}
								// case-2
								Iterator<Edge> calleeEdgeIterator = cg.edgesOutOf(domCalleeSM);
								while (calleeEdgeIterator.hasNext()) {
									Edge calleeEdge = calleeEdgeIterator.next();
									SootMethod calleeSM = calleeEdge.tgt();
									if (PermissionCheckFinder.permissionCheckMethodCGSet.contains(calleeSM)) {
										if (!BinderPatcher.binderRemoteSet.contains(srcSM.getDeclaringClass())) {
											// System.out.println("    " + "    " + "[Ignore1] " + srcSM.getSignature());
											continue;
										}
										
										// System.out.println("    " + "    " + "[Found1] " + srcSM.getSignature() + " ==> " + domCalleeSM.getSignature());
										
										HashSet<String> localPermissions = new HashSet<String>();
										for (ValueBox pValue : calleeEdge.srcStmt().getUseAndDefBoxes()) {
											if (pValue.getValue() instanceof StringConstant) {
												String permission = ((StringConstant) pValue.getValue()).value;
												if (permission.contains(".permission.")) {
													localPermissions.add(permission);
												}
											}
											if (pValue.getValue() instanceof StaticFieldRef) {
												SootField permissionSF = ((StaticFieldRef) pValue.getValue()).getField();
												if (StaticFieldResolver.field2permission.containsKey(permissionSF)) {
													String permission = StaticFieldResolver.field2permission.get(permissionSF);
													localPermissions.add(permission);
												}
											}
										}
										if (calleeSM.isConcrete()) {
											for (Unit unit : calleeSM.retrieveActiveBody().getUnits()) {
												for (ValueBox pValue : unit.getUseAndDefBoxes()) {
													if (pValue.getValue() instanceof StringConstant) {
														String permission = ((StringConstant) pValue.getValue()).value;
														if (permission.contains(".permission.")) {
															localPermissions.add(permission);
														}
													}
													if (pValue.getValue() instanceof StaticFieldRef) {
														SootField permissionSF = ((StaticFieldRef) pValue.getValue()).getField();
														if (StaticFieldResolver.field2permission.containsKey(permissionSF)) {
															String permission = StaticFieldResolver.field2permission.get(permissionSF);
															localPermissions.add(permission);
														}
													}
												}
											}
										}
										
										for (String permission : localPermissions) {
											// System.out.println("    " + "    " + "    " + "[Permission] " + permission);
										}
										
										permissions.addAll(localPermissions);
										
										continue;
									}
								}
							}
						}
					}
				}
				
				if (permissions.isEmpty()) {
					System.out.println("    " + "[Permission] NULL");
				} else {
					System.out.print("    " + "[Permission] ");
					int permIdx = 0;
					for (String permission : permissions) {
						if (permIdx == (permissions.size() - 1))
							System.out.print(permission);
						else
							System.out.print(permission + ", ");
						
						permIdx++;
					}
					System.out.print("\n");
					
					if (!jni2permissions.containsKey(jniSM))
						jni2permissions.put(jniSM, new HashSet<String>());
					jni2permissions.get(jniSM).addAll(permissions);
				}
			}
		}
		
		return jni2permissions;
	}
	
	// ---- //
	
	private static HashMap<SootMethod, HashSet<String>> checkUID(CallGraph cg, HashSet<SootMethod> jniSMs) {
		HashMap<SootMethod, HashSet<String>> jni2uids = new HashMap<SootMethod, HashSet<String>>(); // output
		
		for (SootMethod jniSM : jniSMs) {
			System.out.println("[JNI] " + jniSM.getSignature());
			
			HashSet<ArrayList<SootMethod>> jniPaths = new HashSet<ArrayList<SootMethod>>();
			for (ArrayList<SootMethod> path : paths) {
				SootMethod jniMethod = path.get(path.size() - 1);
				if (!jniMethod.getSignature().equals(jniSM.getSignature()))
					continue;
				if (!path.get(1).getDeclaringClass().getName().endsWith("$Stub$Proxy"))
					continue;
				if (path.size() == CallgraphWrapper.PATH_MAX_LENGTH) {
					// just ignore the abnormal cases
					continue;
				}
				{
					boolean ignore = false;
					HashSet<SootClass> safeSCs = new HashSet<SootClass>();
					SootClass serviceSC = path.get(2).getDeclaringClass();
					safeSCs.add(serviceSC);
					while (serviceSC.hasOuterClass()) {
						serviceSC = serviceSC.getOuterClass();
						safeSCs.add(serviceSC);
					}
					for (int i = 3; i < path.size(); i++) {
						SootClass other = path.get(i).getDeclaringClass();
						while (other.hasOuterClass()) {
							other = other.getOuterClass();
						}
						
						if (safeSCs.contains(other))
							continue;
						if ((other.getName().contains("Service"))
						&& !other.getName().contains("GnssManagerService")
						&& !other.getName().contains("BroadcastRadioService")) {
							ignore = true;
							break;
						}
					}
					
					if (ignore == true)
						continue;
				}
				
				jniPaths.add(path);
			}
			
			if (jniPaths.size() > 4) {
				// heuristic filter (??)
				continue;
			}
			
			int pathIdx = 0;
			for (ArrayList<SootMethod> path : jniPaths) {
				boolean ignore = false;
				for (SootMethod sm : path) {
					if (sm.getDeclaringClass().getName().startsWith("android.util.")) {
						ignore = true;
						break;
					}
				}
				if (ignore == true)
					continue;
				
				System.out.println("    " + String.format("[Path-%d] length: %d", pathIdx, path.size()));
				for (SootMethod sm : path) {
					System.out.println("    " + "    " + sm.getSignature());
				}
				
				pathIdx += 1;
				
				// ---- //
				
				HashSet<String> uids = new HashSet<String>();
				
				for (int srcIdx = 2; srcIdx < (path.size() - 1); srcIdx++) {
					SootMethod srcSM = path.get(srcIdx);
					SootMethod tgtSM = path.get(srcIdx + 1);
					Iterator<Edge> edgeIter = cg.edgesOutOf(srcSM);
					
					HashSet<Stmt> stmts = new HashSet<Stmt>();
					while (edgeIter.hasNext()) {
						Edge curEdge = edgeIter.next();
						if (curEdge.tgt().getSignature().equals(tgtSM.getSignature())) {
							Stmt curStmt = curEdge.srcStmt();
							stmts.add(curStmt);
						}
					}
					
					for (Stmt stmt : stmts) {
						for (Unit domUnit : getDominators(srcSM, stmt)) {
							// System.out.println("    " + "    " + "[Dom] " + domUnit);
							
							// case-1
							if (UIDCheckFinder.uidCheckMethod2Stmt.containsKey(srcSM)
							 && UIDCheckFinder.uidCheckMethod2Stmt.get(srcSM).contains(domUnit)) {
								String uid = UIDCheckFinder.uidCheckStmt2UID.get(domUnit);
								
								// System.out.println("    " + "    " + "[Found0] " + srcSM.getSignature());
								// System.out.println("    " + "    " + "    " + "[UID] " + uid);
								
								uids.add(uid);
								
								continue;
							}
							
							if (!((Stmt) domUnit).containsInvokeExpr())
								continue;
							
							Iterator<Edge> domEdgeIterator = cg.edgesOutOf(domUnit);
							while (domEdgeIterator.hasNext()) {
								Edge domEdge = domEdgeIterator.next();
								SootMethod domCalleeSM = domEdge.tgt();
								// case-2
								if (UIDCheckFinder.uidCheckMethodCGSet.contains(domCalleeSM)) {
									if (!BinderPatcher.binderRemoteSet.contains(srcSM.getDeclaringClass())) {
										// System.out.println("    " + "    " + "[Ignore1] " + srcSM.getSignature());
										continue;
									}
									
									// System.out.println("    " + "    " + "[Found1] " + srcSM.getSignature());
									
									HashSet<String> localUIDs = new HashSet<String>();
									for (Stmt uidCheckStmt : UIDCheckFinder.uidCheckMethod2Stmt.get(domCalleeSM)) {
										String uid = UIDCheckFinder.uidCheckStmt2UID.get(uidCheckStmt);
										// System.out.println("    " + "    " + "    " + "[UID] " + uid);
										localUIDs.add(uid);
									}
									
									// for (String uid : localUIDs) {
										// System.out.println("    " + "    " + "    " + "[UID] " + uid);
									// }
									
									uids.addAll(localUIDs);
									
									continue;
								}
							}
						}
					}
				}
				
				if (uids.isEmpty()) {
					System.out.println("    " + "[UID] NULL");
				} else {
					System.out.print("    " + "[UID] ");
					int permIdx = 0;
					for (String uid : uids) {
						if (permIdx == (uids.size() - 1))
							System.out.print(uid);
						else
							System.out.print(uid + ", ");
						
						permIdx++;
					}
					System.out.print("\n");
					
					if (!jni2uids.containsKey(jniSM))
						jni2uids.put(jniSM, new HashSet<String>());
					jni2uids.get(jniSM).addAll(uids);
				}
			}
		}
		
		return jni2uids;
	}
	
	// ---- //
	
	private static HashSet<SootMethod> checkUserID(CallGraph cg, HashSet<SootMethod> jniSMs) {
		HashSet<SootMethod> jni1userid = new HashSet<SootMethod>(); // output
		
		for (SootMethod jniSM : jniSMs) {
			System.out.println("[JNI] " + jniSM.getSignature());
			
			HashSet<ArrayList<SootMethod>> jniPaths = new HashSet<ArrayList<SootMethod>>();
			for (ArrayList<SootMethod> path : paths) {
				SootMethod jniMethod = path.get(path.size() - 1);
				if (!jniMethod.getSignature().equals(jniSM.getSignature()))
					continue;
				if (!path.get(1).getDeclaringClass().getName().endsWith("$Stub$Proxy"))
					continue;
				if (path.size() == CallgraphWrapper.PATH_MAX_LENGTH) {
					// just ignore the abnormal cases
					continue;
				}
				{
					boolean ignore = false;
					HashSet<SootClass> safeSCs = new HashSet<SootClass>();
					SootClass serviceSC = path.get(2).getDeclaringClass();
					safeSCs.add(serviceSC);
					while (serviceSC.hasOuterClass()) {
						serviceSC = serviceSC.getOuterClass();
						safeSCs.add(serviceSC);
					}
					for (int i = 3; i < path.size(); i++) {
						SootClass other = path.get(i).getDeclaringClass();
						while (other.hasOuterClass()) {
							other = other.getOuterClass();
						}
						
						if (safeSCs.contains(other))
							continue;
						if ((other.getName().contains("Service"))
						&& !other.getName().contains("GnssManagerService")
						&& !other.getName().contains("BroadcastRadioService")) {
							ignore = true;
							break;
						}
					}
					
					if (ignore == true)
						continue;
				}
				
				jniPaths.add(path);
			}
			
			if (jniPaths.size() > 4) {
				// heuristic filter (??)
				continue;
			}
			
			int pathIdx = 0;
			for (ArrayList<SootMethod> path : jniPaths) {
				boolean ignore = false;
				for (SootMethod sm : path) {
					if (sm.getDeclaringClass().getName().startsWith("android.util.")) {
						ignore = true;
						break;
					}
				}
				if (ignore == true)
					continue;
				
				System.out.println("    " + String.format("[Path-%d] length: %d", pathIdx, path.size()));
				for (SootMethod sm : path) {
					System.out.println("    " + "    " + sm.getSignature());
				}
				
				pathIdx += 1;
				
				// ---- //
				
				boolean hasUserIdCheck = false;
				
				for (int srcIdx = 2; srcIdx < (path.size() - 1); srcIdx++) {
					SootMethod srcSM = path.get(srcIdx);
					SootMethod tgtSM = path.get(srcIdx + 1);
					Iterator<Edge> edgeIter = cg.edgesOutOf(srcSM);
					
					HashSet<Stmt> stmts = new HashSet<Stmt>();
					while (edgeIter.hasNext()) {
						Edge curEdge = edgeIter.next();
						if (curEdge.tgt().getSignature().equals(tgtSM.getSignature())) {
							Stmt curStmt = curEdge.srcStmt();
							stmts.add(curStmt);
						}
					}
					
					for (Stmt stmt : stmts) {
						for (Unit domUnit : getDominators(srcSM, stmt)) {
							// System.out.println("    " + "    " + "[Dom] " + domUnit);
							
							// case-1
							if (UserIDCheckFinder.useridCheckMethod2Stmt.containsKey(srcSM)
							 && UserIDCheckFinder.useridCheckMethod2Stmt.get(srcSM).contains(domUnit)) {
								// System.out.println("    " + "    " + "[Found0] " + srcSM.getSignature());
								
								hasUserIdCheck = true;
								
								continue;
							}
							
							if (!((Stmt) domUnit).containsInvokeExpr())
								continue;
							
							Iterator<Edge> domEdgeIterator = cg.edgesOutOf(domUnit);
							while (domEdgeIterator.hasNext()) {
								Edge domEdge = domEdgeIterator.next();
								SootMethod domCalleeSM = domEdge.tgt();
								// case-2
								if (UserIDCheckFinder.useridCheckMethodCGSet.contains(domCalleeSM)) {
									if (!BinderPatcher.binderRemoteSet.contains(srcSM.getDeclaringClass())) {
										// System.out.println("    " + "    " + "[Ignore1] " + srcSM.getSignature());
										continue;
									}
									
									// System.out.println("    " + "    " + "[Found1] " + srcSM.getSignature());
									
									hasUserIdCheck = true;
									
									continue;
								}
							}
						}
					}
				}
				
				if (hasUserIdCheck == false) {
					System.out.println("    " + "[UserID] False");
				} else {
					System.out.println("    " + "[UserID] True");
					
					jni1userid.add(jniSM);
				}
			}
		}
		
		return jni1userid;
	}
	
	// ---- //
	
	private static HashSet<Unit> getDominators(SootMethod sm, Stmt stmt) {
		Body body = sm.retrieveActiveBody();
		BriefUnitGraph cfg = new BriefUnitGraph(body);
		MHGDominatorsFinder<Unit> domFinder = new MHGDominatorsFinder<Unit>(cfg);
		
		HashSet<Unit> domUnits = new HashSet<Unit>();
		
		List<Unit> domUnitFinds = null;
		try {
			domUnitFinds = domFinder.getDominators(stmt);
		} catch (NullPointerException npe) {
			return domUnits;
		}
		domUnits.addAll(domUnitFinds);
		
		// System.out.println(body);
		// System.out.println(stmt);
		
		Queue<Unit> queue = new LinkedList<Unit>();
		for (Unit domUnit : domFinder.getDominators(stmt)) {
			for (Unit preUnit : cfg.getPredsOf(domUnit)) {
				if (domUnits.contains(preUnit))
					continue;
				if (queue.contains(preUnit))
					continue;
				queue.add(preUnit);
			}
		}
		
		while (!queue.isEmpty()) {
			Unit curUnit = queue.poll();
			domUnits.add(curUnit);
			
			for (Unit domUnit : domFinder.getDominators(curUnit)) {
				if (domUnits.contains(domUnit))
					continue;
				if (queue.contains(domUnit))
					continue;
				queue.add(domUnit);
			}
		}
		
		// for (Unit domUnit : domUnits)
			// System.out.println("    " + "[Dom] " + domUnit);
		
		return domUnits;
	}
	
}
