package org.littlestar.jbaidunetdisk;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.io.CloseMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadThread implements Runnable, Comparable<DownloadThread> {
	private static final Logger LOGGER = LoggerFactory.getLogger(DownloadThread.class);
	public enum State {WAITING, DOWNLOADING, DONE, FAILED};
	private static final int DEFALUT_PRIORITY = 1;
	private final CloseableHttpClient httpClient = HttpClients.createDefault();
	private final String accessToken;
	private final BaiduNetDiskFile netDiskFile;
	private final String downloadDir;
	private volatile long downloadedBytes;
	private long submitTime = 0;
	private long startTime = 0;
	private long endTime = 0;
	private volatile State state = State.WAITING;
	private volatile int priority; 
	private String error = "";
	
	public DownloadThread(String accessToken, BaiduNetDiskFile netDiskFile, String downloadDir, int priority) {
		this.accessToken = accessToken;
		this.netDiskFile = netDiskFile;
		this.downloadDir = downloadDir;
		this.priority = priority;
	}
	
	public DownloadThread(String accessToken, BaiduNetDiskFile netDiskFile, String downloadDir) {
		this(accessToken, netDiskFile, downloadDir, DEFALUT_PRIORITY);
	}

	/**
	 * 等待队列的优先级, priority的值越大, 线程在等待队列的优先级越高.
	 * 
	 * @param priority
	 */
	public void setPriority(int priority) {
		this.priority = priority;
	}
	
	public int getPriority() {
		return priority;
	}

	public State getState() {
		return state;
	}
	
	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}
	
	public long getStartTime() {
		return startTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}
	
	public long getEndTime() {
		return endTime;
	}
	
	public void setSubmitTime(long submitTime) {
		this.submitTime = submitTime;
	}
	
	public long getSubmitTime() {
		return submitTime;
	}
	
	public File getFinalFile() {
		String filePath = downloadDir + File.separator + netDiskFile.getServerFilename();
		return new File(filePath);
	}
	
	public File getTempFile() {
		String filePath = downloadDir + File.separator + netDiskFile.getServerFilename() + ".downloading";
		return new File(filePath);
	}

	public long getDownloadedBytes() {
		return downloadedBytes;
	}

	public String getError() {
		return error;
	}
	
	@Override
	public void run() {
		state = State.DOWNLOADING;
		try {
			BaiduNetDiskHelper.initDir(downloadDir);
			long fsid = netDiskFile.getFsId();
			BaiduNetDiskFileMeta fileMeta = BaiduNetDiskApiHelper.getNetDiskFileMeta(accessToken, fsid);
			String dlink = fileMeta.getDlink();
			// download linke还需要传递access_token.
			dlink += "&access_token=" + accessToken;
			HttpGet httpGet = new HttpGet(dlink);
			httpGet.setHeader("User-Agent", "pan.baidu.com");
			try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
				HttpEntity entity = response.getEntity();
				byte[] buffer = new byte[1024];
				int readedBytes = 0;
				InputStream inputStream = entity.getContent();
				// 为分辨已完成下载文件，和正在下载或者下载异常的文件，下载中的文件存为临时文件。下载完成后再重命名为最终文件。
				File tempFile = getTempFile();
				OutputStream outputStream = Files.newOutputStream(tempFile.toPath());
				while ((readedBytes = inputStream.read(buffer)) != -1) {
					outputStream.write(buffer, 0, readedBytes);
					outputStream.flush();
					downloadedBytes += readedBytes;
				}
				File finalFile = getFinalFile();
				tempFile.renameTo(finalFile);
				outputStream.close();
				inputStream.close();
				EntityUtils.consume(entity);
			}
			state = State.DONE;
		} catch (Exception e) {
			error = e.getMessage();
			state = State.FAILED;
			LOGGER.error("文件下载失败", e);
		} finally {
			httpClient.close(CloseMode.GRACEFUL);
		}
	}
	
	private static final String OUTPUT_FORMAT = "%-19s %-19s %-12s %-9s %s";
	public final static String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"; 
	
	public static String getFormattedOutputHeader() {
		return String.format(OUTPUT_FORMAT, "START TIME", "COMPLETED TIME", "STATE", "DOWLOADED", "FILE NAME")
				+ "\n"
				+ String.format(OUTPUT_FORMAT, 
						"-------------------", 
						"-------------------", 
						"------------", 
						"---------", 
						"-------------------------");
	}
	
	public String getFormattedOutput() {
		String strStartTime  = " - ";
		SimpleDateFormat dtFormat = new SimpleDateFormat(DATE_TIME_PATTERN);
		if(startTime > 0) {
			strStartTime = dtFormat.format(new Date(startTime));
		}
		String strEndTime  = " - ";
		if(endTime > 0) {
			strEndTime = dtFormat.format(new Date(endTime));
		}
		String strState = state.toString();
		String downloadedSize = BaiduNetDiskHelper.bytesToPretty(downloadedBytes, 2);
		String fileName = netDiskFile.getServerFilename();
		
		return String.format(OUTPUT_FORMAT, strStartTime, strEndTime, strState, downloadedSize, fileName);
	}
	
	@Override
	public int compareTo(DownloadThread o) {
		int oPriority = o.getPriority();
		if (priority < oPriority) {
			return 1;
		} else if (priority > oPriority) {
			return -1;
		} else {
			return 0;
		}
	}
}
