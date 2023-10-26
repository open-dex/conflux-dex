package conflux.dex.blockchain.crypto.legacy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.ImmutableMap;
import conflux.dex.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;

import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;

import conflux.dex.common.Metrics;
import conflux.dex.common.Utils;
import conflux.dex.config.BlockchainConfig;
import conflux.web3j.Request;
import conflux.web3j.RpcException;
import conflux.web3j.response.StringResponse;

public class NodejsWrapper {

	private static final Logger logger = LoggerFactory.getLogger(NodejsWrapper.class);
	
	private static final Timer perf = Metrics.timer(NodejsWrapper.class, "perf");
	
	private static final String DUMP_JS_FILE = Utils.getResource("blockchain/tool/bf.dump.js");
	private static Map<String, Process> tempFile2processMap = new ConcurrentHashMap<>();

	// Temporary solution before Java ABI SDK available
	private int serviceIndex;
	private int serviceSwitchRound;
	private Web3jService[] servicePool = {
			new HttpService("http://localhost:7001"),
			new HttpService("http://localhost:7002"),
			new HttpService("http://localhost:7003"),
			new HttpService("http://localhost:7004"),
			new HttpService("http://localhost:7005"),
	};
	
	public NodejsWrapper() {
		Metrics.dump(this);
	}
	
	@Autowired
	public void init(BlockchainConfig config) {
		if (config.enabled) {
			// ensure JS service is started
			this.encode("ping");
		}
	}

	public String encode(String method, Object... args) throws RpcException {
		try (Context context = perf.time()) {
			int start = this.serviceIndex;
			
			while (true) {
				Web3jService service = this.servicePool[this.serviceIndex];
				Request<String, StringResponse> request = new Request<>(service, method, StringResponse.class, args);
				
				try {
					return request.sendAndGet();
				} catch (RpcException e) {
					logger.info("failed to encode data via JS service", e);
					logger.warn("failed to encode data via JS service, index = {}, switchRound = {}, error = {}",
							this.serviceIndex, this.serviceSwitchRound, e.getMessage());
					
					// move to next service in pool
					if (++this.serviceIndex >= this.servicePool.length) {
						this.serviceIndex = 0;
						++this.serviceSwitchRound;
					}
					
					// all services in pool are failed
					if (this.serviceIndex == start) {
						throw e;
					}
				}
				
			}
		}
	}
	
	public int getServiceSwitchRound() {
		return serviceSwitchRound;
	}
	
	public int getServiceIndex() {
		return serviceIndex;
	}

	public static String deployToken(String name) {
		String jsPath;
		if (BlockchainConfig.instance.isEvm()) {
			jsPath = "blockchain/deployment/bf.deployEVM.js";
		} else {
			jsPath = "blockchain/deployment/bf.deploy.js";
		}
		return executeOSCommand(Arrays.asList("node",
				Utils.getResource(jsPath),
				"--crcl", "--c_name", name));
	}

	public static Map<Object, Object> processStatus(String logFilePath) {
		Process process = tempFile2processMap.get(logFilePath);
		if (process == null) {
			return ImmutableMap.of("error", "not found");
		}
		List<String> lines;
		try {
			lines = Files.readAllLines(Paths.get(logFilePath));
		} catch (IOException e) {
			lines = Arrays.asList("read fail", e.toString());
		}
		return ImmutableMap.builder().put("file", logFilePath)
				.put("alive", process.isAlive())
				.put("exitValue", process.isAlive() ? "" : process.exitValue())
				.put("logContent", lines)
				.build();
	}
	public static String executeOSCommand(List<String> args) {
		File outputFile;
		try {
			outputFile = File.createTempFile("cmd", ".log");
		} catch (IOException e) {
			logger.error("create temp file fail.", e);
			throw BusinessException.internalError("create temp file fail.", e);
		}
		try {
			Process process = new ProcessBuilder(args).redirectErrorStream(true)
					.redirectOutput(outputFile).start();
			String logFilePath = outputFile.getAbsolutePath();
			tempFile2processMap.put(logFilePath, process);
			logger.info("create node process, log at {}", logFilePath);
			return logFilePath;
		} catch (IOException e) {
			logger.error("start process fail", e);
			throw BusinessException.internalError("start process fail", e);
		}
	}

	public static void dump(String cfxUrl, String failedTxHash) throws Exception {
		// node src/main/resources/blockchain/tool/bf.dump.js <cfx url> <tx hash> <download file> [<dex url>]
		String filename = String.format("dump-failed-tx-%s.json", failedTxHash);
		List<String> cmds = Arrays.asList("node", DUMP_JS_FILE, cfxUrl, failedTxHash, filename);
		
		int exitCode = new ProcessBuilder(cmds).start().waitFor();
		if (exitCode != 0) {
			throw new Exception(String.format("exit code is %s", exitCode));
		}
	}

}
