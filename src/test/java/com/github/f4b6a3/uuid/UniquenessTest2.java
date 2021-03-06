package com.github.f4b6a3.uuid;

import java.util.HashSet;
import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;
import com.github.f4b6a3.uuid.creator.rfc4122.TimeBasedUuidCreator;
import com.github.f4b6a3.uuid.exception.UuidCreatorException;
import com.github.f4b6a3.uuid.strategy.timestamp.StoppedTimestampStrategy;
import com.github.f4b6a3.uuid.util.UuidTime;
import com.github.f4b6a3.uuid.util.UuidUtil;

/**
 * This test runs many threads that request time-based UUIDs in a loop.
 * 
 * This is a longer and VERBOSE alternative to {@link UniquenessTest1}.
 */
public class UniquenessTest2 {

	private int threadCount; // Number of threads to run
	private int requestCount; // Number of requests for thread

	// private long[][] cacheLong; // Store values generated per thread
	private HashSet<Long> hashSet;

	private boolean verbose; // Show progress
	private boolean exception; // Throw exception

	// Abstract time-based UUID creator
	private TimeBasedUuidCreator creator;

	private static final long FIXED_TIMESTAMP = UuidTime.toTimestamp(System.currentTimeMillis());

	/**
	 * Initialize the test.
	 * 
	 * This test is not included in the {@link TestSuite} because it takes a long
	 * time to finish.
	 * 
	 * @param threadCount
	 * @param requestCount
	 * @param creator
	 * @param verbose
	 */
	public UniquenessTest2(TimeBasedUuidCreator creator, int threadCount, int requestCount, boolean verbose,
			boolean exception) {
		this.threadCount = threadCount;
		this.requestCount = requestCount;
		this.creator = creator;
		this.verbose = verbose;
		this.exception = exception;
		this.initCache();
	}

	private void initCache() {
		this.hashSet = new HashSet<>();
	}

	/**
	 * Initialize and start the threads.
	 */
	public void start() {

		TestThread[] threads = new TestThread[this.threadCount];

		// Instantiate and start many threads
		for (int id = 0; id < this.threadCount; id++) {
			threads[id] = new TestThread(id, this.creator, verbose, exception);
			threads[id].start();
		}

		// Wait all the threads to finish
		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public class TestThread extends Thread {

		private int id;
		private TimeBasedUuidCreator creator;
		private boolean verbose;
		private boolean exception;

		public TestThread(int id, TimeBasedUuidCreator creator, boolean verbose, boolean exception) {
			this.id = id;
			this.creator = creator;
			this.verbose = verbose;
			this.exception = exception;

			if (this.creator == null) {
				// DEDICATED generator that creates time-based UUIDs (v1),
				// that uses a hash instead of a random node identifier,
				// and that uses a fixed millisecond to simulate a loop faster than the clock
				this.creator = newCreator();
			}
		}

		/**
		 * Run the test.
		 */
		@Override
		public void run() {

			long msb = 0;
			long lsb = 0;
			long value = 0;
			int progress = 0;
			int max = requestCount;

			for (int i = 0; i < max; i++) {

				// Request a UUID
				UUID uuid = null;
				try {
					uuid = creator.create();
				} catch (UuidCreatorException e) {
					// Ignore the overrun exception and try again
					uuid = creator.create();
				}

				msb = UuidUtil.extractTimestamp(uuid) << 16;
				lsb = UuidUtil.extractClockSequence(uuid);

				value = (msb | lsb);

				if (verbose) {
					// Calculate and show progress
					progress = (int) ((i * 1.0 / max) * 100);
					if (progress % 10 == 0) {
						System.out.println(String.format("[Thread %06d] %s %s %s%%", id, uuid, i, (int) progress));
					}
				}
				synchronized (hashSet) {
					// Insert the value in cache, if it does not exist in it.
					if (!hashSet.add((Long) value)) {
						if (this.exception) {
							new RuntimeException(
									String.format("[Thread %06d] %s %s %s%% [DUPLICATE]", id, uuid, i, (int) progress));
						} else {
							System.err.println(
									String.format("[Thread %06d] %s %s %s%% [DUPLICATE]", id, uuid, i, (int) progress));
						}
					}
				}
			}

			if (verbose) {
				// Finished
				System.out.println(String.format("[Thread %06d] Done.", id));
			}
		}
	}

	public static void execute(TimeBasedUuidCreator creator, int threadCount, int requestCount, boolean verbose,
			boolean exception) {
		UniquenessTest2 test = new UniquenessTest2(creator, threadCount, requestCount, verbose, exception);
		test.start();
	}

	private static TimeBasedUuidCreator newCreator() {
		// a new generator that creates time-based UUIDs (v1),
		// that uses a hash instead of a random node identifier,
		// and that uses a fixed millisecond to simulate a loop faster than the clock
		return UuidCreator.getTimeBasedCreator().withHashNodeIdentifier()
				.withTimestampStrategy(new StoppedTimestampStrategy(FIXED_TIMESTAMP));
	}

	public static void main(String[] args) {

		System.out.println("-----------------------------------------------------");
		System.out.println("SHARED generator for all threads                     ");
		System.out.println("-----------------------------------------------------");

		// SHARED generator for all threads
		TimeBasedUuidCreator creator = newCreator();

		boolean verbose = true;
		boolean exception = false;
		int threadCount = 16; // Number of threads to run
		int requestCount = 1_000_000; // Number of requests for thread

		execute(creator, threadCount, requestCount, verbose, exception);

		System.out.println();
		System.out.println("-----------------------------------------------------");
		System.out.println("DEDICATED generators for each thread                 ");
		System.out.println("-----------------------------------------------------");

		// Dedicated generators for each thread
		creator = null;

		verbose = true;
		exception = false;
		threadCount = 16; // Number of threads to run
		requestCount = 1_000_000; // Number of requests for thread

		execute(creator, threadCount, requestCount, verbose, exception);

	}
}
