package hk.polyu.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

import hk.polyu.Config;

public class ExtractClasses {
	
	private static ArrayList<String> appJarList = new ArrayList<String>();
	private static ArrayList<String> frameworkJarList = new ArrayList<String>();
	
	public static void extract() {
		readInstalledTxtFile();
		showJarList();
		extractJar();
		createClassList();
	}
	
	private static void readInstalledTxtFile() {
		String filePath = Config.InstalledTxtFile;
		File installedTxtFile = new File(filePath);
		if (!installedTxtFile.exists()) {
			System.err.println("the path of \"installed-files.txt\" is invalid (input path: " + filePath + ")");
			System.err.println("exit ......");
			System.exit(0);
		}
		
		// read the file content
		try {
			BufferedReader br = new BufferedReader(new FileReader(installedTxtFile));
			String content = null;
			while ((content = br.readLine()) != null) {
				if (content.contains("/system/app") && content.endsWith(".apk")) {
					/*
					int index = content.split("/").length;
					String appName = content.split("/")[index - 1].split(".apk")[0];
					String appJarPath = Config.SystemAppDirectory + "/" + appName + "_intermediates/classes-full-debug.jar";
					File appJarFile = new File(appJarPath);
					if (appJarFile.exists()) {
						appJarList.add(appJarPath);
						copyJarFile(appName, appJarPath);
					} else {
						System.err.println("cannot find compiled classes for " + appName + " ......");
					}
					*/
				} else if (content.contains("/system/framework") && content.endsWith(".jar")) {
					int index = content.split("/").length;
					String jarName = content.split("/")[index - 1].split(".jar")[0];
					jarName = adjustJarName(jarName);
					String jarPath = Config.FrameworkDirectory + "/" + jarName + "_intermediates/classes.jar";
					File jarFile = new File(jarPath);
					if (jarFile.exists()) {
						frameworkJarList.add(jarPath);
						copyJarFile(jarName, jarPath);
					} else {
						System.err.println("cannot find compiled classes for " + jarName + " ......");
					}
				} else {
					// other cases
				}
			}
			br.close();
		} catch(FileNotFoundException fnfe) {
			fnfe.printStackTrace();
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
		
		/*
		// special issue: NFC (app) is not installed to generic target, thus not found in the file "installed-files.txt"
		String nfcJarPath = Config.SystemAppDirectory + "/" + "Nfc_intermediates/classes-full-debug.jar";
		File nfcJarFile = new File(nfcJarPath);
		if (nfcJarFile.exists()) {
			appJarList.add(nfcJarPath);
			copyJarFile("Nfc", nfcJarPath);
		} else {
			System.err.println("cannot find compiled classes for NFC ......");		
		}
		*/
	}
	
	private static String adjustJarName(String originName) {
		// for AOSP-10
		/*
		switch (originName) {
		case "monkey":
			return "monkeylib";
		case "input":
			return "inputlib";
		case "svc":
			return "svclib";
		default:
			break;
		}
		*/
		
		// for AOSP-11
		/*
		switch (originName) {
		case "framework":
			return "framework-minus-apex";
		case "monkey":
			return "monkeylib";
		default:
			break;
		}
		*/
		
		// for AOSP-12
		switch (originName) {
		case "framework":
			return "framework-minus-apex";
		default:
			break;
		}
		
		// for LineageOS_17_1
		/*
		switch (originName) {
		case "monkey":
			return "monkeylib";
		case "input":
			return "inputlib";
		case "svc":
			return "svclib";
		default:
			break;
		}
		*/
		
		// for GrapheneOS
		/*
		switch (originName) {
		case "framework":
			return "framework-minus-apex";
		case "monkey":
			return "monkeylib";
		default:
			break;
		}
		*/
		
		// for OmniROM
		/*
		switch (originName) {
		case "monkey":
			return "monkeylib";
		case "input":
			return "inputlib";
		case "svc":
			return "svclib";
		default:
			break;
		}
		*/
		
		// for crDroid
		/*
		switch (originName) {
		case "framework":
			return "framework-minus-apex";
		case "monkey":
			return "monkeylib";
		default:
			break;
		}
		*/
		
		return originName;
	}
	
	private static void copyJarFile(String jarName, String jarPath) {
		ArrayList<String> commands = new ArrayList<String>();
		commands.addAll(Arrays.asList(Config.CommonCommand));
		commands.add(String.format("%s %s %s", Config.CopyJarBash, Config.OutputClassesDirectory + "/" + jarName + ".jar", jarPath));
		BashRunner bash = new BashRunner(commands, false);
		bash.run();
	}
	
	private static void showJarList() {
		System.out.println("----    SYSTEM APP JAR LIST    ----");
		for (String jarPath : appJarList) {
			System.out.println("\t" + jarPath);
		}
		System.out.println("----    FRAMEWORK JAR LIST    ----");
		for (String jarPath : frameworkJarList) {
			System.out.println("\t" + jarPath);
		}
	}
	
	private static void extractJar() {
		File classOutputDir = new File(Config.OutputClassesDirectory);
		if (classOutputDir.exists() && classOutputDir.isDirectory()) {
			File[] jarFiles = classOutputDir.listFiles();
			for (File jarFile : jarFiles) {
				ArrayList<String> commands = new ArrayList<String>();
				commands.addAll(Arrays.asList(Config.CommonCommand));
				commands.add(String.format("%s %s %s", Config.ExtractJarBash, jarFile.getAbsolutePath(), Config.APILevel));
				BashRunner bash = new BashRunner(commands, false);
				bash.run();
			}
		} else {
			System.err.println("the path of Config.OutputClassesDirectory is invalid (input path: " + Config.OutputClassesDirectory + ")");
			System.err.println("exit ......");
			System.exit(0);
		}
	}
	
	private static void createClassList() {
		File classOutputDir = new File(Config.OutputClassesDirectory);
		if (!classOutputDir.exists() || !classOutputDir.isDirectory()) {
			System.err.println("the path of Config.OutputClassesDirectory is invalid (input path: " + Config.OutputClassesDirectory + ")");
			System.err.println("exit ......");
			System.exit(0);
		}
		
		ArrayList<String> classList = new ArrayList<String>();
		LinkedList<File> dirQueue = new LinkedList<File>();
		dirQueue.add(classOutputDir);
		while (!dirQueue.isEmpty()) {
			File dir = dirQueue.remove();
			File[] files = dir.listFiles();
			for (File file : files) {
				if (file.isDirectory()) {
					dirQueue.add(file);
				} else if (file.getAbsolutePath().endsWith(".class")) {
					String relativePath = file.getAbsolutePath().replace(Config.OutputClassesDirectory + "/", "");
					String replaceSlash = relativePath.replace("/", ".");
					String className = replaceSlash.replace(".class", "");
					classList.add(className);
				} else {
					// other cases
				}
			}
		}
		
		// write to file "class_list.txt"
		File classListFile = new File(Config.OutputClassListTxtFile);
		if (classListFile.exists())
			classListFile.delete();
		try {
			classListFile.createNewFile();
			BufferedWriter bw = new BufferedWriter(new FileWriter(classListFile));
			for(String className : classList) {
				bw.write(className + "\n");
			}
			bw.flush();
			bw.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
	// ---- ---- ---- ---- ---- //
	
	// module test
	public static void main(String[] args) {
		readInstalledTxtFile();
		showJarList();
		// extractJar();
		// createClassList();
	}

}
