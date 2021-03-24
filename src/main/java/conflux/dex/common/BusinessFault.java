package conflux.dex.common;

public enum BusinessFault {
    SignatureMissed(3, "signature missed"),

    SignatureInvalidLength(4, "invalid signature length"),
    SignatureInvalid(5, "invalid signature"),
    SystemSuspended(6, "system in maintenance"),
    TopicNotSupported(7, "topic not supported"),
    RecordAlreadyExists(8, "record already exists"),
    InvalidChecksumAddress(9, "wrong checksum address"),
    SignatureParseFail(15, "Invalid signature."),
    // currency
    CurrencyNotFound(100, "currency not found"),
    CurrencyAlreadyExists(101, "currency already exists"),
    CurrencyNotCrossChain(102, "not a cross chain currency"),

    // product
    ProductNotFound(200, "product not found"),
    ProductAlreadyExists(201, "product already exists"),
    ProductNotMatch(202, "product does not match in instant exchange product"),
    ProductOrderBookNotFound(203, "orderbook not found for instant exchange product"),
    ProductInvalidDailyLimitParameter(204, "invalid daily limit parameters"),

    // user
    UserNotFound(300, "user not found"),
    UserAccessTokenNotSpecified(301, "access token not specified"),
    UserAccessTokenNotFound(302, "access token not found"),
    UserAccessTokenExpired(303, "access token expired"),

    // account
    AccountNotFound(400, "account not found"),
    AccountBalanceNotEnough(401, "account balance not enough"),
    AccountForceWithdrawing(402, "account is in force withdrawing status"),
    AccountWithdrawAmountTooSmall(403, "amount is lower than minimal withdraw amount"),

    // order
    OrderNotFound(500, "order not found"),
    OrderNonceMismatch(501, "order nonce mismatch"),
    OrderInvalidUser(502, "order is not of current user"),
    OrderNotOpened(503, "order is not opened"),
    OrderClientIdAlreadyExists(504, "order client id already exists"),
    OrderProductNotMatch(505, "product does not match when executing instant exchange order"),
    OrderInstantExchangeError(506, "instant exchange order with invalid matching result"),

    // tick
    TickNotFound(600, "tick not found"),

    // system
    SystemPaused(1001, "system paused");

    private int code;
    private String message;
    BusinessFault(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public BusinessException rise() {
        return new BusinessException(code, message);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
