package conflux.dex.blockchain.crypto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Utils;
import conflux.dex.dao.ConfigDao;

@Component
public class Domain {
	private static Logger log = LoggerFactory.getLogger(Domain.class);
	public static final String PRIMARY_TYPE = "EIP712Domain";
	
	private static final String CONFIG_KEY_BOOMFLOW = "contract.boomflow.address";
	
	public static String defaultVersion = "1.0";
	public static long defaultChainId;
	public static String boomflowAddress;
	
	private static ConcurrentMap<String, Domain> domains = new ConcurrentHashMap<String, Domain>();
	
	public static final List<Entry> SCHEMA = Arrays.asList(
			new Entry("name", "string"),
			new Entry("version", "string"),
			new Entry("chainId", "uint256"),
			new Entry("verifyingContract", "address"));
	
	public String name;
	public String version;
	public long chainId;
	public String verifyingContract;

	public static Domain create(String name, String version, long chainId, String contract) {
		Domain domain = new Domain();
		domain.name = name;
		domain.version = version;
		domain.chainId = chainId;
		domain.verifyingContract = contract;
		log.info("create domain [{}]", name);
		return domain;
	}
	
	@Autowired
	public void init(ConfigDao dao) {
		Optional<String> boomflow = dao.getConfig(CONFIG_KEY_BOOMFLOW);
		if (!boomflow.isPresent()) {
			throw BusinessException.internalError("boomflow address not configured in database");
		}
		
		boomflowAddress = boomflow.get();
		log.info("init domain {}", boomflowAddress);
	}
	
	public static Domain boomflow() {
		if (boomflowAddress == null) {
			throw new NullPointerException("boomflow address not initialized");
		}
		
		return domains.computeIfAbsent("Boomflow", bf -> {
			return create("Boomflow", defaultVersion, defaultChainId, boomflowAddress);
		});
	}
	
	public static Domain getCRCL(String crclAddress) {
		return domains.computeIfAbsent(crclAddress, address -> {
			return create("CRCL", defaultVersion, defaultChainId, address);
		});
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}

}
