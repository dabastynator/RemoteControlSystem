package de.neo.remote.cronjob;

import java.text.ParseException;
import java.util.Calendar;

public class CronJob {

	private CronToken mMinute;

	private CronToken mHour;

	private CronToken mDayOfMonth;

	private CronToken mDayOfWeek;

	private CronToken mMonth;

	private long mNextExecution = 0;

	private Runnable mRunnable;

	private int mRepeat;

	public CronJob(Runnable runnable) {
		mRunnable = runnable;
	}

	public Runnable getRunnable() {
		return mRunnable;
	}

	public void parseExpression(String cronExpression) throws ParseException {
		try {
			if (cronExpression == null)
				throw new ParseException("Cronexpression must not be null", 0);
			String[] tokens = cronExpression.split("\\s+");
			if (tokens.length != 5)
				throw new ParseException(
						"Cronexpression must have 5 blocks, seperated by whitespace. Just found "
								+ tokens.length, 0);
			mMinute = new CronToken(tokens[0], Calendar.MINUTE, new int[] {},
					Calendar.HOUR);
			mHour = new CronToken(tokens[1], Calendar.HOUR_OF_DAY,
					new int[] { Calendar.MINUTE }, Calendar.DAY_OF_MONTH);
			mDayOfMonth = new CronToken(tokens[2], Calendar.DAY_OF_MONTH,
					new int[] { Calendar.MINUTE, Calendar.HOUR_OF_DAY },
					Calendar.MONTH);
			mMonth = new CronToken(tokens[3], Calendar.MONTH, new int[] {
					Calendar.MINUTE, Calendar.HOUR_OF_DAY,
					Calendar.DAY_OF_MONTH }, Calendar.YEAR);
			mDayOfWeek = new CronToken(tokens[4], Calendar.DAY_OF_WEEK,
					new int[] { Calendar.MINUTE, Calendar.HOUR_OF_DAY },
					Calendar.MONTH);
		} catch (NumberFormatException e) {
			throw new ParseException(e.getMessage(), 0);
		}
	}

	public long calculateNextExecution() {
		Calendar next = Calendar.getInstance();
		long cm = System.currentTimeMillis();
		// Round to next minute
		next.setTimeInMillis(cm - (cm % (1000 * 60)) + 1000 * 60);
		for (int i = 0; i < 12; i++) {
			mMonth.calculateNextExecution(next);
			if (mDayOfMonth.mRestricted && mDayOfWeek.mRestricted) {
				Calendar d1 = Calendar.getInstance();
				d1.setTimeInMillis(next.getTimeInMillis());
				mDayOfMonth.calculateNextExecution(d1);
				Calendar d2 = Calendar.getInstance();
				d2.setTimeInMillis(next.getTimeInMillis());
				mDayOfWeek.calculateNextExecution(d2);
				next.setTimeInMillis(Math.min(d1.getTimeInMillis(),
						d2.getTimeInMillis()));
			} else {
				mDayOfMonth.calculateNextExecution(next);
				mDayOfWeek.calculateNextExecution(next);
			}
			mHour.calculateNextExecution(next);
			mMinute.calculateNextExecution(next);
			if (mNextExecution == next.getTimeInMillis())
				break;
			mNextExecution = next.getTimeInMillis();
		}
		if (mNextExecution != next.getTimeInMillis())
			mNextExecution = Long.MAX_VALUE;
		return mNextExecution;
	}

	public long getNextExecution() {
		return mNextExecution;
	}

	private class CronToken {

		private int[] mFigures;

		private int mCalendarField;

		private boolean mRestricted;

		private int[] mResetFields;

		private int mNextField;

		public CronToken(String token, int calendarField, int[] resetFields,
				int nextField) throws ParseException {
			mCalendarField = calendarField;
			mRestricted = true;
			mResetFields = resetFields;
			mNextField = nextField;
			if (token.equals("*"))
				mRestricted = false;
			else if (token.contains(",")) {
				String[] figures = token.split(",");
				setFigures(figures);
			} else if (token.contains("-")) {
				String[] startStop = token.split("-");
				if (startStop.length != 2)
					throw new ParseException("Unexpected token '" + token
							+ "' must look like 3-7", 0);
				int start = Integer.parseInt(startStop[0]);
				int stop = Integer.parseInt(startStop[1]);
				mFigures = new int[stop - start + 1];
				int i = 0;
				while (start <= stop) {
					mFigures[i++] = start++;
				}
			} else if (token.contains("/")) {
				String[] repeat = token.split("/");
				if (repeat.length != 2 || !repeat[0].equals("*"))
					throw new ParseException("Unknown token '" + token
							+ "', expected */5", 0);
				int steps = Integer.parseInt(repeat[1]);
				mFigures = new int[60 / steps];
				for (int i = 0; i < 60 / steps; i++)
					mFigures[i] = i * steps;
			} else {
				mFigures = new int[] { Integer.parseInt(token) };
			}
		}

		public void calculateNextExecution(Calendar calendar) {
			int currentValue = calendar.get(mCalendarField);
			if (mRestricted) {
				int minimal = Integer.MAX_VALUE;
				int minimalOffset = Integer.MAX_VALUE;
				for (int i = 0; i < mFigures.length; i++) {
					if (currentValue <= mFigures[i])
						minimal = Math.min(minimal, mFigures[i]);
					else
						minimalOffset = Math.min(minimalOffset, mFigures[i]);
				}
				if (minimal < Integer.MAX_VALUE)
					calendar.set(mCalendarField, minimal);
				else {
					calendar.add(mNextField, 1);
					calendar.set(mCalendarField, minimalOffset);
					for (int reset : mResetFields)
						calendar.set(reset, 0);
				}
			}
		}

		public void setFigures(String[] figures) throws ParseException {
			mFigures = new int[figures.length];
			for (int i = 0; i < figures.length; i++) {
				mFigures[i] = Integer.parseInt(figures[i]);
			}
		}

	}

	public void setRepeat(int repeat) {
		mRepeat = repeat;
	}

	public int getRepeat() {
		return mRepeat;
	}

}