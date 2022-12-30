# A standard procedure for merge (office) gatk repository to Schaudge:gatk4

## Step 1: From your project repository, check out a new branch and test the changes.
### 默认情况下 git checkout -b 将基于当前 HEAD 指向的分支创建新分支. 
### git checkout -b new-branch existing-branch
### git checkout 命令后面跟着 existing-branch 参数，然后新分支基于该指定的分支而不是当前 HEAD.
```
git checkout -b broadinstitute-master master(已存在本地分支名)
```
### git pull使用给定的参数运行git fetch，并调用git merge将检索到的分支头(master)合并到当前分支(broadinstitute-master)中. 
### 使用--rebase，它运行git rebase而不是git merge.
### 命令git pull <远程仓库地址> <远程分支名>:<本地分支名>
```
git pull https://github.com/broadinstitute/gatk.git master(:broadinstitute-master)
```
#### 参考提示如下 ...
#### 接收对象中: 100% (568/568), 257.96 KiB | 236.00 KiB/s, 完成.
#### 处理 delta 中: 100% (292/292), 完成 148 个本地对象.
#### 来自 https://github.com/broadinstitute/gatk
####  * branch                master     -> FETCH_HEAD
#### 删除 src/main/java/org/broadinstitute/hellbender/utils/codecs/FeaturesHeader.java
#### 自动合并 src/main/java/org/broadinstitute/hellbender/tools/walkers/mutect/Mutect2Engine.java
#### 自动合并 src/main/java/org/broadinstitute/hellbender/tools/walkers/haplotypecaller/AssemblyBasedCallerUtils.java
#### *** !!! 冲突（内容）：合并冲突于 src/main/java/org/broadinstitute/hellbender/tools/walkers/haplotypecaller/AssemblyBasedCallerUtils.java
#### 删除 scripts/travis/install_gcloud.sh
#### 删除 scripts/travis/install_and_authenticate_to_gcloud.sh
#### 删除 scripts/travis/check_for_pull_request
#### 删除 scripts/travis/README.md
#### 删除 .travis.yml
#### 自动合并 .gitignore
#### *** !!! 冲突（内容）：合并冲突于 .gitignore
#### 自动合并失败，修正冲突然后提交修正的结果。


## Step 2: manual correct the expected modification and commit
```
git status
```
#### 您有尚未合并的路径。
####  （解决冲突并运行 "git commit"）
####  （使用 "git merge --abort" 终止合并）
#### ...
#### 未合并的路径：
#### （使用 "git add <文件>..." 标记解决方案）
####	双方修改：   .gitignore
####	双方修改：   src/main/java/org/broadinstitute/hellbender/tools/walkers/haplotypecaller/AssemblyBasedCallerUtils.java
#### *** !!! 人工校正 <<<<<<< (当前分支冲突内容) ======= (远程冲突内容) >>>>>>> 相关冲突内容
```
git add .
git commit -m "merge description"
```

## Step 3: Merge the changes and update on GitHub.
### 切换回 master 分支，并将分支 (broadinstitute-master) 内容合并到 master 分支
```
git checkout master
git merge --no-ff broadinstitute-master
```
### push 到远程仓库
```
git push origin master
```



## Step 4: (optional) delelte new created temp branch
### remove the (local) branch broadinstitute-master
```
git branch -d broadinstitute-master 
```

