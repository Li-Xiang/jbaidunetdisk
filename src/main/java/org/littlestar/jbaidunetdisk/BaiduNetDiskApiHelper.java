package org.littlestar.jbaidunetdisk;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class BaiduNetDiskApiHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger(BaiduNetDiskApiHelper.class);
	private static final CloseableHttpClient httpClient = HttpClients.createDefault();
	
	/**
	 * 根据AppKey生成申请授权的URL。
	 * @param clientId
	 * @return
	 */
	public static String getAuthorizeUrl(String clientId) {
		String format = "http://openapi.baidu.com/oauth/2.0/authorize?response_type=code&client_id=%s&redirect_uri=oob&scope=basic,netdisk";
		return String.format(format, clientId);
	}
	
	public static JsonObject getAccessToken(String clientId, String clientSecret, String code) throws Exception {
		URI apiUrl = new URIBuilder().setScheme("https").setHost("openapi.baidu.com").setPath("/oauth/2.0/token")
				.setParameter("grant_type", "authorization_code")
				.setParameter("code", code)
				.setParameter("client_id", clientId)
				.setParameter("client_secret", clientSecret)
				.setParameter("redirect_uri", "oob")
				.build();
		return doHttpGet(apiUrl);
	}
	
	public static JsonObject refreshAccessToken(String clientId, String clientSecret, String refreshToken)
			throws Exception {
		URI apiUrl = new URIBuilder().setScheme("https").setHost("openapi.baidu.com").setPath("/oauth/2.0/token")
				.setParameter("grant_type", "refresh_token")
				.setParameter("refresh_token", refreshToken)
				.setParameter("client_id", clientId)
				.setParameter("client_secret", clientSecret)
				.build();
		return doHttpGet(apiUrl);
	}
	
	/**
	 * 获取取用户的基本信息，包括账号、头像地址、会员类型等。
	 * 
	 * https://pan.baidu.com/rest/2.0/xpan/nas?method=uinfo&access_token=12.a6b7dbd428f731035f771b8d15063f61.86400.1292922000-2346678-124328
	 * 
	 * @return
	 * @throws Exception 
	 */
	public static JsonObject getUserInfo(String accessToken) throws Exception {
		URI apiUrl = new URIBuilder().setScheme("https").setHost("pan.baidu.com").setPath("/rest/2.0/xpan/nas")
				.setParameter("method", "uinfo")
				.setParameter("access_token", accessToken)
				.build();
		return doHttpGet(apiUrl);
	}
	
	public static JsonObject getDiskQuota(String accessToken) throws Exception {
		URI apiUrl = new URIBuilder().setScheme("https").setHost("pan.baidu.com").setPath("/api/quota")
				.setParameter("access_token", accessToken)
				.setParameter("checkfree", "1")
				.setParameter("checkexpire", "1")
				.build();
		return doHttpGet(apiUrl);
	}
	
	/**
	 * 获取文件列表
	 * @param accessToken
	 * @param dir
	 * @return
	 * @throws Exception
	 */
	public static ArrayList<BaiduNetDiskFile> getFileList(String accessToken, String dir) throws Exception {
		if (isEmpty(dir)) {
			dir = "/";
		}
		URI apiUrl = new URIBuilder().setScheme("https").setHost("pan.baidu.com").setPath("/rest/2.0/xpan/file")
				.setParameter("method", "list").setParameter("access_token", accessToken).setParameter("dir", dir)
				.build();
		JsonObject respone = doHttpGet(apiUrl);
		ArrayList<BaiduNetDiskFile> fileList = new ArrayList<>();
		if (Objects.isNull(respone)) {
			return fileList;
		}
		JsonArray jsonArray = respone.getAsJsonArray("list");
		for (JsonElement element : jsonArray) {
			String fileJson = element.toString();
			BaiduNetDiskFile file = new Gson().fromJson(fileJson, BaiduNetDiskFile.class);
			fileList.add(file);
		}
		return fileList;
	}
	
	/**
	 * 获取给定path下的所有网盘文件。
	 * 
	 * @param accessToken
	 * @param path
	 * @return
	 * @throws Exception
	 */
	public static ArrayList<BaiduNetDiskFile> getBaiduNetDiskFiles(String accessToken, String path) throws Exception {
		final ArrayList<BaiduNetDiskFile> files = new ArrayList<>();
		// 检查给定的path是否合法
		BaiduNetDiskFile targetPath = BaiduNetDiskApiHelper.getBaiduNetDiskFile(accessToken, path);
		if (Objects.isNull(targetPath)) {
			return files;
		}
		
		if (targetPath.isDir()) {
			// 如果给定的path是目录, 遍历目录内容.
			ArrayList<BaiduNetDiskFile> list = BaiduNetDiskApiHelper.getFileList(accessToken, path);
			for (BaiduNetDiskFile file : list) {
				// 如果是目录, 递归.
				if(file.isDir()) {
					String subDir = file.getPath();
					files.addAll(getBaiduNetDiskFiles(accessToken, subDir));
				} else {
					files.add(file);
				}
			}
		} else {
			files.add(targetPath);
		}
		return files;
	}
	
	/**
	 * 获取指定路径的文件或目录的信息, 百度云盘文档上没有看到相关接口, 
	 * 只能通过获取指定路径的父目录然后通过遍历方式获取。
	 * 
	 * 注意: 无法获取'/'目录的信息。
	 * 
	 * @param accessToken
	 * @param path 
	 * @return
	 * @throws Exception
	 */
	public static BaiduNetDiskFile getBaiduNetDiskFile(String accessToken, String path) throws Exception {
		path = path.trim();
		String parentDir = BaiduNetDiskHelper.getNetDiskFileParent(path);
		ArrayList<BaiduNetDiskFile> list = getFileList(accessToken, parentDir);
		for (BaiduNetDiskFile file : list) {
			if (Objects.equals(path, file.getPath())) {
				return file;
			}
		}
		return null;
	}
	
	public static final String FILE_OPERA_COPY   = "copy";
	public static final String FILE_OPERA_MOVER  = "move";
	public static final String FILE_OPERA_RENAME = "rename";
	public static final String FILE_OPERA_DELETE = "delete";

	public static JsonObject fileManager(String accessToken, String opera, Integer async, String filelist, String ondup) throws Exception {
		URI apiUrl = new URIBuilder().setScheme("https").setHost("pan.baidu.com").setPath("/rest/2.0/xpan/file")
				.setParameter("method", "filemanager")
				.setParameter("access_token", accessToken)
				.setParameter("opera", opera)
				.build();
		
		List<NameValuePair> nvps = new ArrayList<>();
		nvps.add(new BasicNameValuePair("async", async.toString()));
		nvps.add(new BasicNameValuePair("filelist", filelist));
		nvps.add(new BasicNameValuePair("ondup", ondup));
		HttpEntity httpEntity = new UrlEncodedFormEntity(nvps);

		JsonObject returnJsonObject = doHttpPost(apiUrl, httpEntity);
		return returnJsonObject;
	}
	
	public static JsonObject searchFile(String accessToken, String key, String dir) throws Exception {
		URI apiUrl = new URIBuilder().setScheme("https").setHost("pan.baidu.com").setPath("/rest/2.0/xpan/file")
				.setParameter("method", "search")
				.setParameter("access_token", accessToken)
				.setParameter("key", key)
				.setParameter("dir", dir)
				.build();
		return doHttpGet(apiUrl);
	}
	
	public static List<BaiduNetDiskFileMeta> getNetDiskFileMetas(String accessToken, long[] fsids) throws Exception {
		URI apiUrl = new URIBuilder().setScheme("https").setHost("pan.baidu.com").setPath("/rest/2.0/xpan/multimedia")
				.setParameter("method", "filemetas")
				.setParameter("access_token", accessToken)
				.setParameter("fsids", Arrays.toString(fsids))
				.setParameter("dlink", "1")
				.build();
		JsonObject response = doHttpGet(apiUrl);
		LOGGER.debug(apiUrl.toString());
		try {
			int errno = response.get("errno").getAsInt();
			if(errno != 0) {
				throw new Exception("获取网盘文件Meta请求失败: " + response.toString());
			}
		} catch (Exception e) {
			throw e;
		}
		JsonArray jsonArray = response.getAsJsonArray("list");
		List<BaiduNetDiskFileMeta> fileMetaList = new ArrayList<>();
		for (JsonElement element : jsonArray) {
			String fileJson = element.toString();
			BaiduNetDiskFileMeta file = new Gson().fromJson(fileJson, BaiduNetDiskFileMeta.class);
			fileMetaList.add(file);
		}
		return fileMetaList;
	}
	
	public static JsonObject uploadSlice(String accessToken, String uploadId, String filename, byte[] sliceData,
			String netDiskPath, Integer partSeq) throws Exception {
		URI apiUrl = new URIBuilder().setScheme("https").setHost("d.pcs.baidu.com").setPath("/rest/2.0/pcs/superfile2")
				.setParameter("method", "upload")
				.setParameter("access_token", accessToken)
				.setParameter("path", netDiskPath)
				.setParameter("type", "tmpfile")
				.setParameter("uploadid", uploadId)
				.setParameter("partseq", partSeq.toString())
				.build();
		
		HttpEntity fileEntity = MultipartEntityBuilder.create()
				// .addBinaryBody("file", sliceData); // 必须指定文件名, 否则报错: 
				// {"error_code":31208,"error_msg":"form data format invalid"}
				.addBinaryBody("file", sliceData, ContentType.DEFAULT_BINARY, filename)
				.build();

		JsonObject returnJsonObject = doHttpPost(apiUrl, fileEntity);
		return returnJsonObject;
	}

	public static JsonObject preCreateNetDiskFile(String accessToken, String uploadPath, String fileSize, String isDir,
			String rType, String blockList) throws Exception {
		URI apiUrl = new URIBuilder().setScheme("https").setHost("pan.baidu.com").setPath("/rest/2.0/xpan/file")
				.setParameter("method", "precreate")
				.setParameter("access_token", accessToken)
				.build();
		
		List<NameValuePair> nvps = new ArrayList<>();
		nvps.add(new BasicNameValuePair("path", uploadPath));
		nvps.add(new BasicNameValuePair("size", fileSize));
		nvps.add(new BasicNameValuePair("isdir", isDir));
		nvps.add(new BasicNameValuePair("autoinit", "1"));
		nvps.add(new BasicNameValuePair("rtype", rType));
		nvps.add(new BasicNameValuePair("block_list", blockList));
		UrlEncodedFormEntity httpEntity = new UrlEncodedFormEntity(nvps);
		JsonObject returnJsonObject = doHttpPost(apiUrl, httpEntity);
		return returnJsonObject;
	}
	
	public static JsonObject createNetDiskFile(String accessToken, String netDiskPath, String fileSize, String isDir,
			String uploadId, String blockList) throws Exception {
		URI apiUrl = new URIBuilder().setScheme("https").setHost("pan.baidu.com").setPath("/rest/2.0/xpan/file")
				.setParameter("method", "create")
				.setParameter("access_token", accessToken)
				.build();
		
		List<NameValuePair> nvps = new ArrayList<>();
		nvps.add(new BasicNameValuePair("path", netDiskPath));
		nvps.add(new BasicNameValuePair("size", fileSize));
		nvps.add(new BasicNameValuePair("isdir", isDir));
		nvps.add(new BasicNameValuePair("uploadid", uploadId));
		nvps.add(new BasicNameValuePair("block_list", blockList));
		UrlEncodedFormEntity httpEntity = new UrlEncodedFormEntity(nvps);
		JsonObject returnJsonObject = doHttpPost(apiUrl, httpEntity);
		return returnJsonObject;
	}
	
	public static BaiduNetDiskFileMeta getNetDiskFileMeta(String accessToken, long fsid) throws Exception {
		List<BaiduNetDiskFileMeta> metaList = getNetDiskFileMetas(accessToken, new long[] { fsid });
		if (metaList.size() < 1) {
			throw new Exception("无法获取文件(fsid='" + fsid + "')的meta信息.");
		}
		return metaList.get(0);
	}
	
	public static JsonObject doHttpPost(final URI uri, final HttpEntity httpEntity) throws Exception {
		HttpPost httpPost = new HttpPost(uri);
		httpPost.setEntity(httpEntity);
		httpPost.setHeader("User-Agent", "pan.baidu.com");
		//httpPost.setHeader("Content-type", "application/x-www-form-urlencoded; charset=UTF-8");
		JsonObject returnJsonObject = null;
		try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
			LOGGER.debug("respone: " + response.getCode() + ", " + response.getReasonPhrase());
			HttpEntity entity = response.getEntity();
			String contentType = entity.getContentType();
			long contentLength = entity.getContentLength();
			LOGGER.debug("content-type: " + contentType + ", length: " + contentLength);
			String contents = EntityUtils.toString(entity);
			LOGGER.debug("contents: " + contents);
			returnJsonObject = new Gson().fromJson(contents, JsonObject.class);
			EntityUtils.consume(entity);
		}
		return returnJsonObject;
	}
	
	public static JsonObject doHttpGet(final URI uri) throws Exception {
		HttpGet httpGet = new HttpGet(uri);
		httpGet.setHeader("User-Agent", "pan.baidu.com");
		JsonObject returnJsonObject = null;
		try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
			LOGGER.debug("respone: " + response.getCode() + ", " + response.getReasonPhrase());
			HttpEntity entity = response.getEntity();
			String contentType = entity.getContentType();
			long contentLength = entity.getContentLength();
			LOGGER.debug("content-type: " + contentType + ", length: " + contentLength);
			String contents = EntityUtils.toString(entity);
			LOGGER.debug("contents: " + contents);
			returnJsonObject = new Gson().fromJson(contents, JsonObject.class);
			EntityUtils.consume(entity);
		}
		return returnJsonObject;
	}
	
	public static boolean isEmpty(String s) {
		return s == null || s.trim().length() == 0;
	}
}
