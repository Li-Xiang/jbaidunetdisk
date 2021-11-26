package org.littlestar.jbaidunetdisk;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.jline.reader.LineReader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class BaiduNetDiskHelper {
	public static final String APP_NAME_KEY   = "AppName";
	public static final String APP_KEY_KEY    = "AppKey";
	public static final String SECRET_KEY_KEY = "SecretKey";
	public static final String ACCESS_TOKEN_KEY = "access_token";
	public static final String REFRESH_TOKEN_KEY = "refresh_token";
	private static final String USER_DIR = System.getProperty("user.dir") + File.separator;
	private static final String  DOWNLOAD_DIR = USER_DIR + "downloads";
	private static String appFilePath = USER_DIR  + "app.properties";
	private static String authFilePath = USER_DIR + "authorize.properties";
	
	/**
	 * rtype: 文件命名策略，默认0
	 *   0 为不重命名，返回冲突
	 *   1 为只要path冲突即重命名
	 *   2 为path冲突且block_list不同才重命名
	 *   3 为覆盖
	 *   
	 * @param accessToken
	 * @param localFile
	 * @param rType
	 * @return
	 * @throws Exception
	 */
	public static String preCreateNetDiskFile(String accessToken, String localPath, String uploadPath, String rType, String blockList) throws Exception {
		File file = new File(localPath);
		Long bytes = Files.size(file.toPath());
		String fileSize = bytes.toString();
		String isDir = file.isDirectory() ? "1" : "0";
		JsonObject response = BaiduNetDiskApiHelper.preCreateNetDiskFile(accessToken, uploadPath, fileSize, isDir, rType,
				blockList);
		int errno = response.get("errno").getAsInt();
		String uploadId = "";
		if (errno == 0) {
			uploadId = response.get("uploadid").getAsString();
		} else {
			throw new Exception(response.toString());
		}
		return uploadId;
	}
	
	public static void upload(String appName, String accessToken, String localPath, String rType) throws Exception {
		File localFile = new File(localPath);
		if(!localFile.exists()) {
			String msg = "指定的文件不存在, 请检查路径是否正确, Windows平台'\\'字符使用'\\\\'来禁止转移,如'c:\\\\dir\\\\hello.txt'.";
			throw new FileNotFoundException(msg) ;
		}
		if(localFile.isDirectory()) {
			List<File> files = 
					Files.walk(Paths.get(localFile.toURI()))
					.filter(Files::isRegularFile)
					.map(Path::toFile)
					.collect(Collectors.toList());
			for(File subFile : files) {
				String uploadPath = convertToUploadFileName(appName, localFile, subFile);
				String subFilePath = subFile.getAbsolutePath();
				UploadThread uploadThread = new UploadThread(accessToken, subFilePath, uploadPath);
				UploadCoordinator.coordinator().submit(uploadThread);
			}
		} else {
			String uploadPath = convertToUploadFileName(appName, localFile);
			UploadThread uploadThread = new UploadThread(accessToken, localFile.getAbsolutePath(), uploadPath);
			UploadCoordinator.coordinator().submit(uploadThread);
		}
	}
	
	public static final int MAX_SLICE_SIZE = 4 * 1024 * 1024; 
	//
	public static String getFileBlockList(String localFile) throws Exception {
		String blockList = "";
		try (final RandomAccessFile randomAccessFile = new RandomAccessFile(localFile, "r")) {
			final FileChannel fileChannel = randomAccessFile.getChannel();
			final MappedByteBuffer fileMeppedBuffer = fileChannel.map(MapMode.READ_ONLY, 0, randomAccessFile.length());
			byte[] buffer;
			long fileSize = randomAccessFile.length();
			//// 要上传文件没有超过单个分片大小固定, 使用固定MAX_SLICE_SIZE大小进行分片, 并计算每个分片的MD5值。
			long currPos = 0;
			while (true) {
				long remainBytes = fileSize - currPos;
				if (remainBytes > MAX_SLICE_SIZE) {
					buffer = new byte[MAX_SLICE_SIZE];
					fileMeppedBuffer.get(buffer);
					String md5 = getMd5Digest(buffer);
					blockList += "\"" + md5 + "\",";
					currPos += MAX_SLICE_SIZE;
				} else {
					int lastBytes = (int) remainBytes;
					buffer = new byte[lastBytes];
					fileMeppedBuffer.get(buffer);
					String md5 = getMd5Digest(buffer);
					blockList += "\"" + md5 + "\",";
					break;
				}
			}
		}
		if (blockList.endsWith(",")) {
			blockList = blockList.substring(0, blockList.length() - 1);
		}
		blockList = "[" + blockList + "]";
		return blockList;
	}
	
	public static String getMd5Digest(byte[] bytes) throws Exception {
		MessageDigest md5Digest = MessageDigest.getInstance("MD5");
		md5Digest.update(bytes);
		byte[] rawMd5 = md5Digest.digest();
		//String md5 = new String(Hex.encodeHex(rawMd5));
		String md5 = getHexString(rawMd5);
		return md5;
	}
	
	public static String getHexString(byte[] bytes) {
		StringBuilder stringBuilder = new StringBuilder("");
		if (bytes == null || bytes.length <= 0) {
			return null;
		}
		for (int i = 0; i < bytes.length; i++) {
			int v = getUInt(bytes[i]);
			String hv = Integer.toHexString(v);
			if (hv.length() < 2) {
				stringBuilder.append(0);
			}
			stringBuilder.append(hv);
		}
		return stringBuilder.toString();
	}

	/**
	 * Get Unsigned Integer of giving byte.
	 * 
	 * @param b
	 * @return
	 */
	private static int getUInt(final byte b) {
		int value = b & 0xFF;
		return value;
	}
	
	public static String getMd5(final File file) throws Exception {
		MessageDigest MD5 = MessageDigest.getInstance("MD5");
		try (InputStream fileInputStream = Files.newInputStream(file.toPath())) {
			byte[] buffer = new byte[8192];
			int length;
			while ((length = fileInputStream.read(buffer)) != -1) {
				MD5.update(buffer, 0, length);
			}
			return new String(Hex.encodeHex(MD5.digest()));
		}
	}
	
	/**
	 * 列出云盘当前路径下的文件。
	 * 
	 * @param accessToken
	 * @param lsCommand 
	 * @param currentDir
	 * @param lineReader
	 * @throws Exception
	 */
	public static void listDir(String accessToken, String targetFile, String currentDir) throws Exception {
		final String newDir = buildNetDiskFilePath(currentDir, targetFile);
		if (!Objects.equals(newDir, "/")) {
			BaiduNetDiskFile targetNetDiskFile = BaiduNetDiskApiHelper.getBaiduNetDiskFile(accessToken, newDir);
			if (Objects.isNull(targetNetDiskFile)) {
				throw new Exception("无法找到目录/文件: " + newDir);
			} else {
				int isDir = targetNetDiskFile.getIsdir();
				// 是一个文件, 打印文件信息. 如果是目录, 遍历该目录信息...
				if (isDir == 0) {
					Application.stdOutput(BaiduNetDiskFile.getFormattedOutputHeader());
					Application.stdOutput(targetNetDiskFile.getFormattedOutput());
					return;
				}
			}
		}
		Application.stdOutput("获取目录文件列表: " + newDir);
		ArrayList<BaiduNetDiskFile> fileList = BaiduNetDiskApiHelper.getFileList(accessToken, newDir);

		Application.stdOutput(BaiduNetDiskFile.getFormattedOutputHeader());
		for (BaiduNetDiskFile file : fileList) {
			Application.stdOutput(file.getFormattedOutput());
		}
	}
	
	public static JsonObject delete(String accessToken, String targetFile, String currentDir) throws Exception {
		String newDir  = buildNetDiskFilePath(currentDir, targetFile);
		if (Objects.equals(newDir, "/")) {
			throw new Exception("不支持删除根目录.");
		}
		String fileList = "[\"" + newDir + "\"]";
		JsonObject response = BaiduNetDiskApiHelper.fileManager(accessToken, BaiduNetDiskApiHelper.FILE_OPERA_DELETE, 1,
				fileList, "fail");
		return response;
	}
	
	public static JsonObject copy(String accessToken, String source, String dest, String currentDir) throws Exception {
		String sourcePath = buildNetDiskFilePath(currentDir, source);
		String destPath = buildNetDiskFilePath(currentDir, dest);
		String fileList = buildCopyMoveFileList(sourcePath, destPath);
		JsonObject response = BaiduNetDiskApiHelper.fileManager(accessToken, BaiduNetDiskApiHelper.FILE_OPERA_COPY, 1,
				fileList, "fail");
		return response;
	}
	
	public static JsonObject move(String accessToken, String source, String dest, String currentDir) throws Exception {
		String sourcePath = buildNetDiskFilePath(currentDir, source);
		String destPath = buildNetDiskFilePath(currentDir, dest);
		String fileList = buildCopyMoveFileList(sourcePath, destPath);
		JsonObject response = BaiduNetDiskApiHelper.fileManager(accessToken, BaiduNetDiskApiHelper.FILE_OPERA_MOVER, 1,
				fileList, "fail");
		return response;
	}
	
	public static JsonObject rename(String accessToken, String source, String newName, String currentDir) throws Exception {
		String sourcePath = buildNetDiskFilePath(currentDir, source);
		String fileList = "[{\"path\":\"" + sourcePath + "\",\"newname\":\"" + newName + "\"}]";
		JsonObject response = BaiduNetDiskApiHelper.fileManager(accessToken, BaiduNetDiskApiHelper.FILE_OPERA_RENAME, 1,
				fileList, "fail");
		return response;
	}
	
	public static String changeDir(String accessToken, String targetFile, String currentDir)
			throws Exception {
		//// 根据当前目录和命令输入的目录，构建目标目录的绝对路径。
		String newDir  = buildNetDiskFilePath(currentDir, targetFile);
		//// 检查目录路径是否合法。
		if (!Objects.equals(newDir, "/")) {
			BaiduNetDiskFile targetNetDiskFile = BaiduNetDiskApiHelper.getBaiduNetDiskFile(accessToken, newDir);
			if (Objects.isNull(targetNetDiskFile)) {
				Application.stdOutput("无法找到目录: " + newDir);
			} else {
				int isDir = targetNetDiskFile.getIsdir();
				// 是一个文件
				if (isDir == 0) {
					Application.stdOutput("改变目录失败: '" + newDir + "'是一个文件...");
					return currentDir;
				} else {
					return newDir;
				}
			}
		}
		return "/";
	}
	
	public static void download(String accessToken, String targetFile, String currentDir)
			throws Exception {
		String newDir = buildNetDiskFilePath(currentDir, targetFile);
		ArrayList<BaiduNetDiskFile> fileList = BaiduNetDiskApiHelper.getBaiduNetDiskFiles(accessToken, newDir);
		for (BaiduNetDiskFile file : fileList) {
			//// 构建本地下载目录。
			String serverFileName = file.getServerFilename();
			String serverFilePath = file.getPath();
			// 1. 去掉文件名，获得路径。
			serverFilePath = serverFilePath.substring(0, serverFilePath.length() - serverFileName.length());
			
			// 2. 将网盘当前路径替换为本地下载目录。
			String downloadDir = DOWNLOAD_DIR;
			// 添加子目录, 如果有
			if (serverFilePath.length() > 1) {
				downloadDir = downloadDir + File.separator + serverFilePath.substring(currentDir.length(), serverFilePath.length() - 1);
			}
			downloadDir += File.separator;
			DownloadThread downloadThread = new DownloadThread(accessToken, file, downloadDir, 0);
			DownloadCoordinator.coordinator().submit(downloadThread);
		}
	}
	
	/**
	 * 初始化访问令牌, 如果本地没有缓存则发起申请。
	 * 
	 * @param lineReader
	 * @param clientId
	 * @param secretKey
	 * @return 
	 * @throws Exception
	 */
	public static Properties initAuthorizeToken(final LineReader lineReader, String clientId, String secretKey)
			throws Exception {
		Properties authToken = null;
		File authFile = new File(authFilePath);
		if (!authFile.exists()) {
			authToken = requestAuthorizeToken(lineReader, clientId, secretKey, authFile);
		} else {
			authToken = getAuthorizeToken(authFile);
		}
		return authToken;
	}
	
	/**
	 * 读取本地百度云盘访问令牌。
	 * 
	 * @param authFile
	 * @return
	 * @throws Exception
	 */
	private static Properties getAuthorizeToken(File authFile) throws Exception {
		final Properties authToken = new Properties();
		try (Reader reader = Files.newBufferedReader(authFile.toPath())) {
			authToken.load(reader);
		} catch (Exception e) {
			throw new Exception("读取访问令牌失败, 请检查授权配置文件: " + authFile.getAbsolutePath(), e);
		}
		String accessToken = authToken.getProperty(ACCESS_TOKEN_KEY);
		String refreshToken = authToken.getProperty(REFRESH_TOKEN_KEY);
		if (isEmpty(accessToken) || isEmpty(refreshToken)) {
			throw new Exception(ACCESS_TOKEN_KEY + "和" + REFRESH_TOKEN_KEY + "不能为空, 请检查授权配置文件。");
		}
		return authToken;
	}
	
	/**
	 * 引导用户请求百度网盘访问授权(refresh_token和access_token), 并保存到本地配置文件。
	 * 
	 * @param lineReader
	 * @param clientId
	 * @param secretKey
	 * @return 获取的refresh_token和access_token.
	 * @throws Exception
	 */
	private static Properties requestAuthorizeToken(final LineReader lineReader, String clientId, String secretKey,
			File authFile) throws Exception {
		final Properties authToken = new Properties();
		lineReader.printAbove("百度网盘小程序需要授权并获取授权码才能访问你的网盘, 请通过浏览器打开以下链接进行授权:");
		lineReader.printAbove(BaiduNetDiskApiHelper.getAuthorizeUrl(clientId));
		String authCode = lineReader.readLine("请输入返回的授权码: ");
		if (isEmpty(authCode)) {
			throw new Exception("授权码为空。");
		}
		lineReader.printAbove("正在获取访问令牌(access_token)...");
		JsonObject response = null;
		try {
			response = BaiduNetDiskApiHelper.getAccessToken(clientId, secretKey, authCode);
		} catch (Exception e) {
			throw new Exception("获取访问令牌(access_token)失败.", e);
		}
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		lineReader.printAbove(gson.toJson(response));
		// 如果返回的是错误信息而非授权信息
		if (!Objects.isNull(response.get("error"))) {
			String error = response.get("error").getAsString();
			throw new Exception("获取访问令牌(access_token)失败: " + error);
		}
		// 保存访问令牌到本地配置文件
		String accessToken = response.get(ACCESS_TOKEN_KEY).getAsString();
		String refreshToken = response.get(REFRESH_TOKEN_KEY).getAsString();
		authToken.setProperty(ACCESS_TOKEN_KEY, accessToken);
		authToken.setProperty(REFRESH_TOKEN_KEY, refreshToken);
		try (Writer writer = Files.newBufferedWriter(authFile.toPath())) {
			authToken.store(writer, "");
			lineReader.printAbove("访问令牌已经保存成功: " + authFile.getAbsolutePath());
		} catch (Exception e) {
			throw new Exception("访问令牌保存到" + authFile.getAbsolutePath() + "失败.", e);
		}
		return authToken;
	}
	
	private static Properties requestAppInfo(final LineReader lineReader, final File appFile) throws Exception {
		final Properties appInfo = new Properties();
		lineReader.printAbove("需要百度网盘小程序的AppKey和SecretKey信息. 如果你还没有, 可以通以下链接申请:");
		lineReader.printAbove("https://pan.baidu.com/union/home");
		lineReader.printAbove("请输入百度网盘小程序信息");
		String appName = lineReader.readLine("  AppName: ");
		if (isEmpty(appName)) {
			throw new Exception("AppName输入为空。");
		}
		appInfo.put(APP_NAME_KEY, appName);
		
		String appKey = lineReader.readLine("  AppKey: ");
		if (isEmpty(appKey)) {
			throw new Exception("AppKey输入为空。");
		}
		appInfo.put(APP_KEY_KEY, appKey);
		String secretKey = lineReader.readLine("  SecretKey: ");
		if (isEmpty(secretKey)) {
			throw new Exception("SecretKey输入为空。");
		}
		appInfo.put(SECRET_KEY_KEY, secretKey);
		try (Writer writer = Files.newBufferedWriter(appFile.toPath())) {
			appInfo.store(writer, "");
			lineReader.printAbove("百度网盘小程序信息保存成功: " + appFile.getAbsolutePath());
		} catch (Exception e) {
			throw new Exception("百度网盘小程序信息保存到" + appFile.getAbsolutePath() + "失败.", e);
		}
		return appInfo;
	}
	
	private static Properties getAppInfo(final LineReader lineReader, final File appFile) throws Exception {
		final Properties appInfo = new Properties();
		try (Reader reader = Files.newBufferedReader(appFile.toPath())) {
			appInfo.load(reader);
		} catch (Exception e) {
			throw new Exception("读取百度网盘小程序信息失败, 请检查配置文件: " + appFile.getAbsolutePath(), e);
		}
		String appName = appInfo.getProperty(APP_NAME_KEY);
		String clientId = appInfo.getProperty(APP_KEY_KEY);
		String secretKey = appInfo.getProperty(SECRET_KEY_KEY);
		if (isEmpty(appName) || isEmpty(clientId) || isEmpty(secretKey)) {
			throw new Exception(APP_NAME_KEY + ", " + APP_KEY_KEY + "和" + SECRET_KEY_KEY + "不能为空, 请检查APP配置文件。");
		}
		return appInfo;
	}
	
	/**
	 * 读取配置文件获取百度网盘小程序信息, 如果配置文件不存在, 引导用户录入小程序信息。
	 * 
	 * @param lineReader
	 * @return 返回获取的百度网盘小程序信息。
	 * @throws Exception
	 */
	public static Properties initAppInfo(final LineReader lineReader) throws Exception {
		Properties baiduAppInfo = new Properties();
		File appFile = new File(appFilePath);
		if (!appFile.exists()) {
			baiduAppInfo = requestAppInfo(lineReader, appFile);
		} else {
			baiduAppInfo = getAppInfo(lineReader, appFile);
		}
		return baiduAppInfo;
	}
	
	//// 网盘路径相关方法。
	/**
	 * <pre>
	 * 基于当前路径和一个给定的后缀构建一个新的路径。该方法主要服务于ls, cd等命令。
	 *   - 如果给定的后缀路径是一个绝对路径, 返回这个绝对路径;
	 *   - 如果给定的后缀路径是一个相对路径(非/开头), 则与当前路径进行拼接成新的绝对路径;
	 *   
	 * </pre>
	 * @param currentDir 当前绝对路径
	 * @param suffixPath 需要拼接在当前路径后的相对路径或绝对路径
	 * @return 新构建的绝对路径
	 */
	public static String buildNetDiskFilePath(String currentDir, String suffixPath) {
		if (isEmpty(currentDir)) {
			return "/";
		} 
		String newDir = currentDir;
		if (!isEmpty(suffixPath)) {
			suffixPath = suffixPath.trim();
			// 如果指定的是绝对路径, 则显示指定的绝对路径.
			if (suffixPath.startsWith("/")) {
				newDir = suffixPath;
			} else if (suffixPath.equals("..")) {
				// 支持suffixPath="..", 返回当前目录的上级目录;
				newDir = getNetDiskFileParent(currentDir);
			} else if (suffixPath.startsWith("./")) {
				// 支持suffixPath="./dir1"格式路径, 返回当前目录+/dir1目录; 
				if (currentDir.endsWith("/")) {
					newDir = suffixPath.replaceFirst("./", currentDir);
				} else {
					newDir = suffixPath.replaceFirst("./", currentDir + "/");
				}
			} else {
				if (currentDir.endsWith("/")) {
					newDir = currentDir + suffixPath;
				} else {
					newDir = currentDir + "/" + suffixPath;
				}
			}
		}
		// 百度网盘不支持以"/"结尾的目录格式, 如/dir1/要转换成/dir1
		while (newDir.length() > 1 && newDir.endsWith("/")) {
			newDir = newDir.substring(0, newDir.length() - 1);
		}
		return newDir;
	}
	
	/**
	 * 构建move/copy需要的filelist的json串。
	 * 
	 * @param sourcePath 需要被move/copy的源文件/目录.
	 * @param destPath 需要被move/copy的目的文件/目录, 如果是目录, 需要以'/'结尾标识.
	 * @return
	 */
	private static String buildCopyMoveFileList(String sourcePath, String destPath) {
		String newName = "";
		String destDir = "";
		if (destPath.endsWith("/")) {
			newName = getNetDiskFileName(sourcePath);
			destDir = getNetDiskFileParent(destPath);
		} else {
			newName = getNetDiskFileName(destPath);
			destDir = getNetDiskFileParent(destPath);
		}
		String fileList = "[{" 
				+ "\"path\":\"" + sourcePath + "\"," 
				+ "\"dest\":\"" + destDir + "\","
				+ "\"newname\":\"" + newName + "\","
				+ "\"ondup\":\"fail\"}]";
		return fileList;
	}

	/**
	 * 获取给定的网盘路径的父目录, 仅支持绝对路径。
	 * @param filePath
	 * @return
	 */
	public static String getNetDiskFileParent(String path) {
		path = path.trim();
		int lastIndex = path.lastIndexOf("/");
		String parent = "/";
		if (lastIndex > 0) {
			parent = path.substring(0, lastIndex);
		}
		return parent;
	}

	/**
	 * 获取给定网盘路径的目录名/文件名, 仅支持绝对路径。
	 * 
	 * @param path
	 * @return
	 */
	public static String getNetDiskFileName(String path) {
		int lastIndex = path.lastIndexOf("/");
		String fileName = path.substring(lastIndex, path.length())
				.replaceFirst("/", "");
		return fileName;
	}
	
	/**
	 * 初始化下载目录, 目录位于程序当前路径下的downloads.
	 * 
	 * @return
	 * @throws Exception
	 */
	public static File initDownloadDir() throws Exception {
		return initDir(DOWNLOAD_DIR);
	}
	
	/**
	 * 将本地文件转换成网盘的路径。
	 * @param appName
	 * @param file
	 * @return
	 */
	private static String convertToUploadFileName(String appName, final File file) {
		String fileName = file.getName();
		String uploadPath = "/apps/" + appName + "/" + fileName;
		return uploadPath.trim();
	}
	
	/**
	 * 根据网盘的约定, 将给定目录下的文件路径转换成要上传的网盘的路径。
	 * 
	 * @param appName 应用名称
	 * @param dir     所在的本地目录绝对路径
	 * @param file    本地文件的绝对路径
	 * @return 上传网盘的路径
	 */
	private static String convertToUploadFileName(String appName, final File dir, final File file) {
		/*
		 * 目录下的文件路径, 去掉目录路径, 就是目录下的路径;
		 * 网盘对应的路径为/apps/应用名称/目录名称/目录下的路径
		 */
		String dirPath = dir.getAbsolutePath();
		String dirName = dir.getName();
		String subFilePath = file.getAbsolutePath();
		subFilePath = subFilePath.substring(dirPath.length(), subFilePath.length());
		subFilePath = subFilePath.replace("\\", "/");
		if (subFilePath.startsWith("/")) {
			subFilePath = subFilePath.substring(1, subFilePath.length());
		}
		String uploadPath = "/apps/" + appName + "/" +dirName+"/"+ subFilePath ;
		return uploadPath.trim();
	}
	
	/**
	 * 初始化指定的本地目录，如目录不存在，则尝试创建。
	 * @param path
	 * @return
	 */
	public static File initDir(String path) {
		File dir =  new File(path);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		return dir;
	}
	
	public static boolean isEmpty(String s) {
		return BaiduNetDiskApiHelper.isEmpty(s);
	}
	
	/**
	 * 将给定字节数转换成跟利于用户阅读的格式(单位, 无KB/MB/GB)
	 * 
	 * @param bytes 字节数
	 * @param scale 保留小数点后几位.
	 * @return
	 */
	public static String bytesToPretty(long bytes, int scale) {
		String format = "%."+Math.abs(scale)+"f";
		long b = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
		 return b < 1024L ? bytes + " B"
		            : b <= 0xfffccccccccccccL >> 40 ? String.format(format+" KB", bytes / 0x1p10)
		            : b <= 0xfffccccccccccccL >> 30 ? String.format(format+" MB", bytes / 0x1p20)
		            : b <= 0xfffccccccccccccL >> 20 ? String.format(format+" GB", bytes / 0x1p30)
		            : b <= 0xfffccccccccccccL >> 10 ? String.format(format+" TB", bytes / 0x1p40)
		            : b <= 0xfffccccccccccccL ? String.format(format+" PB", (bytes >> 10) / 0x1p40)
		            : String.format(format+" EB", (bytes >> 20) / 0x1p40);
	}
}
