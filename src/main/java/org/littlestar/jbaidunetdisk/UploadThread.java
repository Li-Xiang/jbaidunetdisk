package org.littlestar.jbaidunetdisk;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UploadThread implements Runnable, Comparable<UploadThread> {
	/**
	 * 上传限制: 
	 * 普通用户单个分片大小固定为4MB（文件大小如果小于4MB，无需切片，直接上传即可），单文件总大小上限为4G。
	 * 普通会员用户单个分片大小上限为16MB，单文件总大小上限为10G。
	 * 超级会员用户单个分片大小上限为32MB，单文件总大小上限为20G。
	 */
	
	private static final Logger LOGGER = LoggerFactory.getLogger(UploadThread.class);
	public enum State {WAITING, UPLOADING, DONE, FAILED};
	private static final int DEFALUT_PRIORITY = 1;
	
	private final String accessToken;
	private final String localPath;
	private final String uploadPath;
	private volatile long uploadedBytes;
	private long submitTime = 0;
	private long startTime = 0;
	private long endTime = 0;
	private volatile State state = State.WAITING;
	private volatile int priority; 
	private String error = "";
	
	public UploadThread(String accessToken, String localPath, String uploadPath, int priority) {
		this.accessToken = accessToken;
		this.localPath = localPath;
		this.uploadPath = uploadPath;
		this.priority = priority;
	}

	public UploadThread(String accessToken, String localPath, String uploadPath) {
		this(accessToken, localPath, uploadPath, DEFALUT_PRIORITY);
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

	public long getUploadedBytes() {
		return uploadedBytes;
	}

	public String getError() {
		return error;
	}

	@Override
	public void run() {
		state = State.UPLOADING;
		final int maxSliceSize = BaiduNetDiskHelper.MAX_SLICE_SIZE;
		try {
			File file = new File(localPath);
			if (!file.isFile()) {
				throw new Exception("要上传的必须是一个文件。");
			}
			String fileName = file.getName();
			final Long fileSize = Files.size(file.toPath());
			String rType = "3";
			// String uploadPath = "/apps/" + appName + uploadSubDir + fileName;
			String blockList = BaiduNetDiskHelper.getFileBlockList(localPath);
			String uploadId = BaiduNetDiskHelper.preCreateNetDiskFile(accessToken, localPath, uploadPath, rType,
					blockList);
			try (final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
				final FileChannel fileChannel = randomAccessFile.getChannel();
				final MappedByteBuffer fileMeppedBuffer = fileChannel.map(MapMode.READ_ONLY, 0,
						randomAccessFile.length());
				// long fileSize = randomAccessFile.length();
				long currPos = 0;
				int partSeq = 0;
				byte[] buffer;
				while (true) {
					long remainBytes = fileSize - currPos;
					if (remainBytes > maxSliceSize) {
						buffer = new byte[maxSliceSize];
						fileMeppedBuffer.get(buffer);
						currPos += maxSliceSize;
						BaiduNetDiskApiHelper.uploadSlice(accessToken, uploadId, fileName, buffer, uploadPath, partSeq);
						uploadedBytes = currPos;
					} else {
						int lastBytes = (int) remainBytes;
						buffer = new byte[lastBytes];
						fileMeppedBuffer.get(buffer);
						BaiduNetDiskApiHelper.uploadSlice(accessToken, uploadId, fileName, buffer, uploadPath, partSeq);
						uploadedBytes = randomAccessFile.length();
						state = State.DONE;
						break;
					}
					partSeq++;
				}
			}
			BaiduNetDiskApiHelper.createNetDiskFile(accessToken, uploadPath, fileSize.toString(), "0", uploadId,
					blockList);
		} catch (Exception e) {
			error = e.getMessage();
			state = State.FAILED;
			LOGGER.error("文件删上传失败", e);
		}
	}
	


	@Override
	public int compareTo(UploadThread o) {
		int oPriority = o.getPriority();
		if (priority < oPriority) {
			return 1;
		} else if (priority > oPriority) {
			return -1;
		} else {
			return 0;
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
		String uploadedSize = BaiduNetDiskHelper.bytesToPretty(uploadedBytes, 2);
		
		String fileName = new File(localPath).getName();
		
		return String.format(OUTPUT_FORMAT, strStartTime, strEndTime, strState, uploadedSize, fileName);
	}
}
