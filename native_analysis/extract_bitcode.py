import networkx as nx
import os
import re
import shutil
import subprocess
import Queue

## Android 10
# aosp_out_dir = "/home/zhouhao/data_fast/android_0805/out/target/product/generic_x86"
# bitcode_dir = "/home/zhouhao/data_fast/SECURITY-2021/native_analysis/bitcode_aosp_0805"
# installed = "/home/zhouhao/data_fast/android_0805/out/target/product/generic_x86/installed-files.txt"
## Android 11
# aosp_out_dir = "/home/zhouhao/data_fast/android_1005/out/target/product/generic_x86"
# bitcode_dir = "/home/zhouhao/data_fast/SECURITY-2021/native_analysis/bitcode_aosp_1005"
# installed = "/home/zhouhao/data_fast/android_1005/out/target/product/generic_x86/installed-files.txt"
## Android 11
# aosp_out_dir = "/home/zhouhao/AOSP/aosp_11_0805/out/target/product/generic_x86"
# bitcode_dir = "/home/zhouhao/AOSP/NDSS21/native_analysis/bc_aosp_11_0805"
# installed = "/home/zhouhao/AOSP/aosp_11_0805/out/target/product/generic_x86/installed-files.txt"
## Android 12
aosp_out_dir = "/home/zhouhao/AOSP/aosp_12_0905/out/target/product/generic_x86"
bitcode_dir = "/home/zhouhao/AOSP/NDSS21/native_analysis/bc_aosp_12_0905"
installed = "/home/zhouhao/AOSP/aosp_12_0905/out/target/product/generic_x86/installed-files.txt"

#### ==== #### PRE-PROCESS #### ==== ####

'''
## STEP-1
so_paths = [] ## store the absolute path of each shared library and each native service

fp = open(installed, "r")
for line in fp.readlines():
    line = line.strip()
    ## library
    if line.endswith(".so"):
        if "/system/product/" in line:
            continue
    
        path = os.path.join(aosp_out_dir, line[line.find("/")+1:])
        if os.path.islink(path):
            continue
        if not ("/lib/" in path):
            continue
        assert os.path.exists(path)
        
        is_32bit = False
        command = "file %s" % (path)
        p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        for line in p.stdout.readlines():
            line = line.strip()
            # print line
            if "32-bit" in line:
                is_32bit = True
        if is_32bit == True:
            # print path
            so_paths.append(path)
    ## executable (NOTE: some native services are native executable applications)
    else:
        if not ("/system/bin/" in line):
            continue
        
        path = os.path.join(aosp_out_dir, line[line.find("/")+1:])
        if os.path.islink(path):
            continue
        assert os.path.exists(path)
        
        is_32bit = False
        command = "file %s" % (path)
        p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        for line in p.stdout.readlines():
            line = line.strip()
            # print line
            if "32-bit" in line:
                is_32bit = True
        
        is_service = False
        command = "llvm-objdump -p %s | grep \"NEEDED\"" % (path)
        p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        for line in p.stdout.readlines():
            line = line.strip()
            # print line
            if "libbinder.so" in line:
                is_service = True
        
        if is_32bit and is_service:
            if "app_process" in path:
                continue
            # print path + "(*)"
            so_paths.append(path)            
fp.close()

so_paths.sort()
# for so_path in so_paths:
    # print so_path
# exit()

##

soname2sopath = {} ## [so name] -> [so path]
for so_path in so_paths:
    if not so_path.endswith(".so"):
        so_name = so_path.split("/")[-1]
        if soname2sopath.has_key(so_name):
            print "[WARN] %s" % (so_name)
            assert True
        soname2sopath[so_name] = so_path
        continue
    
    so_name = so_path.split("/")[-1]
    if soname2sopath.has_key(so_name):
        if ("/system/lib/%s" % (so_name)) in soname2sopath[so_name]:
            continue ## we assume that shared libraries prefer to being placed in "/system/lib/" 
    
        if ("/system/lib/%s" % (so_name)) in so_path:
            # print "[UPDATE] %s -> %s" % (so_name, so_path)
            soname2sopath[so_name] = so_path
        elif ("/system/apex/com.android.runtime.debug/lib/%s" % (so_name)) in so_path:
            # print "[UPDATE] %s -> %s" % (so_name, so_path)
            soname2sopath[so_name] = so_path
        # for android 10
        elif "/vndk-28/" in soname2sopath[so_name]:
            if os.path.exists(soname2sopath[so_name].replace("/vndk-28/", "/vndk-29/")):
                # print "[UPDATE] %s -> %s" % (so_name, soname2sopath[so_name].replace("/vndk-28/", "/vndk-29/"))
                soname2sopath[so_name] = soname2sopath[so_name].replace("/vndk-28/", "/vndk-29/")
        elif "/vndk-sp-28/" in soname2sopath[so_name]:
            if os.path.exists(soname2sopath[so_name].replace("/vndk-sp-28/", "/vndk-sp-29/")):
                # print "[UPDATE] %s -> %s" % (so_name, soname2sopath[so_name].replace("/vndk-sp-28/", "/vndk-sp-29/"))
                soname2sopath[so_name] = soname2sopath[so_name].replace("/vndk-sp-28/", "/vndk-sp-29/")
        # for android 11-12
        elif ".vndk.v28" in soname2sopath[so_name]:
            if os.path.exists(soname2sopath[so_name].replace(".vndk.v28", ".vndk.current")):
                # print "[UPDATE] %s -> %s" % (so_name, soname2sopath[so_name].replace(".vndk.v28", ".vndk.current"))
                soname2sopath[so_name] = soname2sopath[so_name].replace(".vndk.v28", ".vndk.current")
        elif ".vndk.v29" in soname2sopath[so_name]:
            if os.path.exists(soname2sopath[so_name].replace(".vndk.v29", ".vndk.current")):
                # print "[UPDATE] %s -> %s" % (so_name, soname2sopath[so_name].replace(".vndk.v29", ".vndk.current"))
                soname2sopath[so_name] = soname2sopath[so_name].replace("vndk.v29", ".vndk.current")
        elif ".vndk.v30" in soname2sopath[so_name]:
            if os.path.exists(soname2sopath[so_name].replace(".vndk.v30", ".vndk.current")):
                # print "[UPDATE] %s -> %s" % (so_name, soname2sopath[so_name].replace(".vndk.v30", ".vndk.current"))
                soname2sopath[so_name] = soname2sopath[so_name].replace("vndk.v30", ".vndk.current")
        else:
            # print "[IGNORE] %s -> %s" % (so_name, so_path)
            pass
    else:
        # print "%s -> %s" % (so_name, so_path)
        soname2sopath[so_name] = so_path

# for so_name in soname2sopath.keys():
    # so_path = soname2sopath[so_name]
    # print "%s -> %s" % (so_name, so_path)

soname2sopath_fp = open("soname2sopath.txt", "w")
for so_name in soname2sopath.keys():
    so_path = soname2sopath[so_name]
    soname2sopath_fp.write("%s -> %s\n" % (so_name, so_path))
soname2sopath_fp.flush()
soname2sopath_fp.close()

##

soname2depname = {} ## [so name] -> [dep so name]
for so_name in soname2sopath.keys():
    # print so_name
    so_path = soname2sopath[so_name]
    so_deps = []
    dep_queue = Queue.Queue()
    dep_queue_util = set()
    ## put direct dependent libraries into dep_queue
    command = "llvm-objdump -p %s | grep \"NEEDED\"" % (so_path)
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.replace("NEEDED", "")
        line = line.strip()
        if soname2sopath.has_key(line):
            # print "    " + line
            dep_queue.put(line)
            dep_queue_util.add(line)
        else:
            print "  [UNKNOWN] " + line
            assert False
    ## put indirect dependent libraries into dep_queue
    while not dep_queue.empty():
        cur_so = dep_queue.get()
        if cur_so in so_deps:
            continue
        if not soname2sopath.has_key(cur_so):
            continue
        so_deps.append(cur_so)
        # cur_path = soname2sopath[cur_so]
        # command = "llvm-objdump -p %s | grep \"NEEDED\"" % (cur_path)
        # p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        # for line in p.stdout.readlines():
            # line = line.replace("NEEDED", "")
            # line = line.strip()
            # if soname2sopath.has_key(line):
                # # print "    " + line
                # if not (line in so_deps) and not (line in dep_queue_util):
                    # dep_queue.put(line)
                    # dep_queue_util.add(line)
            # else:
                # print "  [UNKNOWN] " + line
                # assert False
        
    if soname2depname.has_key(so_name):
        assert False
    soname2depname[so_name] = so_deps

# for so_name in soname2depname.keys():
    # print so_name
    # so_deps = soname2depname[so_name]
    # for so_dep in so_deps:
        # print "    => " + so_dep

soname2depname_fp = open("soname2depname.txt", "w")
for so_name in soname2depname.keys():
    so_deps = soname2depname[so_name]
    for so_dep in so_deps:
        soname2depname_fp.write("%s -> %s\n" % (so_name, so_dep))
soname2depname_fp.flush()
soname2depname_fp.close()

exit()
'''

#'''
soname2sopath = {} ## [so name] -> [so path]
soname2sopath_fp = open("soname2sopath.txt", "r")
for line in soname2sopath_fp.readlines():
    line = line.strip()
    so_name = line.split(" -> ")[0].strip()
    so_path = line.split(" -> ")[1].strip()
    assert not soname2sopath.has_key(so_name)
    soname2sopath[so_name] = so_path
soname2sopath_fp.close()

soname2depname = {} ## [so name] -> [dep so name]
soname2depname_fp = open("soname2depname.txt", "r")
for line in soname2depname_fp.readlines():
    line = line.strip()
    so_name = line.split(" -> ")[0].strip()
    if not soname2depname.has_key(so_name):
        soname2depname[so_name] = []
    so_dep = line.split(" -> ")[1].strip()
    soname2depname[so_name].append(so_dep)
soname2depname_fp.close()
#'''

#### ==== #### ==== ####

so_tests = [ 
             # "libmedia_jni.so",
             # "libandroid_servers.so",
             # "libandroid_runtime.so",
             
             # "surfaceflinger",
             # "libsurfaceflinger.so",
             # "libSurfaceFlingerProp.so",
             
             # "audioserver",
             # "libaudioflinger.so",
             # "libaudiopolicyservice.so"
             # "libaaudioservice.so",
             
             # "libcameraservice.so",
           ]

'''
## STEP-2 (I. extract-bc)
for so_name in soname2sopath.keys():
    if len(so_tests) > 0 and not (so_name in so_tests):
        continue

    so_path = soname2sopath[so_name]
    bc_path = so_path.replace(aosp_out_dir, bitcode_dir) + ".bc"
    if not os.path.exists(os.path.dirname(bc_path)):
        os.makedirs(os.path.dirname(bc_path))
    
    # NOTE: it is possible that some native libraries may not have the ".llvm_bc" ELF section (should raraely happen)
    command = "extract-bc --output %s %s" % (bc_path, so_path)
    print "extrace-bc -> " + so_path
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line

exit()
'''

'''
## STEP-2 (II. llvm-dis)
for so_name in soname2sopath.keys():
    if len(so_tests) > 0 and not (so_name in so_tests):
        continue

    so_path = soname2sopath[so_name]
    bc_path = so_path.replace(aosp_out_dir, bitcode_dir) + ".bc"
    if not os.path.exists(bc_path):
        continue
    ll_path = so_path.replace(aosp_out_dir, bitcode_dir) + ".ll"
    
    command = "llvm-dis %s -o=%s" % (bc_path, ll_path)
    print "llvm-dis -> " + bc_path
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line

exit()
'''

'''
## For Debug Purpose ##
## STEP-2 (III. llvm-link)
ignores = [ "libc.so", "libc++.so", "libstdc++.so", "libm.so", "libz.so", 
            "libdl.so", "libdl_android.so", "ld-android.so", 
            "libbpf.so", "libbpf_android.so", "libnetdbpf.so", 
            "libbacktrace.so", "libunwindstack.so", 
            "liblog.so", 
            "libEGL.so", "libGLESv1_CM.so", "libGLESv2.so", "libGLESv3.so", 
            "libhwui.so" ]
for so_name in soname2sopath.keys():
    if not (so_name in so_tests):
        continue

    ## TODO: cannot handle libhwui.so
    if so_name in ignores:
        continue
    if not soname2depname.has_key(so_name):
        continue
        
    ## TODO: correct?
    so_deps = soname2depname[so_name]
    if not ("libbinder.so" in so_deps):
        continue
    
    print so_name
    so_path = soname2sopath[so_name]
    ll_path = so_path.replace(aosp_out_dir, bitcode_dir) + ".ll"
    assert os.path.exists(ll_path)
    command = ("llvm-link --only-needed %s" % (ll_path))
    for so_dep in so_deps:
        if so_dep in ignores:
            print "    [IGNORE] " + so_dep
            continue
        print "    " + so_dep
        so_dep_path = soname2sopath[so_dep]
        ll_dep_path = so_dep_path.replace(aosp_out_dir, bitcode_dir) + ".ll"
        if os.path.exists(ll_dep_path):
            command = "%s %s" % (command, ll_dep_path)
    link_output = so_path.replace(aosp_out_dir, bitcode_dir) + ".lnk"
    command = "%s -o %s" % (command, link_output)
    print command
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line
    
exit()
'''

'''
## STEP-2 (III. llvm-link)
binder_sonames = set()
binder_sonames.add("libbinder.so")
for so_name in soname2sopath.keys():
    if not (so_name in soname2depname.keys()):
        continue
    so_deps = soname2depname[so_name]
    if "libbinder.so" in so_deps:
        binder_sonames.add(so_name)

for so_name in soname2sopath.keys():
    if len(so_tests) > 0 and not (so_name in so_tests):
        continue

    if not (so_name in soname2depname.keys()):
        continue
    so_deps = soname2depname[so_name]
    if not ("libbinder.so" in so_deps):
        continue
    
    print so_name
    so_path = soname2sopath[so_name]
    ll_path = so_path.replace(aosp_out_dir, bitcode_dir) + ".ll"
    assert os.path.exists(ll_path)
    command = ("llvm-link --only-needed %s" % (ll_path))
    for so_dep in so_deps:
        if not (so_dep in binder_sonames) and not (so_dep == "libutils.so"):
            print "  [IGNORE] " + so_dep
            continue
        #if so_dep == "libandroid_runtime.so":
            #print "  [IGNORE] " + so_dep
            #continue
        if so_dep == "libhwui.so":
            print "  [IGNORE] " + so_dep
            continue
        ## test -->>
        # if so_dep == "libbinder.so":
            # print "  [IGNORE] " + so_dep
            # continue
        ## test <<--
        print "  " + so_dep
        so_dep_path = soname2sopath[so_dep]
        ll_dep_path = so_dep_path.replace(aosp_out_dir, bitcode_dir) + ".ll"
        if os.path.exists(ll_dep_path):
            command = "%s %s" % (command, ll_dep_path)
    link_output = so_path.replace(aosp_out_dir, bitcode_dir) + ".lnk"
    command = "%s -o %s" % (command, link_output)
    print command
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line
    
exit()
'''

'''
## STEP-2 (IV. opt)
for so_name in soname2sopath.keys():
    if len(so_tests) > 0 and not (so_name in so_tests):
        continue

    so_path = soname2sopath[so_name]
    link_path = so_path.replace(aosp_out_dir, bitcode_dir) + ".lnk"
    if not os.path.exists(link_path):
        continue
    print link_path
    opt_path = so_path.replace(aosp_out_dir, bitcode_dir) + ".opt"
    
    command = "opt --mem2reg %s -o %s" % (link_path, opt_path)
    print command
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line

exit()
'''

'''
## STEP-2 (V. llvm-dis)
for so_name in soname2sopath.keys():
    if len(so_tests) > 0 and not (so_name in so_tests):
        continue

    so_path = soname2sopath[so_name]
    opt_path = so_path.replace(aosp_out_dir, bitcode_dir) + ".opt"
    if not os.path.exists(opt_path):
        continue
    print opt_path
    dis_path = so_path.replace(aosp_out_dir, bitcode_dir) + ".dis"
    
    command = "llvm-dis %s -o=%s" % (opt_path, dis_path)
    print command
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line

exit()
'''

#### ==== #### ==== ####

#'''
## STEP-3
OOM_list = [ "libhwui.so" ]
for so_name in soname2sopath.keys():
    if len(so_tests) > 0 and not (so_name in so_tests):
        continue
    
    # if not ("surfaceflinger" in so_name):
        # continue
    # if not ("libsurfaceflinger.so" in so_name):
        # continue
    # if not ("libgui.so" in so_name):
        # continue
    # if not ("libinputflinger.so" in so_name):
        # continue
    
    # if not ("libaudiopolicyservice.so" in so_name):
        # continue
    # if not ("gpuservice" in so_name):
        # continue
        
    # if not ("audioserver" in so_name):
        # continue
        
    if so_name in OOM_list:
        continue
    
    so_path = soname2sopath[so_name]
    dis_path = so_path.replace(aosp_out_dir, bitcode_dir) + ".dis"
    if not os.path.exists(dis_path):
        continue
    cg_path = so_path.replace(aosp_out_dir, bitcode_dir) + ".cg"
    svfg_path = so_path.replace(aosp_out_dir, bitcode_dir) + ".svfg"
    if os.path.exists(cg_path) and os.path.exists(svfg_path):
        continue
    dis_size = round(os.path.getsize(dis_path) / float(1024 * 1024), 2)
    # if dis_size >= 36.0:
        # continue
    print "%s, %f" % (dis_path, dis_size)
    
    # command = "wpa --fspta --vcall-cha --dump-callgraph --dump-svfg %s" % (dis_path) # memory-consuming
    command = "wpa --ander --svfg --vcall-cha --dump-callgraph --dump-icfg --dump-inst --dump-svfg %s" % (dis_path)
    # command = "wpa --ander --svfg --v-call-cha --dump-callgraph --dump-icfg --dump-vfg %s" % (dis_path)
    # print command
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line
        if "Aborted (core dumped)" in line:
            print "[ERROR] [ERROR] [ERROR] " + so_name
    command = "sync"
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line
    
    ## move the generated callgraph_final.dot, svfg_final.dot, icfg_final.dot
    cg_src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "callgraph_final.dot")
    cg_tgt = so_path.replace(aosp_out_dir, bitcode_dir) + ".cg"
    if os.path.exists(cg_src):
        shutil.copy(cg_src, cg_tgt)
    svfg_src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "svfg_final.dot")
    svfg_tgt = so_path.replace(aosp_out_dir, bitcode_dir) + ".svfg"
    if os.path.exists(svfg_src):
        shutil.copy(svfg_src, svfg_tgt)
    icfg_src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "icfg_final.dot")
    icfg_tgt = so_path.replace(aosp_out_dir, bitcode_dir) + ".icfg"
    if os.path.exists(icfg_src):
        shutil.copy(icfg_src, icfg_tgt)
    
    command = "sync"
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line
    command = "rm callgraph_*"
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line
    command = "rm svfg_*"
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line
    command = "rm icfg_*"
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line
    command = "sync"
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in p.stdout.readlines():
        line = line.strip()
        print line
        
    # break

exit()
#'''

