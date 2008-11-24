//
// $Id$

package com.threerings.msoy.web.server;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.Maps;
import com.samskivert.util.StringUtil;

import static com.threerings.msoy.Log.log;

/**
 * Records the times that it takes to call methods. Allows one simultaneous method call per thread
 * (no nested calls). Uses java's nano second timer. Results are logged by method name.
 * TODO: this is not specific to msoy or web servers, move to a lower-level library for reuse
 */
public class MethodProfiler
{
    /**
     * The results of sampling for a single method.
     */
    public static class Result
    {
        /**
         * Creates a new result with the given values.
         */
        Result (int numSamples, double average, double stdDev)
        {
            this.numSamples = numSamples;
            this.averageTime = average;
            this.standardDeviation = stdDev;
        }
        
        /** Number of method calls sampled. */
        public int numSamples;
        
        /** Average time spent in the method. */
        public double averageTime;

        /** Standard deviation from the average. */
        public double standardDeviation;

        // from Object
        public String toString ()
        {
            return StringUtil.fieldsToString(this);
        }
    }

    /**
     * Runs some very basic tests of the method profiler.
     */
    public static void main (String args[])
        throws InterruptedException
    {
        int testNum = 0;
        if (args.length > 0) {
            testNum = Integer.parseInt(args[0]);
        }
        switch (testNum) {
        case 0: // rum some rpc threads
            MethodProfiler test = new MethodProfiler();
            Thread t1 = test.new TestThread("testm1", 100, 50);
            Thread t2 = test.new TestThread("testm2", 100, 50);
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            for (Map.Entry<String, Result> method : test.getResults().entrySet()) {
                log.info(method.getKey(), "result", method.getValue());
            }
            break;
        case 1:
            simpleSampleTest("Single", 100);
            break;
        case 2:
            simpleSampleTest("Triple", 100, 0, 200);
            break;
        case 3:
            simpleSampleTest("Multi", 0, 25, 50, 100, 125, 150, 175, 200, 112.5, 112.5, 112.5);
            break;
        }
    }

    /**
     * Creates a new profiler.
     */
    public MethodProfiler ()
    {
    }

    /**
     * Gets all method results so far.
     */
    public Map<String, Result> getResults ()
    {
        HashMap<String, MethodProfile> profiles = new HashMap<String, MethodProfile>();
        synchronized (_profiles) {
            profiles.putAll(_profiles);
        }

        HashMap<String, Result> results = new HashMap<String, Result>();
        for (Map.Entry<String, MethodProfile> entry : profiles.entrySet()) {
            synchronized (entry.getValue()) {
                results.put(entry.getKey(), entry.getValue().toResult());
            }
        }

        return results;
    }

    /**
     * Notes that the calling thread has entered the given method and records the time stamp.
     */
    public void enter (String method)
    {
        CurrentCall curr = _current.get();
        if (curr.name != null) {
            // TODO: warn, but only once
            return;
        }
        curr.entryTime = System.nanoTime();
        curr.name = method;
    }

    /**
     * Notes that the calling thread has exited the given method and records the time delta since
     * entry. The method parameter is not strictly necessary but allows some error checking. If not
     * null, it must match the most recent value given to {@link #enter()} for the calling thread.
     */
    public void exit (String method)
    {
        long nanos = System.nanoTime();
        CurrentCall curr = _current.get();
        if (curr.name == null || (method != null && !method.equals(curr.name))) {
            // TODO: warn, but only once
            return;
        }

        long elapsed = nanos - curr.entryTime;
        recordTime(curr.name, (double)elapsed / 1000000);
        curr.name = null;
    }

    /**
     * Clears all recorded methods and times.
     */
    public void reset ()
    {
        synchronized (_profiles) {
            _profiles.clear();
        }
    }

    /**
     * Adds a sample to our profile of the given method.
     */
    protected void recordTime (String method, double elapsedMs)
    {
        MethodProfile profile;
        synchronized (_profiles) {
            profile = _profiles.get(method);
            if (profile == null) {
                _profiles.put(method, profile = new MethodProfile());
            }
        }

        synchronized (profile) {
            profile.addSample(elapsedMs);
        }
    }

    /**
     * For testing, just calls the {@link enter()} and {@link exit()} methods at a fixed interval
     * for a given number of times.
     */
    protected class TestThread extends Thread
    {
        public TestThread (String method, int methodCount, long sleep)
        {
            _method = method;
            _methodCount = methodCount;
            _sleep = sleep;
        }

        // from Runnable
        @Override public void run ()
        {
            try {
                for (int i = 0; i < _methodCount; ++i) {
                    MethodProfiler.this.enter(_method);
                    Thread.sleep(_sleep);
                    MethodProfiler.this.exit(_method);
                }
            } catch (InterruptedException ie) {
            }
        }
        
        protected int _methodCount;
        protected String _method;
        protected long _sleep;
    }

    /**
     * Class describing what we know about a thread's current method call.
     */
    protected static class CurrentCall
    {
        /** The name of the entered method. */
        public String name;

        /** The time the method was entered. */
        long entryTime;
    }

    /**
     * Holds the aggregate information about all the calls to a given method. The caller is
     * expected to perform all thread coordination.
     */
    protected static class MethodProfile
    {
        /**
         * Records a new elapsed time spent in the method.
         */
        public void addSample (double time)
        {
            if (_numSamples == 0) {
                _numSamples = 1;
                _average = time;
                _variance = 0;
                return;
            }
            _numSamples = _numSamples + 1;
            double oldAverage = _average;
            _average += (time - _average) / _numSamples;
            double baseVariance = time - oldAverage;
            _variance += (_numSamples - 1) * baseVariance * baseVariance / _numSamples;
        }

        /**
         * Calculates the results of the the profile.
         */
        public Result toResult ()
        {
            return new Result(_numSamples, _average, Math.sqrt(_variance / _numSamples));
        }

        protected int _numSamples;
        protected double _average;
        protected double _variance;
    }

    /**
     * Runs the method profile stat collection with the given samples and logs the result.
     */
    protected static void simpleSampleTest (String name, double... samples)
    {
        MethodProfile prof = new MethodProfile();
        for (double sample : samples) {
            prof.addSample(sample);
        }
        log.info(name, "results", prof.toResult());
    }

    /** Name of the current method. */
    protected ThreadLocal<CurrentCall> _current = new ThreadLocal<CurrentCall>() {
        @Override protected CurrentCall initialValue () {
            return new CurrentCall();
        }
    };

    /** Profiles by method name. */
    protected HashMap<String, MethodProfile> _profiles = Maps.newHashMap();
}
