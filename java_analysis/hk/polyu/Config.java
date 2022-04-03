package hk.polyu;

public class Config {
	
	public static int APILevel = 30;
	
	// AOSP (Android-10)
	/*
	public static String AOSPDirectory = "/home/zhouhao/data_fast/android_0805";
	public static String InstalledTxtFile = AOSPDirectory + "/out/target/product/generic_x86/installed-files.txt";
	public static String SystemAppDirectory = AOSPDirectory + "/out/target/common/obj/APPS";
	public static String FrameworkDirectory = AOSPDirectory + "/out/target/common/obj/JAVA_LIBRARIES";
	*/
	
	// AOSP (Android-11)
	/*
	public static String AOSPDirectory = "/home/zhouhao/data_fast/android_1005";
	public static String InstalledTxtFile = AOSPDirectory + "/out/target/product/generic_x86/installed-files.txt";
	public static String SystemAppDirectory = AOSPDirectory + "/out/target/common/obj/APPS";
	public static String FrameworkDirectory = AOSPDirectory + "/out/target/common/obj/JAVA_LIBRARIES";
	*/
	/*
	public static String AOSPDirectory = "/home/zhouhao/data_fast/android_1205";
	public static String InstalledTxtFile = AOSPDirectory + "/out/target/product/generic_x86/installed-files.txt";
	public static String SystemAppDirectory = AOSPDirectory + "/out/target/common/obj/APPS";
	public static String FrameworkDirectory = AOSPDirectory + "/out/target/common/obj/JAVA_LIBRARIES";
	*/
	/*
	public static String AOSPDirectory = "/home/zhouhao/AOSP/aosp_11_0805";
	public static String InstalledTxtFile = AOSPDirectory + "/out/target/product/generic_x86/installed-files.txt";
	public static String SystemAppDirectory = AOSPDirectory + "/out/target/common/obj/APPS";
	public static String FrameworkDirectory = AOSPDirectory + "/out/target/common/obj/JAVA_LIBRARIES";
	*/
	
	// AOSP (Android-12)
	public static String AOSPDirectory = "/home/zhouhao/AOSP/aosp_12_0905";
	public static String InstalledTxtFile = AOSPDirectory + "/out/target/product/generic_x86/installed-files.txt";
	public static String SystemAppDirectory = AOSPDirectory + "/out/target/common/obj/APPS";
	public static String FrameworkDirectory = AOSPDirectory + "/out/target/common/obj/JAVA_LIBRARIES";
	
	// LineageOS
	/*
	public static String AOSPDirectory = "/home/zhouhao/data/lineage-17.1";
	public static String InstalledTxtFile = AOSPDirectory + "/out/target/product/generic_x86/installed-files.txt";
	public static String SystemAppDirectory = AOSPDirectory + "/out/target/common/obj/APPS";
	public static String FrameworkDirectory = AOSPDirectory + "/out/target/common/obj/JAVA_LIBRARIES";
	*/
	
	// GrapheneOS
	/*
	public static String AOSPDirectory = "/home/zhouhao/data/grapheneos-11";
	public static String InstalledTxtFile = AOSPDirectory + "/out/target/product/generic_x86_64/installed-files.txt";
	public static String SystemAppDirectory = AOSPDirectory + "/out/target/common/obj/APPS";
	public static String FrameworkDirectory = AOSPDirectory + "/out/target/common/obj/JAVA_LIBRARIES";
	*/
	
	// OmniROM
	/*
	public static String AOSPDirectory = "/home/zhouhao/data/omnirom";
	public static String InstalledTxtFile = AOSPDirectory + "/out/target/product/generic_x86/installed-files.txt";
	public static String SystemAppDirectory = AOSPDirectory + "/out/target/common/obj/APPS";
	public static String FrameworkDirectory = AOSPDirectory + "/out/target/common/obj/JAVA_LIBRARIES";
	*/
	
	// crDroid
	/*
	public static String AOSPDirectory = "/home/zhouhao/data/crDroid";
	public static String InstalledTxtFile = AOSPDirectory + "/out/target/product/generic_x86_64/installed-files.txt";
	public static String SystemAppDirectory = AOSPDirectory + "/out/target/common/obj/APPS";
	public static String FrameworkDirectory = AOSPDirectory + "/out/target/common/obj/JAVA_LIBRARIES";
	*/
	
	public static String[] CommonCommand = {"/bin/sh", "-c"};
	
	public static String Workspace = "/home/zhouhao/CrossFrameworkAnalysis/JavaLayer";
	
	public static String CopyJarBash = Workspace + "/copy_jar_bash.sh";
	public static String ExtractJarBash = Workspace + "/extract_jar_bash.sh";
	
	public static String OutputClassesDirectory = Workspace + "/output/classes";
	public static String OutputClassListTxtFile = Workspace + "/output/class_list.txt";
	
	// ClassHierarchy.java
	// public static String OutputClassHierarchyTxtFile = Workspace + "/output/class_hierarchy.txt";
	
	// CallGraph.java
	// public static String OutputRawCallGraphTxtFile = Workspace + "/output/raw_callgraph.txt";
	
	// FindMessage.java
	// public static String OutputMessageTxtFile = Workspace + "/output/message.txt";
	
	// AnalyzeHandleMessage.java
	// public static String OutputHandleMessageSwitchTxtFile = Workspace + "/output/handle_message_switch.txt";
	
	// FindRPCMethod.java
	// public static String OutputRPCMethodTxtFile = Workspace + "/output/rpc_method.txt";
	
	// BuildBasicCallgraph.java
	// public static String OutputCallGraphTxtFile = Workspace + "/output/call_graph.txt";
	
	// ParseDex.java
	// public static String Dex2JarBash = "/home/zhouhao/dex2jar/d2j-dex2jar.sh";
	// public static String EnjarifyBash = "/home/zhouhao/enjarify/enjarify.sh";
	
	public static boolean DEBUG = false;
	
	//
	public static String OutputPermissionCheckFile = Workspace + "/output/permission_check.txt";
	public static String OutputUidCheckFile = Workspace + "/output/uid_check.txt";
	
	public static String OutputJNIMethodFile = Workspace + "/output/jnimethods.txt";
}
