package conflux.dex.controller.request;

import conflux.dex.common.Validators;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class CacheOperation<T>  extends AdminRequest implements Serializable {

	private static final long serialVersionUID = 108746390348773799L;
	
	public static final String OPERATION_READ = "READ";
    public static final String OPERATION_REMOVE = "REMOVE";
    private String cacheName;
    private String key;
    private T value;

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    @Override
    protected void validate() {
        Validators.nonEmpty(cacheName, "cache name");
        Validators.nonEmpty(key, "cache key");
    }

    @Override
    protected List<RlpType> getEncodeValues() {
        return Arrays.asList(
                RlpString.create(cacheName),
                RlpString.create(key)
        );
    }
}
