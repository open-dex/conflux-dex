package conflux.dex.blockchain.crypto.legacy;

import java.util.List;

// Encode typed data via RPC of nodejs
public interface RpcEncodable {
	
	List<Object> toArray();

}
