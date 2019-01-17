import {getColorFor} from './colorsUtils';
import {formatters} from 'services';

const {convertToMilliseconds} = formatters;

export function formatTooltip(
  {index, datasetIndex},
  {datasets},
  targetValue,
  configuration,
  formatter,
  processInstanceCountData,
  property,
  type
) {
  const {hideAbsoluteValue, hideRelativeValue} = configuration;
  let formatted = '';
  if (!hideAbsoluteValue) formatted = formatter(datasets[datasetIndex].data[index]);

  if (property === 'frequency' && processInstanceCountData && !hideRelativeValue) {
    let processInstanceCount = processInstanceCountData[datasetIndex];
    // in the case of the line with target value we have 2 datasets for each report
    // we have to divide by 2 to get the right index
    if (type === 'line' && targetValue.active) {
      processInstanceCount = processInstanceCountData[~~(datasetIndex / 2)];
    }
    return `${formatted} (${getRelativeValue(
      datasets[datasetIndex].data[index],
      processInstanceCount
    )})`;
  } else {
    return formatted;
  }
}

function getRelativeValue(data, total) {
  if (data === null) return '';
  return Math.round(data / total * 1000) / 10 + '%';
}

export function getTooltipLabelColor(tooltipItem, chart, type) {
  const datasetOptions = chart.data.datasets[tooltipItem.datasetIndex];
  if (type === 'pie') {
    const color = datasetOptions.backgroundColor[tooltipItem.index];
    return {
      borderColor: color,
      backgroundColor: color
    };
  }

  return {
    borderColor: datasetOptions.legendColor,
    backgroundColor: datasetOptions.legendColor
  };
}

export function calculateLinePosition(chart) {
  const yAxis = chart.scales[chart.options.scales.yAxes[0].id];

  return (1 - chart.options.lineAt / yAxis.max) * yAxis.height + yAxis.top;
}

export function drawHorizentalLine(chart) {
  if (chart.options.lineAt >= 0) {
    const ctx = chart.chart.ctx;
    const xAxe = chart.scales[chart.options.scales.xAxes[0].id];
    const lineAt = calculateLinePosition(chart);

    ctx.save();
    ctx.strokeStyle = getColorFor('targetBar', true);
    ctx.setLineDash([10, 10]);
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(xAxe.left, lineAt);
    ctx.lineTo(xAxe.right, lineAt);
    ctx.stroke();
    ctx.restore();
  }
}

// Override the default generate legend's labels function
// This is done to modify the colors retrieval method of the legend squares and filter unneeded labels
export function generateLegendLabels(chart) {
  const data = chart.data;
  return data.datasets.length
    ? data.datasets
        .map(function(dataset) {
          return {
            text: dataset.label,
            fillStyle: !dataset.backgroundColor.length
              ? dataset.backgroundColor
              : dataset.legendColor,
            strokeStyle: dataset.legendColor
          };
        }, this)
        .filter(dataset => {
          return dataset.text;
        })
    : [];
}

export function createDurationFormattingOptions(data, targetLine, isCombined) {
  // since the duration is given in milliseconds, chart.js cannot create nice y axis
  // ticks. So we define our own set of possible stepSizes and find one that the maximum
  // value of the dataset fits into or the maximum target line value if it is defined.
  let dataMinStep;
  if (isCombined) {
    dataMinStep =
      Math.max(...data.map(reportResult => Math.max(...Object.values(reportResult)))) / 10;
  } else {
    dataMinStep = Math.max(...Object.values(data)) / 10;
  }
  const targetLineMinStep = targetLine ? targetLine / 10 : 0;
  const minimumStepSize = Math.max(targetLineMinStep, dataMinStep);

  const steps = [
    {value: 1, unit: 'ms', base: 1},
    {value: 10, unit: 'ms', base: 1},
    {value: 100, unit: 'ms', base: 1},
    {value: 1000, unit: 's', base: 1000},
    {value: 1000 * 10, unit: 's', base: 1000},
    {value: 1000 * 60, unit: 'min', base: 1000 * 60},
    {value: 1000 * 60 * 10, unit: 'min', base: 1000 * 60},
    {value: 1000 * 60 * 60, unit: 'h', base: 1000 * 60 * 60},
    {value: 1000 * 60 * 60 * 6, unit: 'h', base: 1000 * 60 * 60},
    {value: 1000 * 60 * 60 * 24, unit: 'd', base: 1000 * 60 * 60 * 24},
    {value: 1000 * 60 * 60 * 24 * 7, unit: 'wk', base: 1000 * 60 * 60 * 24 * 7},
    {value: 1000 * 60 * 60 * 24 * 30, unit: 'm', base: 1000 * 60 * 60 * 24 * 30},
    {value: 1000 * 60 * 60 * 24 * 30 * 6, unit: 'm', base: 1000 * 60 * 60 * 24 * 30},
    {value: 1000 * 60 * 60 * 24 * 30 * 12, unit: 'y', base: 1000 * 60 * 60 * 24 * 30 * 12},
    {value: 10 * 1000 * 60 * 60 * 24 * 30 * 12, unit: 'y', base: 1000 * 60 * 60 * 24 * 30 * 12}, //10s of years
    {value: 100 * 1000 * 60 * 60 * 24 * 30 * 12, unit: 'y', base: 1000 * 60 * 60 * 24 * 30 * 12} //100s of years
  ];

  const niceStepSize = steps.find(({value}) => value > minimumStepSize);
  if (!niceStepSize) return;

  return {
    callback: v => v / niceStepSize.base + niceStepSize.unit,
    stepSize: niceStepSize.value
  };
}

export function getFormattedTargetValue({values}) {
  if (!values.unit) return values.value;
  return convertToMilliseconds(values.value, values.unit);
}
