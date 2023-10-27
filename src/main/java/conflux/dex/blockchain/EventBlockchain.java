package conflux.dex.blockchain;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.common.BusinessException;
import conflux.dex.common.Validators;
import conflux.dex.controller.AddressTool;
import conflux.web3j.types.Address;
import conflux.web3j.types.AddressType;
import org.influxdb.dto.Point.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;

import conflux.dex.blockchain.log.DepositData;
import conflux.dex.blockchain.log.ScheduleWithdrawRequest;
import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.InfluxDBReportable;
import conflux.dex.config.BlockchainConfig;
import conflux.web3j.Cfx;
import conflux.web3j.RpcException;
import conflux.web3j.request.Epoch;
import conflux.web3j.request.LogFilter;
import conflux.web3j.response.Log;

@Component
public class EventBlockchain implements InfluxDBReportable {
	private static final Logger logger = LoggerFactory.getLogger(EventBlockchain.class);
	
	private static final String DEPOSIT_EVENT = "0x5548c837ab068cf56a2c2479df0882a4922fd203edb7517321831d95078c5f62";
	private static final String SCHEDULE_EVENT = "0x0ebe7b96b8d0566030cee68cd5153d3af3eb238d56092c9493a18e6d0b568369";
	
	private static final Timer pollPerf = Metrics.timer(EventBlockchain.class, "poll", "perf");
	private static final Histogram pollEpochsStat = Metrics.histogram(EventBlockchain.class, "poll", "epochs");
	
    private final Cfx cfx;
    
    private BlockchainConfig config = new BlockchainConfig();
    private BigInteger pollEpochFrom = BigInteger.ONE;
    
    private final LogFilter filter = new LogFilter();
    private final List<conflux.web3j.types.Address> addresses = Collections.synchronizedList(new ArrayList<>());
    
    @Autowired
    public EventBlockchain(Cfx cfx) {
		this.cfx = cfx;
		this.filter.setAddress(this.addresses);
		this.filter.setTopics(Arrays.asList(
				Arrays.asList(DEPOSIT_EVENT, SCHEDULE_EVENT),
				null, null, null));
		
		Metrics.dumpReportable(this);
	}
    
    @Autowired
    public void setConfig(BlockchainConfig config) {
		this.config = config;
		this.pollEpochFrom = config.eventPollEpochFrom;
	}
    
    public BigInteger getPollEpochFrom() {
		return pollEpochFrom;
	}
    
    public void setPollEpochFrom(BigInteger pollEpochFrom) {
		this.pollEpochFrom = pollEpochFrom;
	}
    
    public List<conflux.web3j.types.Address> getAddresses() {
		return addresses;
	}
    
    public void addAddress(String address) {
		addAddress(address, false);
	}
    public void addAddress(String address, boolean silent) {
		if (this.addresses.stream().anyMatch(addr->addr.getHexAddress().equalsIgnoreCase(address))) {
			if (silent) {
				logger.warn("address already added {}", address);
				return;
			}
			throw BusinessException.validateFailed("already added:" + address);
		}
		this.addresses.add(AddressTool.address(address));
		this.filter.setAddress(this.addresses);
    }

    public boolean getLatestLogs(List<DepositData> deposits, List<ScheduleWithdrawRequest> schedule) throws RpcException {
    	// poll logs from confirmed epochs
		BigInteger confirmedEpoch = getConfirmedEpoch();
		if (confirmedEpoch.compareTo(this.pollEpochFrom) < 0) {
			return false;
		}
        
        // poll logs from the latest confirmed epoch if address not specified
        if (this.addresses.isEmpty()) {
			this.pollEpochFrom = confirmedEpoch;
			return false;
		}
        
        // limit the number of polled epochs to avoid RPC timeout
        BigInteger pollEpochTo = this.pollEpochFrom.add(this.config.eventPollEpochMax).min(confirmedEpoch);
        
        if (logger.isTraceEnabled()) {
        	logger.trace("poll event logs for epochs: from = {}, to = {}, delta = {}",
        			this.pollEpochFrom, pollEpochTo, pollEpochTo.subtract(this.pollEpochFrom));
		}
        
        // poll logs
        List<Log> logs = this.pollEventLogs(pollEpochTo);
        for (Log log : logs) {
        	if (log.getTopics().isEmpty()) {
				continue;
			}
        	switch (log.getTopics().get(0)) {
			case DEPOSIT_EVENT:
				deposits.add(new DepositData(log));
				break;
			case SCHEDULE_EVENT:
				schedule.add(new ScheduleWithdrawRequest(log));
				break;
			default:
				break;
			}
		}

        this.pollEpochFrom = pollEpochTo.add(BigInteger.ONE);
        
        return confirmedEpoch.compareTo(pollEpochTo) > 0;
    }

	public BigInteger getConfirmedEpoch() {
		return this.cfx.getEpochNumber(Epoch.latestConfirmed()).sendAndGet();
	}

	private List<Log> pollEventLogs(BigInteger pollEpochTo) throws RpcException {
    	this.filter.setFromEpoch(Epoch.numberOf(this.pollEpochFrom));
		this.filter.setToEpoch(Epoch.numberOf(pollEpochTo));
		
		pollEpochsStat.update(pollEpochTo.subtract(this.pollEpochFrom).longValueExact());

		try (Context ignored = pollPerf.time()) {
			return this.cfx.getLogs(this.filter).sendAndGet();
		}
    }
    
    @Override
    public Builder buildInfluxDBPoint(Builder builder) {
    	return builder.addField("epoch", this.pollEpochFrom.longValue());
    }
}