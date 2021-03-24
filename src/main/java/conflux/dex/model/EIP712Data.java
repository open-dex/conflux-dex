package conflux.dex.model;

public class EIP712Data {
	/**
	 * EIP712 message.
	 */
	private Object message;
	/**
	 * EIP712 message hash.
	 */
	private String hash;
	
	public static EIP712Data create(Object message, String hash) {
		EIP712Data data = new EIP712Data();
		data.message = message;
		data.hash = hash;
		return data;
	}
	
	public Object getMessage() {
		return message;
	}
	
	public void setMessage(Object message) {
		this.message = message;
	}
	
	public String getHash() {
		return hash;
	}
	
	public void setHash(String hash) {
		this.hash = hash;
	}
	
}
