package conflux.dex.controller.request;

import java.util.Arrays;
import java.util.List;

import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import conflux.dex.common.BusinessException;

public class SystemCommand extends AdminRequest {
	
	public static final String CMD_SUSPEND = "dex.suspend";
	public static final String CMD_RESUME = "dex.resume";
	public static final String CMD_LIST_CONFIG = "dex.list.config";
	public static final String CMD_REFRESH_CTOKEN = "dex.refresh.ctoken";

	/**
	 * System command to execute.
	 */
	public String command;
	/**
	 * Optional comment.
	 */
	public String comment;
	
	public SystemCommand() {
	}

	public SystemCommand(String command, String comment) {
		this.command = command;
		this.comment = comment;
	}
	
	@Override
	protected void validate() {
		if (this.command == null || this.command.isEmpty()) {
			throw BusinessException.validateFailed("command not specified");
		}
	}
	
	@Override
	protected List<RlpType> getEncodeValues() {
		return Arrays.asList(
				RlpString.create(this.command),
				RlpString.create(this.comment == null ? "" : this.comment));
	}

}
