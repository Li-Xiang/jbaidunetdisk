## 百度网盘客户端(JBaiduNetDisk)
JBaiduNetDisk是一个基于Java语言开发的百度网盘客户端。

### 1. 特性(Features)
跨平台，支持字符交互界面，支持命令行参数。

### 2. 限制（Limits）
- 不支持断点上传/下载，不支持上传带中文名的本地文件/文件夹。
- 此外百度网盘开放平台对上传目录有限制，必须放在/apps/目录下，详细参考百度文档(https://pan.baidu.com/union)。
- 需要Java 8+。

### 3. 安装（Install）
#### 3.1. 使用发布压缩包
下载压缩包并进行解压到需要的目录即可。

#### 3.2. 通过mvn进行编译
```
$ git clone 
$ mvn package
$ ls ./target/jbaidunetdisk-1.0-jar-with-dependencies.jar
```
### 4. 使用 （Usage）

通过批处理或直接执行jar文件既可以执行。

```
runNetDisk.sh | runNetDisk.cmd
or
java -jar jbaidunetdisk.jar
```

首次执行，需要百度appid和获取用户授权。
#### 4.1. 配置app信息
要接入百度网盘，需要配置百度网盘app相关信息，如果你还没有，需要先注册申请。
百度网盘开放平台(https://pan.baidu.com/union/home) 
-> 控制台
-> 创建应用
可以通过程序提示进行输入。配置保存在程序目录下的app.properties文件中。
```
$ cat app.properties 
AppName=your_app_name       # 基本信息 -> 应用名称:
AppKey=your_app_key         # 密钥信息 -> AppKey:
SecretKey=your_secret_key   # 密钥信息 -> SecretKey:
```
#### 4.2. 获取用户授权
app需要授权(access_token)才能访问网盘，如果你没有配置，将跟你配置的app信息，生成百度的授权连接，复制连接到浏览器，根据提示进行授权，并输入授权码来获取访问授权。授权信息保存在程序目录下的authorize.properties配置文件.

#### 4.3. 用户交互模式
如果程序没有指定参数，初始化后将进入交互模式，键入help将获取使用帮助.
```
[YOUR_NET_DISK_NAME: /]: help
支持的命令列表:
  help     : 显示帮助。
  cd <Path>: 进入网盘指定的目录。
  ls [Path]: 列出指定的网盘目录下的文件列表，如果不指定显示当前网盘目录下的文件列表。
  download <Path> : 下载指定网盘路径下的文件/目录。
  upload <Path>   : 上传指定本地路径下的文件/目录到网盘，目前不支持带中文的路径。
  delete <Path>   : 删除指定的目录/文件(放入网盘回收站)。
  copy <source> <dest>      : 拷贝source目录/文件到dest位置。
  move <source> <dest>      : 移动source目录/文件到dest位置。
  rename <source> <newname> : 将source重命名为newname。
  print download  : 打印当前的下载列表。
  print upload    : 打印当前的上传列表。
  print quota     : 显示网盘的容量信息。
  print userinfo  : 显示当前网盘用户信息。
```
#### 4.4. 命令行模式
可以通过指定参数方式以命令行方式执行, --help可以查看命令行参数。
```
D:\github\jbaidunetdisk>runNetDisk.cmd --help
usage: jbaidunetdisk [option]
注 意:
  1. 如果不指定参数将进入交互模式;
  2. 除了-v,--verbose选项外, 其他选项(option)都是互斥的, 同时只能指定一个;
  3. 命令行模式仅支持绝对路径, 路径/文件名严格区分大小写;
  4. 如果路径/文件名如果包含空格, 需要使用双引号包含起来;

option:
 -c,--copy <soure> <dest>        拷贝<source>目录/文件到<dest>，需要指定绝对路径。
 -d,--download <path>            下载指定路径下的网盘文件或者目录, 需要指定绝对路径。
 -h,--help                       打印帮助。
 -l,--list <path>                列出指定路径下的网盘文件或者目录，需要指定绝对路径。
 -m,--move <soure> <dest>        移动<source>目录/文件到<dest>，需要指定绝对路径。
 -n,--rename <soure> <newname>   将<source>重命名为<newName>，需要指定绝对路径。
 -r,--refresh                    刷新Access Token。
 -u,--upload <path>              上传指定路径下的文件或者目录到网盘。
 -v,--verbose                    显示详细输出。
 -x,--delete <path>              删除指定<path>的目录/文件，需要指定绝对路径。

使用案例:
列出'/apps dir/'目录下的所有文件
$ jbaidunetdisk -l "/your dir"

下载'/apps dir/'目录下所有文件
$ jbaidunetdisk -d "/your dir"

```