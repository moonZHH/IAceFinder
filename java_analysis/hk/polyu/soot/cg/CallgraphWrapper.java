package hk.polyu.soot.cg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class CallgraphWrapper {
	
	public static int PATH_MAX_LENGTH = 13; // (16-3)
	
	public static HashSet<ArrayList<SootMethod>> jniPaths = null;
	
	public static HashSet<ArrayList<SootMethod>> findJNIPaths() {
		// for speed-up
		if (jniPaths != null && !jniPaths.isEmpty())
			return jniPaths;
		
		if (jniPaths == null) {
			jniPaths = new HashSet<ArrayList<SootMethod>>();
		}
		
		CallGraph cg = Scene.v().getCallGraph();
		
		SootMethod mainSM = Scene.v().getMethod("<hk.polyu.Main: void main(java.lang.String[])>");
		ArrayList<SootMethod> curPath = new ArrayList<SootMethod>();
		curPath.add(mainSM);
		
		Iterator<Edge> edgeIter = cg.edgesOutOf(mainSM);
		while (edgeIter.hasNext()) {
			Edge edge = edgeIter.next();
			SootMethod tgt = edge.tgt();
			findJNIPathsInternal(cg, tgt, curPath);
		}
		
		/*
		try {
			File tmp = new File("tmp.txt");
			BufferedWriter bw = new BufferedWriter(new FileWriter(tmp));
			int idx = 0;
			for (ArrayList<SootMethod> path : jniPaths) {
				// System.out.println("[Path-" + idx + "]");
				bw.write("[Path-" + idx + "]" + "\n");
				for (SootMethod sm : path) {
					// System.out.println("\t" + sm.getSignature());
					bw.write("\t" + sm.getSignature() + "\n");
				}
				idx += 1;
			}
			bw.flush();
		} catch(Exception e) {
			// pass
		}
		*/
		
		return jniPaths;
	}
	
	private static void findJNIPathsInternal(CallGraph cg, SootMethod src, ArrayList<SootMethod> path) {
		// exceeds the maximum path length
		if (path.size() >= PATH_MAX_LENGTH)
			return;
		// contains more than one Binder transactions
		int binderCnt = 0;
		for (SootMethod sm : path) {
			SootClass sc = sm.getDeclaringClass();
			if (sc.getName().endsWith("$Stub$Proxy"))
				binderCnt += 1;
		}
		if (binderCnt > 1)
			return;
		if (binderCnt == 1 && src.getDeclaringClass().getName().endsWith("$Stub$Proxy"))
			return;
		// finds nested method call
		if (path.contains(src))
			return;
		
		// ---- //
		
		ArrayList<SootMethod> curPath = new ArrayList<SootMethod>(path);
		curPath.add(src);
		if (src.isNative() && !src.isPhantom() && binderCnt == 1) {
			jniPaths.add(curPath);
			return;
		}
		
		Iterator<Edge> edgeIter = cg.edgesOutOf(src);
		while (edgeIter.hasNext()) {
			Edge edge = edgeIter.next();
			SootMethod tgt = edge.tgt();
			findJNIPathsInternal(cg, tgt, curPath);
		}
	}

}
