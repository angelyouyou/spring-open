package net.onrc.onos.core.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import java.util.Map;

/**
 * This class acts a singleton to hold the Metrics registry for ONOS.
 */
public final class OnosMetrics {

    /**
     * Hide constructor.  The only way to get the registry is through the
     * singleton getter.
     */
    private OnosMetrics() {}

    /**
     * Components that can hold Metrics.  This is used as the first part of
     * a Metric's name.
     */
    public enum MetricsComponents {
        /**
         * Global scope, not associated with a particular component.
         */
        GLOBAL("Global"),

        /**
         * Topology component.
         */
        TOPOLOGY("Topology");

        private final String name;

        /**
         * Constructor allows specifying an alternate string name.
         *
         * @param name string for the name of the component
         */
        private MetricsComponents(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Features that can hold Metrics.  This is used as the second part of
     * a Metric's name.
     */
    public enum MetricsFeatures {
        /**
         * Global scope, not associated with a particular feature.
         */
        GLOBAL("Global"),

        /**
         * Topology Intents Framework feature. (example)
         */
        TOPOLOGY_INTENTS("IntentsFramework");

        private final String name;

        /**
         * Constructor allows specifying an alternate string name.
         *
         * @param name string for the name of the component
         */
        private MetricsFeatures(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final MetricRegistry METRICS_REGISTRY = new MetricRegistry();

    /**
     * Generates a name for a Metric from its component and feature.
     *
     * @param component component the metric is defined in
     * @param feature feature the metric is defined in
     * @param metricName local name of the metric
     *
     * @return full name of the metric
     */
    public static String generateName(final MetricsComponents component,
                                      final MetricsFeatures feature,
                                      final String metricName) {
        return MetricRegistry.name(component.toString(),
                feature.toString(),
                metricName);
    }

    /**
     * Creates a Counter metric.
     *
     * @param component component the Counter is defined in
     * @param feature feature the Counter is defined in
     * @param metricName local name of the metric
     * @return Counter Meteric
     */
    public static Counter createCounter(final MetricsComponents component,
                                        final MetricsFeatures feature,
                                        final String metricName) {
        final String name = generateName(component, feature, metricName);
        return METRICS_REGISTRY.counter(name);
    }

    /**
     * Creates a Histogram metric.
     *
     * @param component component the Histogram is defined in
     * @param feature feature the Histogram is defined in
     * @param metricName local name of the metric
     * @return Histogram Metric
     */
    public static Histogram createHistogram(final MetricsComponents component,
                                            final MetricsFeatures feature,
                                            final String metricName) {
        final String name = generateName(component, feature, metricName);
        return METRICS_REGISTRY.histogram(name);
    }

    /**
     * Creates a Timer metric.
     *
     * @param component component the Timer is defined in
     * @param feature feature the Timeer is defined in
     * @param metricName local name of the metric
     * @return Timer Metric
     */
    public static Timer createTimer(final MetricsComponents component,
                                    final MetricsFeatures feature,
                                    final String metricName) {
        final String name = generateName(component, feature, metricName);
        return METRICS_REGISTRY.timer(name);
    }

    /**
     * Creates a Meter metric.
     *
     * @param component component the Meter is defined in
     * @param feature feature the Meter is defined in
     * @param metricName local name of the metric
     * @return Meter Metric
     */
    public static Meter createMeter(final MetricsComponents component,
                                    final MetricsFeatures feature,
                                    final String metricName) {
        final String name = generateName(component, feature, metricName);
        return METRICS_REGISTRY.meter(name);
    }

    /**
     * Registers an already created Metric.  This is used for situation where a
     * caller needs to allocate its own Metric, but still register it with the
     * system.
     *
     * @param component component the Metric is defined in
     * @param feature feature the metric is defined in
     * @param metricName local name of the metric
     * @param metric Metric to register
     */
    public static void registerMetric(final MetricsComponents component,
                                      final MetricsFeatures feature,
                                      final String metricName,
                                      final Metric metric) {
        final String name = generateName(component, feature, metricName);
        METRICS_REGISTRY.register(name, metric);
    }

    /**
     * Fetches the existing Timers.
     *
     * @param filter filter to use to select Timers
     * @return a map of the Timers that match the filter, with the key as the
     *         name String to the Timer.
     */
    public static Map<String, Timer> getTimers(final MetricFilter filter) {
        return METRICS_REGISTRY.getTimers(filter);
    }

    /**
     * Fetches the existing Gauges.
     *
     * @param filter filter to use to select Gauges
     * @return a map of the Gauges that match the filter, with the key as the
     *         name String to the Gauge.
     */
    @SuppressWarnings("rawtypes")
    public static Map<String, Gauge> getGauges(final MetricFilter filter) {
        return METRICS_REGISTRY.getGauges(filter);
    }

    /**
     * Fetches the existing Counters.
     *
     * @param filter filter to use to select Counters
     * @return a map of the Counters that match the filter, with the key as the
     *         name String to the Counter.
     */
    public static Map<String, Counter> getCounters(final MetricFilter filter) {
        return METRICS_REGISTRY.getCounters(filter);
    }

    /**
     * Fetches the existing Meters.
     *
     * @param filter filter to use to select Meters
     * @return a map of the Meters that match the filter, with the key as the
     *         name String to the Meter.
     */
    public static Map<String, Meter> getMeters(final MetricFilter filter) {
        return METRICS_REGISTRY.getMeters(filter);
    }

    /**
     * Fetches the existing Histograms.
     *
     * @param filter filter to use to select Histograms
     * @return a map of the Histograms that match the filter, with the key as the
     *         name String to the Histogram.
     */
    public static Map<String, Histogram> getHistograms(final MetricFilter filter) {
        return METRICS_REGISTRY.getHistograms(filter);
    }

    /**
     * Removes all Metrics that match a given filter.
     *
     * @param filter filter to use to select the Metrics to remove.
     */
    public static void removeMatching(final MetricFilter filter) {
        METRICS_REGISTRY.removeMatching(filter);
    }
}

