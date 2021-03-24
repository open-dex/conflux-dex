package conflux.dex.controller.metric;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.databind.ObjectMapper;

import conflux.dex.common.Metrics;
import org.apache.catalina.filters.CorsFilter;

public class MetricsServlet extends HttpServlet {
	
	private static final long serialVersionUID = 7495695669206609303L;
	
	private static final String template = "<!DOCTYPE HTML>"
			+ "<html>"
			+ "	<body>"
			+ "		<ul>"
			+ "			%s"
			+ "		</ul>"
			+ "	</body>"
			+ "</html>";
	
	private static final ObjectMapper MAPPER = new ObjectMapper()
			.registerModules(new MetricsModule(TimeUnit.SECONDS, TimeUnit.MILLISECONDS, false));

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
		
		if (req.getPathInfo() == null || req.getPathInfo().equals("/")) {
			this.renderMetricList(req, resp);
		} else {
			this.renderMetric(req.getPathInfo().substring(1), resp);
		}
	}
	
	private void renderMetricList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String path = req.getContextPath() + req.getServletPath();
		resp.setContentType("text/html");
		
		StringBuilder builder = new StringBuilder();
		
		for (String metric : Metrics.DefaultRegistry.getNames()) {
			builder.append(String.format("<li><a href=\"%s/%s\">%s</a></li>", path, metric, metric));
		}
		
		String html = String.format(template, builder.toString());
		
		try (PrintWriter writer = resp.getWriter()) {
			writer.write(html);
		}
	}
	
	private void renderMetric(String name, HttpServletResponse resp) throws IOException {
		resp.setContentType("application/json");
		//CORS
		HttpServletResponse response = resp;
		response.addHeader(CorsFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN, "*");
		//
		Object metric = Metrics.DefaultRegistry.getMetrics().get(name);
		if (metric == null) {
			metric = "metric not found: " + name;
		}
		
		String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(metric);
		
		try (PrintWriter writer = resp.getWriter()) {
			writer.write(json);
		}
	}
	
}
