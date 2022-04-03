package hk.polyu;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import hk.polyu.util.ExtractClasses;

public class Main {
	
	public static void main(String[] args) {
		init();
		
		ExtractClasses.extract();
	}
	
	private static void init() {
		System.gc();
		
		try {
			File outputDirectory = new File(Config.Workspace + "/output");
			if (!outputDirectory.exists())
				outputDirectory.mkdir();
		
			File outputClassesDirectory = new File(Config.OutputClassesDirectory);
			if (!outputClassesDirectory.exists()) {
				outputClassesDirectory.mkdir();
			} else {
				FileUtils.deleteDirectory(outputClassesDirectory);
				outputClassesDirectory.mkdir();
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

}
