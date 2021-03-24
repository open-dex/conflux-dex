package conflux.dex.controller;

import java.util.TimeZone;

import conflux.dex.config.AuthRequire;
import conflux.dex.tool.SpringTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.model.FeeData;
import conflux.dex.service.FeeService;

/**
 * Common information
 */
@RestController
@RequestMapping("/common")
public class CommonController {
	
	private TimeZone systemTimeZone;
	
	@Autowired
	public CommonController(TimeZone systemTimeZone) {
		this.systemTimeZone = systemTimeZone;
	}
	
	/**
	 * Get system timestamp
	 * Get the current system timestamp in milliseconds.
	 */
	@GetMapping("/timestamp")
	public long getSystemTimestamp() {
		return System.currentTimeMillis();
	}
	
	/**
	 * Get system timezone ID
	 */
	@GetMapping("/timezone")
	public String getSystemTimeZoneId() {
		return this.systemTimeZone.getID();
	}
	
	/**
	 * Get Boomflow address
	 */
	@GetMapping("/boomflow")
	public String getBoomflowAddress() {
		return Domain.boomflow().verifyingContract;
	}

	@AuthRequire
	@GetMapping("/custodian-proxy")
	public String getCustodianProxyAddress() {
		String s = SpringTool.getBean(Environment.class).getProperty("CUSTODIAN_PROXY_ADDRESS");
		return s;
	}
	
	/**
	 * Get chain ID
	 * Get the chain ID of Conflux network used by DEX.
	 */
	@GetMapping("/chainid")
	public long getChainId() {
		return Domain.boomflow().chainId;
	}
	
	/**
	 * Get fee configuration
	 * Get the fee configuration of DEX that used for trade settlement.
	 */
	@GetMapping("/fee")
	public FeeData getFeeData() {
		return SpringTool.getBean(FeeService.class).getData();
	}

}
