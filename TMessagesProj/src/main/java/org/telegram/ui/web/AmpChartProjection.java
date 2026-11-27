package org.telegram.ui.web;

/** Numeric drawing coordinates, computed once instead of summing all series for each frame. */
final class AmpChartProjection {
    final double[] edges;
    final double[][] values, tops;
    final double total;

    AmpChartProjection(AmpChartData data) {
        total = data.total();
        edges = new double[data.labels.length + 1];
        double width = 0;
        if (data.widths != null) for (double value : data.widths) width += value;
        double before = 0;
        for (int row = 0; row < edges.length; row++) {
            edges[row] = data.widths == null ? (double) row / data.labels.length : before / width;
            if (data.widths != null && row < data.widths.length) before += data.widths[row];
        }
        values = data.proportional ? new double[data.labels.length][data.series.length] : data.values;
        boolean stacked = data.kind == AmpChartData.Kind.STACKED || data.kind == AmpChartData.Kind.AREA && data.stacked;
        tops = stacked ? new double[data.labels.length][data.series.length] : values;
        if (!data.proportional && !stacked) return;
        for (int row = 0; row < data.labels.length; row++) {
            double sum = 0, cumulative = 0;
            if (data.proportional) for (double value : data.values[row]) sum += value;
            for (int series = 0; series < data.series.length; series++) {
                if (data.proportional) values[row][series] = data.values[row][series] * 100 / sum;
                if (stacked) { cumulative += values[row][series]; tops[row][series] = cumulative; }
            }
        }
    }
}
