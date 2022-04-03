package hk.polyu.soot.preprocess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;

import hk.polyu.Config;
import soot.G;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.options.Options;

public class SootInitializer {
	
	public static void init() {
		// clean up
		G.reset();
				
		// set soot environment
		Options.v().set_no_bodies_for_excluded(true);
		Options.v().set_allow_phantom_refs(true);
		Options.v().set_whole_program(true);
		Options.v().set_output_format(Options.output_format_none);
		Options.v().set_process_dir(Collections.singletonList(Config.OutputClassesDirectory));
		Options.v().set_src_prec(Options.src_prec_class);
		Options.v().set_keep_line_number(false);
		Options.v().set_keep_offset(false);
		Options.v().set_ignore_resolving_levels(true);
		Options.v().setPhaseOption("cg", "enabled:true");
		Options.v().setPhaseOption("cg.spark", "enabled:true");
		Options.v().setPhaseOption("cg.spark", "string-constants:true");
		        
		// exclude certain packages for better performance
		List<String> excludeList = new ArrayList<String>();
		excludeList.add("java.");
		Options.v().set_exclude(excludeList);
		Options.v().set_no_bodies_for_excluded(true);
		        
		Scene.v().loadNecessaryClasses();
				
		// ensure every SootClass instance has been resolved
		while (true) {
			try {
				// note: Scene.v().getClasses() may throw ConcurrentModificationException
				for (SootClass sc : Scene.v().getClasses()) {
					if (sc.isPhantom())
						continue;
					if (sc.getName().startsWith("java.")) {
						sc.setPhantomClass();
						for (SootMethod sm : sc.getMethods()) {
							sm.setPhantom(true);
						}
					} else {
						for (SootMethod sm : sc.getMethods()) {
							if (!sm.isConcrete())
								continue;
							sm.retrieveActiveBody();
						}
					}
				}
				break;
			} catch (ConcurrentModificationException e) { /* pass */ }
		}
				
		System.out.println(" -->> Soot Initialization Finish"); // debug
	}

}
