package net.onrc.onos.core.datastore;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.onrc.onos.core.datastore.DataStoreClient;
import net.onrc.onos.core.datastore.IKVClient;
import net.onrc.onos.core.datastore.IKVTableID;
import net.onrc.onos.core.datastore.ObjectDoesntExistException;
import net.onrc.onos.core.datastore.ObjectExistsException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AtomicCounterTest {

    private static final String TEST_COUNTER = "TestCounter";
    private static final byte[] LONG_ZERO = {0, 0, 0, 0, 0, 0, 0, 0}; // 0L
    private static final IKVTableID counterID = DataStoreClient.getClient().getTable(TEST_COUNTER).getTableId();

    @After
    @Before
    public void resetCounter() {
        IKVClient client = DataStoreClient.getClient();
        client.setCounter(counterID, LONG_ZERO, 0L);
        client.destroyCounter(counterID, LONG_ZERO);
    }

    @Test
    public void testSetCounter() throws ObjectExistsException, ObjectDoesntExistException {
        IKVClient client = DataStoreClient.getClient();

        final long five = 5;
        client.createCounter(counterID, LONG_ZERO, five);

        final long three = 3;
        client.setCounter(counterID, LONG_ZERO, three);

        final long four = client.incrementCounter(counterID, LONG_ZERO, 1);
        assertEquals(4, four);
    }

    @Test
    public void testIncrementCounter() throws ObjectExistsException, ObjectDoesntExistException {

        IKVClient client = DataStoreClient.getClient();

        final long five = 5;
        client.createCounter(counterID, LONG_ZERO, five);

        final long six = client.incrementCounter(counterID, LONG_ZERO, 1);
        assertEquals(6, six);


        final long nine = client.incrementCounter(counterID, LONG_ZERO, 3);
        assertEquals(9, nine);
    }


    private static final int NUM_INCREMENTS = Math.max(1, Integer
            .valueOf(System.getProperty("AtomicCounterTest.NUM_INCREMENTS",
                    "500")));
    private static final int NUM_THREADS = Math.max(1, Integer.valueOf(System
            .getProperty("AtomicCounterTest.NUM_THREADS", "3")));

    class Incrementor implements Callable<Long> {
        private final ConcurrentMap<Long,Long> uniquenessTestSet;
        private final ConcurrentLinkedQueue<Long> incrementTimes;

        public Incrementor(ConcurrentMap<Long, Long> uniquenessTestSet, ConcurrentLinkedQueue<Long> incrementTimes) {
            super();
            this.uniquenessTestSet = uniquenessTestSet;
            this.incrementTimes = incrementTimes;
        }

        @Override
        public Long call() throws ObjectDoesntExistException {
            IKVClient client = DataStoreClient.getClient();
            for (int i = 0 ; i < NUM_INCREMENTS ; ++i) {
                final long start = System.nanoTime();
                final long incremented = client.incrementCounter(counterID, LONG_ZERO, 1);
                incrementTimes.add( System.nanoTime() - start );
                final Long expectNull = uniquenessTestSet.putIfAbsent(incremented, incremented);
                assertNull(expectNull);
            }
            return null;
        }
    }

    @Test
    public void testParallelIncrementCounter() throws ObjectExistsException,
                                    InterruptedException, ExecutionException {

        IKVClient client = DataStoreClient.getClient();

        client.createCounter(counterID, LONG_ZERO, 0L);

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        final int initThreads = Math.max(1, Integer.valueOf(System
                .getProperty("AtomicCounterTest.initThreads",
                        String.valueOf(NUM_THREADS))));
        for (int num_threads = initThreads; num_threads <= NUM_THREADS; ++num_threads) {
            client.setCounter(counterID, LONG_ZERO, 0L);
            parallelIncrementCounter(executor, num_threads);
        }

        executor.shutdown();
    }

    private void parallelIncrementCounter(final ExecutorService executor,
            final int num_threads) throws InterruptedException, ExecutionException {

        ConcurrentNavigableMap<Long,Long> uniquenessTestSet = new ConcurrentSkipListMap<>();
        ConcurrentLinkedQueue<Long> incrementTimes = new ConcurrentLinkedQueue<Long>();

        List<Callable<Long>> tasks = new ArrayList<>(num_threads);
        for (int i = 0 ; i < num_threads ; ++i) {
            tasks.add(new Incrementor(uniquenessTestSet, incrementTimes));
        }
        List<Future<Long>> futures = executor.invokeAll(tasks);

        // wait for all tasks to end
        for (Future<Long> future : futures) {
            future.get();
        }

        assertEquals(num_threads * NUM_INCREMENTS , uniquenessTestSet.size());
        long prevValue = 0;
        for (Long value : uniquenessTestSet.keySet() ) {
            assertEquals( (prevValue + 1), value.longValue() );
            prevValue = value;
        }

        long max = 0L;
        long min = Long.MAX_VALUE;
        long sum = 0L;
        for (Long time : incrementTimes) {
            sum += time;
            max = Math.max(max, time);
            min = Math.min(min, time);
        }
        System.err.printf("incrementCounter(th:%d , incs:%d ) N:%d\tavg:%f (ns) min:%d (ns) max:%d (ns)\n",
                num_threads, NUM_INCREMENTS, incrementTimes.size(),
                sum/(double)incrementTimes.size(), min, max );
    }

}