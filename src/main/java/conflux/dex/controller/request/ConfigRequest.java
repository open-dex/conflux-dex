package conflux.dex.controller.request;

import conflux.dex.common.Validators;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import java.util.Arrays;
import java.util.List;

public class ConfigRequest extends AdminRequest {
    public String name;
    public String value;
    @Override
    protected void validate() {
        Validators.nonEmpty(name, "name");
        Validators.nonEmpty(value, "value");
    }

    @Override
    protected List<RlpType> getEncodeValues() {
        return Arrays.asList(
                RlpString.create(name),
                RlpString.create(value)
        );
    }
}
