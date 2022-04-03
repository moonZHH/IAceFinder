package hk.polyu;

import hk.polyu.soot.cg.CallgraphBuilder;
import hk.polyu.soot.cg.CallgraphPatcherPost;
import hk.polyu.soot.cg.CallgraphPatcherPre;
import hk.polyu.soot.cg.JNIAnalyzer;
import hk.polyu.soot.cg.PermissionCheckFinder;
import hk.polyu.soot.cg.StaticFieldResolver;
import hk.polyu.soot.cg.UIDCheckFinder;
import hk.polyu.soot.cg.UserIDCheckFinder;
import hk.polyu.soot.preprocess.SootInitializer;
import soot.Scene;
import soot.jimple.toolkits.callgraph.CallGraph;

public class MainCMD {
	
	public static void main(String[] args) {
		SootInitializer.init();
		
		Config.OutputJNIMethodFile = args[1];
		
		// perform the analysis that does not rely on Callgraph
		// hk.polyu.soot.preprocess.PermissionCheckFinder.find();
		// hk.polyu.soot.preprocess.UidCheckFinder.find();
		
		// build Callgraph
		CallgraphPatcherPre.patch();
		CallgraphBuilder.generateCallgraph(args[0]);
		CallgraphPatcherPost.patch();
		
		// perform the analysis that relies on Callgraph
		CallGraph cg = Scene.v().getCallGraph();
		StaticFieldResolver.resolve(cg);
		PermissionCheckFinder.find(cg);
		UIDCheckFinder.find(cg);
		// UserIDCheckFinder.find(cg);
		
		JNIAnalyzer.analyze(cg);
	}

}
