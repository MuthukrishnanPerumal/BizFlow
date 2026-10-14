import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { formatMoney } from './reporting-format';

export interface ChartSeries {
  name: string;
  color: string;
  points: readonly { label: string; amount: number }[];
}

@Component({
  selector: 'app-metric-chart',
  templateUrl: './metric-chart.html',
  styleUrl: './metric-chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MetricChart {
  readonly title = input.required<string>();
  readonly caption = input('');
  readonly empty = input('No movement in this period.');
  readonly mode = input<'line' | 'bar'>('line');
  readonly series = input<readonly ChartSeries[]>([]);
  readonly bars = input<readonly { label: string; amount: number }[]>([]);

  readonly active = computed(() => {
    if (this.mode() === 'bar') {
      return this.bars().some((bar) => bar.amount !== 0);
    }
    return this.series().some((item) => item.points.some((point) => point.amount !== 0));
  });

  readonly plot = computed(() => buildPlot(this.series()));

  readonly barRows = computed(() => {
    const items = this.bars();
    const max = Math.max(...items.map((item) => Math.abs(item.amount)), 0);
    return items.map((item) => ({
      label: item.label,
      display: formatMoney(item.amount),
      width: max === 0 ? 0 : Math.max(6, (Math.abs(item.amount) / max) * 100),
      negative: item.amount < 0,
    }));
  });
}

function buildPlot(series: readonly ChartSeries[]) {
  const width = 640;
  const height = 168;
  const padL = 8;
  const padR = 8;
  const padT = 14;
  const padB = 28;
  const first = series[0]?.points ?? [];
  const amounts = series.flatMap((item) => item.points.map((point) => point.amount));
  const max = Math.max(0, ...amounts, 0);
  const min = Math.min(0, ...amounts, 0);
  const span = max - min || 1;
  const innerW = width - padL - padR;
  const innerH = height - padT - padB;
  const count = first.length;
  const xOf = (index: number): number => (count <= 1 ? padL + innerW / 2 : padL + (index / (count - 1)) * innerW);
  const yOf = (amount: number): number => padT + ((max - amount) / span) * innerH;
  const lines = series.map((item) => {
    const coords = item.points.map((point, index) => ({ x: xOf(index), y: yOf(point.amount) }));
    const points = coords.map((coord) => `${coord.x.toFixed(1)},${coord.y.toFixed(1)}`).join(' ');
    const last = coords[coords.length - 1];
    const first = coords[0];
    const area =
      first && last
        ? `${points} ${last.x.toFixed(1)},${yOf(0).toFixed(1)} ${first.x.toFixed(1)},${yOf(0).toFixed(1)}`
        : '';
    return {
      name: item.name,
      color: item.color,
      points,
      area,
      dots: coords.length > 0 && coords.length <= 14 ? coords : [],
    };
  });
  const step = count <= 8 ? 1 : Math.ceil(count / 5);
  const labels = first
    .map((point, index) => ({ label: point.label, x: xOf(index), show: index % step === 0 || index === count - 1 }))
    .filter((item) => item.show);
  return {
    width,
    height,
    zero: yOf(0),
    lines,
    labels,
  };
}
