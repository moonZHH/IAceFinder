package hk.polyu.soot.cg;

import java.util.HashMap;

import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.jimple.toolkits.callgraph.CallGraph;

public class StaticFieldResolver {
	
	public static HashMap<SootField, String> field2permission = new HashMap<SootField, String>();
	
	public static void resolve(CallGraph cg) {
		// for AOSP
		SootClass aospSC = Scene.v().getSootClassUnsafe("android.Manifest$permission", false);
		if (!aospSC.isPhantom()) {
			for (SootField sf : aospSC.getFields()) {
				assert !field2permission.containsKey(sf);
				
				String permission = "android.permission." + sf.getName();
				field2permission.put(sf, permission);
			}
		}
		
		// for LineageOS
		/*
		SootClass lineageosSC = Scene.v().getSootClassUnsafe("lineageos.platform.Manifest$permission", false);
		if (!lineageosSC.isPhantom()) {
			for (SootField sf : lineageosSC.getFields()) {
				assert !field2permission.containsKey(sf);
				
				String permission = "lineageos.permission." + sf.getName();
				field2permission.put(sf, permission);
			}
		}
		*/
		
		// for OmniROM
		// do nothing for OmniROM (reuse the logic for AOSP)
		
		
		// debug
		/*
		for (SootField field : field2permission.keySet()) {
			String permission = field2permission.get(field);
			System.out.println("[" + field.toString() + "]" + " -->> " + permission);
		}
		*/
		
		System.out.println(" -->> Resolve Static Field Finish");
	}

}
