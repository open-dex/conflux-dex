package conflux.dex.blockchain;

import java.time.Duration;

import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;

import conflux.web3j.Cfx;
import okhttp3.OkHttpClient;

public class CfxBuilder {
	
	private String url;
	
	private int retry;
	private long retryIntervalMillis;
	
	private long callTimeoutMillis;
	
	public CfxBuilder(String url) {
		this.url = url;
	}
	
	public CfxBuilder withRetry(int retry, long intervalMillis) {
		this.retry = retry;
		this.retryIntervalMillis = intervalMillis;
		return this;
	}
	
	public CfxBuilder withCallTimeout(long millis) {
		this.callTimeoutMillis = millis;
		return this;
	}
	
	public Cfx build() {
		OkHttpClient client = new OkHttpClient.Builder()
				.callTimeout(Duration.ofMillis(this.callTimeoutMillis))
				.build();
		Web3jService service = new HttpService(this.url, client);
		return Cfx.create(service, this.retry, this.retryIntervalMillis);
	}

}
