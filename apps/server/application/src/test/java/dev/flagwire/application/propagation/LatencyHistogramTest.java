package dev.flagwire.application.propagation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class LatencyHistogramTest {

  @Test
  void anEmptyHistogramReportsZero() {
    LatencyHistogram histogram = new LatencyHistogram();

    assertThat(histogram.count()).isZero();
    assertThat(histogram.percentile(0.99)).isZero();
  }

  @Test
  void computesNearestRankPercentiles() {
    LatencyHistogram histogram = new LatencyHistogram();
    LongStream.rangeClosed(1, 100).forEach(histogram::record);

    assertThat(histogram.count()).isEqualTo(100);
    assertThat(histogram.percentile(0.5)).isEqualTo(50);
    assertThat(histogram.percentile(0.95)).isEqualTo(95);
    assertThat(histogram.percentile(0.99)).isEqualTo(99);
    assertThat(histogram.percentile(1.0)).isEqualTo(100);
    assertThat(histogram.percentile(0.0)).isEqualTo(1);
  }

  @Test
  void putsEverythingAboveTheLimitInTheOverflowBucket() {
    LatencyHistogram histogram = new LatencyHistogram();
    histogram.record(LatencyHistogram.MAX_TRACKED_MILLIS);
    histogram.record(LatencyHistogram.MAX_TRACKED_MILLIS + 1);
    histogram.record(10_000_000);

    assertThat(histogram.percentile(0.3)).isEqualTo(LatencyHistogram.MAX_TRACKED_MILLIS);
    assertThat(histogram.percentile(1.0)).isEqualTo(LatencyHistogram.OVERFLOW_MILLIS);
  }

  @Test
  void treatsNegativeLatenciesAsZero() {
    LatencyHistogram histogram = new LatencyHistogram();
    histogram.record(-30);

    assertThat(histogram.percentile(1.0)).isZero();
  }

  @Test
  void mergesAndClears() {
    LatencyHistogram first = new LatencyHistogram();
    LatencyHistogram second = new LatencyHistogram();
    first.record(5);
    second.record(7);
    second.record(9);

    first.merge(second);

    assertThat(first.count()).isEqualTo(3);
    assertThat(first.percentile(1.0)).isEqualTo(9);
    first.clear();
    assertThat(first.count()).isZero();
    assertThat(second.count()).isEqualTo(2);
  }
}
