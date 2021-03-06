package de.neo.smarthome.cronjob;

import java.text.ParseException;
import java.util.Comparator;
import java.util.PriorityQueue;

import de.neo.remote.rmi.RMILogger.LogPriority;
import de.neo.smarthome.RemoteLogger;

public class CronScheduler extends Thread {

	public static final int REPEAT_INFINIT = -1;

	private static CronScheduler mInstance;

	public static CronScheduler getInstance() {
		if (mInstance == null)
			mInstance = new CronScheduler();
		return mInstance;
	}

	private boolean mRunning;

	private PriorityQueue<CronJob> mHeap;

	private JobExecuter mExecutor;

	private CronScheduler() {
		mHeap = new PriorityQueue<CronJob>(16, new JobComparator());
		mRunning = true;
		mExecutor = new JobExecuter() {
			@Override
			public void executeJob(Runnable runnable) {
				runnable.run();
			}
		};
		start();
	}

	public void setJobExecutor(JobExecuter executer) {
		mExecutor = executer;
	}

	@Override
	public void run() {
		while (mRunning) {
			try {
				handleHeap();
				synchronized (this) {
					wait();
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private synchronized void handleHeap() {
		while (mHeap.size() > 0) {
			CronJob job = mHeap.poll();
			try {
				long nextExecution = job.getNextExecution();
				long now = System.currentTimeMillis();
				if (now < nextExecution)
					wait(nextExecution - now);
				if (System.currentTimeMillis() >= nextExecution) {
					mExecutor.executeJob(job.getRunnable());
					if (job.getRepeat() != REPEAT_INFINIT)
						job.setRepeat(job.getRepeat() - 1);
					job.calculateNextExecution();
				}
			} catch (InterruptedException e) {
				// ignore
			} finally {
				if (job.getRepeat() > 0 || job.getRepeat() == REPEAT_INFINIT)
					mHeap.add(job);
			}
		}
	}

	public synchronized void scheduleJob(Runnable runnable, String cronExpression, int repeat) throws ParseException {
		CronJob job = new CronJob(runnable);
		job.parseExpression(cronExpression);
		job.setRepeat(repeat);
		job.calculateNextExecution();
		if (job.getNextExecution() < Long.MAX_VALUE) {
			mHeap.add(job);
			RemoteLogger.performLog(LogPriority.INFORMATION, "Schedule cron job", job.toString());
		} else
			RemoteLogger.performLog(LogPriority.ERROR, "Job can't be scheduled", job.toString());
		notify();
	}

	public void scheduleJob(Runnable runnable, String cronExpression) throws ParseException {
		scheduleJob(runnable, cronExpression, REPEAT_INFINIT);
	}
	
	public synchronized void remove(Runnable runnable) {
		CronJob found = null;
		for (CronJob job: mHeap) {
			if (job.mRunnable == runnable) {
				found = job;
			}
		}
		if (found != null) {
			mHeap.remove(found);	
		}
	}

	interface JobExecuter {
		void executeJob(Runnable runnable);
	}

	class JobComparator implements Comparator<CronJob> {

		@Override
		public int compare(CronJob o1, CronJob o2) {
			return Long.compare(o1.mNextExecution, o2.mNextExecution);
		}

	}

}
