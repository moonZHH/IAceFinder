import os
import shutil
import sys

# aosp_dir = "/home/zhouhao/data_fast/android_0805"
# log_path = "/home/zhouhao/data_fast/SECURITY-2021/patch_framework/aosp_0805.log"

# aosp_dir = "/home/zhouhao/data_fast/android_1005"
# log_path = "/home/zhouhao/data_fast/SECURITY-2021/patch_framework/aosp_1005.log"

# aosp_dir = "/home/zhouhao/AOSP/aosp_11_0805"
# log_path = "/home/zhouhao/AOSP/NDSS21/patch_framework/aosp_0805.log"

aosp_dir = "/home/zhouhao/AOSP/aosp_12_0905"
log_path = "/home/zhouhao/AOSP/NDSS21/patch_framework/aosp_0905.log"

# STEP-1
step1_error = False

dir2so = {}
fp1 = open(log_path, "r")
for line in fp1.readlines():
    line = line.strip()
    if (" link " in line) and (".so" in line):
        #if (("[x86" in line) or ("x86]" in line)) or (("[linux_glibc" in line) or ("linux_glibc]" in line)):
        if ("[linux_glibc" in line) or ("linux_glibc]" in line):
            continue
        # print line
        src = line.split("]")[1].split(" ")[1]
        tgt = line.split("]")[1].split(" ")[3]
        # print "    %s ==>> %s" % (src, tgt)
        
        if dir2so.has_key(src):
            if tgt == dir2so[src]:
                pass
            else:
                print "    pre:%s, cur:%s" % (dir2so[src], tgt)
                step1_error = True
        else:
            dir2so[src] = tgt
fp1.close()

if step1_error:
    #sys.exit(1)
    pass

'''
for src in dir2so.keys():
    tgt = dir2so[src]
    print "%s ==>> %s" % (src, tgt)
'''
    
# STEP-2
so2src = {}
so2head = {}
fp2 = open(log_path, "r")
fp2_lines = fp2.readlines()
line_idx = -1
for line in fp2_lines:
    line_idx += 1 # <- current line index
    line = line.strip()
    if (("clang " in line) or ("clang++ " in line)) and ("[" in line) and ("]" in line) and ("%" in line):
        #if (("[x86" in line) or ("x86]" in line)) or (("[linux_glibc" in line) or ("linux_glibc]" in line)):
        if ("[linux_glibc" in line) or ("linux_glibc]" in line):
            continue
        # print line
        src = line.split("]")[1].split(" ")[1]
        # tgt = line.split("]")[1].split(" ")[3]
        if not dir2so.has_key(src):
            continue
        so = dir2so[src]
        # print "    %s ==>> %s" % (src, so)
        
        if not so2src.has_key(so):
            so2src[so] = []
        if not so2head.has_key(so):
            so2head[so] = []
        
        line = fp2_lines[line_idx + 1].strip()
        assert line.startswith("INFO::")
        
        tgt = line.split(" ")[-1][:-1]
        
        src_path = os.path.join(aosp_dir, tgt)
        #if not os.path.exists(src_path): ## patch
            #src_path = src_path.replace("x86_64", "x86") ## patch
        assert os.path.exists(src_path)
        # print "    %s ==>> %s" % (so, src_path)
        if not (src_path in so2src[so]):
            so2src[so].append(src_path)
        
        for item in line.split(" "):
            if item.startswith("["):
                item = item[1:]
            if item.endswith("]"):
                item = item[:-1]
            if item.startswith("-I"):
                head_dir = os.path.join(aosp_dir, item[2:])
                if os.path.exists(head_dir):
                    for root, _, files in os.walk(head_dir):
                        for head_file in files:
                            if not head_file.endswith(".h"):
                                continue
                            
                            head_path = os.path.join(root, head_file).replace("/./", "/")
                            if os.path.exists(head_path):
                                if not (head_path in so2head[so]):
                                    so2head[so].append(head_path)
                else:
                    pass
fp2.close()

'''
for so in so2src.keys():
    print so
    for src in so2src[so]:
        print "    %s" % (src)
    for head in so2head[so]:
        print "    %s" % (head)
'''

# STEP-3
candidate_heads = []
for so in so2src.keys():
    for src in so2src[so]:
        if not (src in candidate_heads):
            candidate_heads.append(src)
    for head in so2head[so]:
        if not (head in candidate_heads):
            candidate_heads.append(head)

heads = []
pass_heads = []
for head in candidate_heads:
    if head in pass_heads:
        continue
        
    flag_cnt = 0
    fp_head = open(head, "r")
    for head_line in fp_head:
        head_line = head_line.strip()
        if "#include <binder/IInterface.h>" in head_line:
            flag_cnt += 1
        if (not ("class IInterface" in head_line)) and ("class " in head_line) and ("IInterface" in head_line):
            flag_cnt += 1
    fp_head.close()
        
    pass_heads.append(head)
    if flag_cnt >= 2:
        heads.append(head)
heads.sort()

'''
for head in heads:
    print head
sys.exit(1)
'''

'''
# STEP-4 (backup or recover)
head_idx = 0
for head in heads:
    if "_backup." in head: # for backup
    # if not ("_backup." in head): # for recover
        continue

    head_idx += 1
    
    print "%d: %s" % (head_idx, head)
    assert head.endswith(".h")
    
    shutil.copy(head, (head[:-2] + "_backup.h")) # for backup
    # shutil.copy(head, head.replace("_backup.h", ".h")) # for recover
exit()
'''

#'''
# STEP-4
head_idx = 0
for head in heads:
    if "_backup." in head:
        continue
    if os.path.islink(head):
        continue

    head_idx += 1
    print "%d: %s" % (head_idx, head)
    
    fp_head = open(head, "r")
    head_lines = fp_head.readlines()
    fp_head.close()
    
    fp_head = open(head, "w")
    insert_idx = -1
    line_idx = -1
    cnt = 0
    for line in head_lines:
        line_idx += 1
        fp_head.write(line)
        if insert_idx == line_idx:
            insert_stmt = "/* polyu */ int polyuIdentifier[%d];\n" % (head_idx * 7 + cnt)
            cnt += 1
            print "    %d: %s" % (head_idx, insert_stmt.strip())
            fp_head.write(insert_stmt)
            insert_idx = -1
        line = line.strip()
        if ("class " in line) and ("IInterface" in line):
            if "public:" in head_lines[line_idx + 1].strip():
                insert_idx = line_idx + 1
            elif "public:" in head_lines[line_idx + 2].strip():
                insert_idx = line_idx + 2
            elif "public:" in head_lines[line_idx + 3].strip():
                insert_idx = line_idx + 3
            elif "public:" in head_lines[line_idx + 4].strip():
                insert_idx = line_idx + 4
            elif "public:" in head_lines[line_idx + 5].strip():
                insert_idx = line_idx + 5
            elif "public:" in head_lines[line_idx + 6].strip():
                insert_idx = line_idx + 6
            elif "public:" in head_lines[line_idx + 7].strip():
                insert_idx = line_idx + 7
            elif "public:" in head_lines[line_idx + 8].strip():
                insert_idx = line_idx + 8
            elif "public:" in head_lines[line_idx + 9].strip():
                insert_idx = line_idx + 9
            elif "public:" in head_lines[line_idx + 10].strip():
                insert_idx = line_idx + 10
            else:
                assert False
    fp_head.flush()
    fp_head.close()
#'''

## NOTE: IApInterface.h, IClientInterface.h, IWifiScannerImpl.h


