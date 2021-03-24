package conflux.dex.common.channel;

import conflux.dex.common.BusinessException;

public interface Sender<T> {
	
	void send(T data) throws BusinessException;
	
}
