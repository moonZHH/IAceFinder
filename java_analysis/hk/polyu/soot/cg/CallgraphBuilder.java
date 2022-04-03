package hk.polyu.soot.cg;

import java.util.Collections;

import hk.polyu.soot.preprocess.SootInitializer;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class CallgraphBuilder {
	
	public static void build() {
		
	}
	
	public static void generateCallgraph(String binderProxy) {
		EntryPointCreator.specBinderProxyName = binderProxy;
		SootMethod entryPoint = EntryPointCreator.create();
		Scene.v().setEntryPoints(Collections.singletonList(entryPoint));
		PackManager.v().runPacks();
		System.out.println(" -->> Build Callgraph Finish");
	}
	
	// ---- ---- ---- ---- ---- //
	
	// module test
	public static void main(String[] args) {
		SootInitializer.init();
		
		// SootClass sc = Scene.v().getSootClassUnsafe("com.android.server.display.DisplayManagerService$BinderService", false);
		// SootMethod sm = sc.getMethodByName("createVirtualDisplay");
		
		// perform the analysis that does not rely on Callgraph
		// hk.polyu.soot.preprocess.PermissionCheckFinder.find();
		// hk.polyu.soot.preprocess.UidCheckFinder.find();
		
		//
		int classNumber = 0;
		int methodNumber = 0;
		for (SootClass sc : Scene.v().getClasses()) {
			// System.out.println(sc.getName());
			classNumber++;
			for (SootMethod sm : sc.getMethods()) {
				if (sm.isConcrete())
					methodNumber++;
			}
		}
		System.out.println("Class Number: " + classNumber);
		System.out.println("Method Number: " + methodNumber);
		// System.exit(0);
		//
		
		// build Callgraph
		CallgraphPatcherPre.patch();
		// generateCallgraph("lineageos.media.ILineageAudioService$Stub$Proxy");
		generateCallgraph("android.media.IAudioService$Stub$Proxy");
		// generateCallgraph("android.hardware.display.IDisplayManager$Stub$Proxy");
		// generateCallgraph("android.os.IVibratorService$Stub$Proxy");
		// generateCallgraph("android.hardware.IConsumerIrService$Stub$Proxy");
		// generateCallgraph("android.app.IActivityTaskManager$Stub$Proxy");
		CallgraphPatcherPost.patch();
		System.exit(0);
		
		/*
		for (Edge edge : Scene.v().getCallGraph()) {
			System.out.println(edge);
		}
		*/
		
		/*
		for (SootClass sc : Scene.v().getClasses()) {
			if (sc.getName().equals("android.app.UiAutomationConnection")) {
				System.out.println(sc);
				for (SootMethod sm : sc.getMethods()) {
					// if (sm.getSignature().equals("<com.android.server.display.DisplayManagerService: android.content.Context access$2100(com.android.server.display.DisplayManagerService)>")) {
					// if (sm.getName().equals("hasSystemFeature")) {
					// if (sm.getSignature().equals("<com.android.server.display.DisplayManagerService$BinderService: boolean checkCallingPermission(java.lang.String,java.lang.String)>")) {
					if (true) {
						System.out.println(sm.getSignature());
						if (sm.isConcrete())
							System.out.println(sm.retrieveActiveBody());
					}
				}
			}
		}
		*/
		
		// System.exit(0);
		
		// perform the analysis that relies on Callgraph
		CallGraph cg = Scene.v().getCallGraph();
		StaticFieldResolver.resolve(cg);
		PermissionCheckFinder.find(cg);
		UIDCheckFinder.find(cg);
		// UserIDCheckFinder.find(cg); // ignore currently
		
		JNIAnalyzer.analyze(cg);
		//
	}

}
