package conflux.dex.common.channel;

public interface Receiver<T> {
	
	T receive() throws InterruptedException;

}
