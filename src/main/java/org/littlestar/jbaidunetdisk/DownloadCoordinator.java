package org.littlestar.jbaidunetdisk;

import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.collect.EvictingQueue;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class DownloadCoordinator {
	private static volatile DownloadCoordinator singleton;
	private final DownloadThreadPoolExecutor downloadThreadPoolExecutor;
	private final EvictingQueue<DownloadThread> downloadQueue;
	
	private DownloadCoordinator() {
		ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
				.setNameFormat("download-threadpool-%d")
				.build();
		downloadThreadPoolExecutor = new DownloadThreadPoolExecutor(
				4, 4, 0, TimeUnit.MINUTES, 
				new PriorityBlockingQueue<Runnable>(200), 
				namedThreadFactory, 
				new ThreadPoolExecutor.AbortPolicy());
		// 下载历史列表， 定长队列，超过长度会丢弃最早加入的。
		downloadQueue = EvictingQueue.create(300);
	}
	
	public static DownloadCoordinator coordinator() {
		if (singleton == null) {
			synchronized (DownloadCoordinator.class) {
				if (singleton == null) {
					singleton = new DownloadCoordinator();
				}
			}
		}
		return singleton;
	}
	
	public void submit(DownloadThread d) {
		long submitTime = new Date().getTime();
		d.setSubmitTime(submitTime);
		downloadThreadPoolExecutor.execute(d);
		downloadQueue.add(d);
	}
	
	public List<DownloadThread> getDownloadQueue() {
		return downloadQueue.parallelStream().collect(Collectors.toList());
	}
	
	public long getTaskCount() {
		return downloadThreadPoolExecutor.getTaskCount();
	}
	
	public long getCompletedTaskCount() {
		return downloadThreadPoolExecutor.getCompletedTaskCount();
	}
	
	public long getPendingTaskCount() {
		long taskCount = downloadThreadPoolExecutor.getTaskCount();
		long completedCount = downloadThreadPoolExecutor.getCompletedTaskCount();
		return taskCount - completedCount;
	}
	
	class DownloadThreadPoolExecutor extends ThreadPoolExecutor {
		public DownloadThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
				BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
			super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
		}
		
		@Override
		protected void beforeExecute(Thread t, Runnable r) {
			if (r instanceof DownloadThread) {
				DownloadThread downloadThread = (DownloadThread) r;
				long startTime = new Date().getTime();
				downloadThread.setStartTime(startTime);
			}
		}
		
		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			if (r instanceof DownloadThread) {
				DownloadThread downloadThread = (DownloadThread) r;
				long endTime = new Date().getTime();
				downloadThread.setEndTime(endTime);
			}
		}
		
		@Override
		protected void terminated() {
		}
	}

}
