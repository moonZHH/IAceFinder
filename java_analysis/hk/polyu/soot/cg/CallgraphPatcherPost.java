package hk.polyu.soot.cg;

public class CallgraphPatcherPost {
	
	public static void patch() {
		// patch the confusing edges brought by BinderPatcher
		BinderPatcher.patchPost();
		// patch the confusing edges brought by android.os.Handler
		HandlerPatcher.patchPost();
		// patch the confusing edges brought by android.os.Parcel
		ParcelPatcher.patchPost();
		// patch edges to <init> and <clinit>
		ConstructorPatcher.patchPost();
		// patch invalid edges introduced by the "run" method
		RunPatcher.patchPost();
		// patch miscellaneous invalid edges
		InvalidEdgePatcher.patchPost();
		// patch the missing edges
		EdgePatcher.patchPost();
	}

}
