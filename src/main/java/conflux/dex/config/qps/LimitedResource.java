package conflux.dex.config.qps;

/**
 * limited resource identified by string in the format:
 * IP[:URL].
 * Example:
 * 1.2.3.4  IP
 * 1.2.3.4:/order/place place order request from one IP.
 */
public class LimitedResource {
    public Long id;
    public String key;
    public String ip;
    public String url;
    public double rate;
    public long banTimes = 0;
    public long overRateTimes = 0;

    public static LimitedResource buildResource(String key) {
        LimitedResource r = new LimitedResource();
        r.key = key;
        String[] arr = key.split("#");
        r.ip = arr[0];
        r.url = arr.length > 1 ? arr[1] : null;
        return r;
    }
}
