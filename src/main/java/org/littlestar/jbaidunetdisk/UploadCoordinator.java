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

public class UploadCoordinator {
	private static volatile UploadCoordinator singleton;
	private final UploadThreadPoolExecutor uploadThreadPoolExecutor;
	private final EvictingQueue<UploadThread> uploadQueue;

	private UploadCoordinator() {
		ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("upload-threadpool-%d").build();
		uploadThreadPoolExecutor = new UploadThreadPoolExecutor(4, 4, 0, TimeUnit.MINUTES,
				new PriorityBlockingQueue<Runnable>(200), namedThreadFactory, new ThreadPoolExecutor.AbortPolicy());
		uploadQueue = EvictingQueue.create(300);
	}

	public static UploadCoordinator coordinator() {
		if (singleton == null) {
			synchronized (UploadCoordinator.class) {
				if (singleton == null) {
					singleton = new UploadCoordinator();
				}
			}
		}
		return singleton;
	}

	public void submit(UploadThread d) {
		long submitTime = new Date().getTime();
		d.setSubmitTime(submitTime);
		uploadThreadPoolExecutor.execute(d);
		uploadQueue.add(d);
	}

	public List<UploadThread> getUploadQueue() {
		return uploadQueue.parallelStream().collect(Collectors.toList());
	}

	public long getTaskCount() {
		return uploadThreadPoolExecutor.getTaskCount();
	}

	public long getCompletedTaskCount() {
		return uploadThreadPoolExecutor.getCompletedTaskCount();
	}

	public long getPendingTaskCount() {
		long taskCount = uploadThreadPoolExecutor.getTaskCount();
		long completedCount = uploadThreadPoolExecutor.getCompletedTaskCount();
		return taskCount - completedCount;
	}

	class UploadThreadPoolExecutor extends ThreadPoolExecutor {
		public UploadThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
				BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
			super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
		}

		@Override
		protected void beforeExecute(Thread t, Runnable r) {
			if (r instanceof UploadThread) {
				UploadThread uploadThread = (UploadThread) r;
				long startTime = new Date().getTime();
				uploadThread.setStartTime(startTime);
			}
		}

		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			if (r instanceof UploadThread) {
				UploadThread uploadThread = (UploadThread) r;
				long endTime = new Date().getTime();
				uploadThread.setEndTime(endTime);
			}
		}

		@Override
		protected void terminated() {
		}
	}
}
