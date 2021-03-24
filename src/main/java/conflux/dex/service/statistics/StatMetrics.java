package conflux.dex.service.statistics;

import java.math.BigDecimal;
import java.util.Map;

import org.influxdb.dto.Point.Builder;

import conflux.dex.common.Metrics.ReportableGauge;

interface GaugeByCurrency extends ReportableGauge<Map<String, BigDecimal>> {
	
	@Override
	default Builder buildInfluxDBPoint(Builder builder, Map<String, BigDecimal> value) {
		for (Map.Entry<String, BigDecimal> entry : value.entrySet()) {
			builder.addField(entry.getKey(), entry.getValue().doubleValue());
		}
		
		return builder;
	}

}
