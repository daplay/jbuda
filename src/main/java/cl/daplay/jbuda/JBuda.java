package cl.daplay.jbuda;

import cl.daplay.jbuda.http.DefaultHTTPClient;
import cl.daplay.jbuda.http.RetryHTTPClient;
import cl.daplay.jbuda.jackson.JacksonJSON;
import cl.daplay.jbuda.model.ApiKey;
import cl.daplay.jbuda.model.Page;
import cl.daplay.jbuda.model.Ticker;
import cl.daplay.jbuda.model.Balance;
import cl.daplay.jbuda.model.Deposit;
import cl.daplay.jbuda.model.Market;
import cl.daplay.jbuda.model.Order;
import cl.daplay.jbuda.model.OrderBook;
import cl.daplay.jbuda.model.Trades;
import cl.daplay.jbuda.model.Withdrawal;
import cl.daplay.jbuda.signer.DefaultSigner;
import cl.daplay.jbuda.signer.NOOPSigner;
import cl.daplay.jfun.ThrowingFunction;
import cl.daplay.lazylist.LazyList;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;

public class JBuda {

    /**
     * @return default nonce implementation, can't be shared among clients
     */
    public static LongSupplier newNonce() {
        return new AtomicLong(currentTimeMillis())::getAndIncrement;
    }

    public static DecimalFormat newBigDecimalFormat(){
        final DecimalFormat format = new DecimalFormat();

        final DecimalFormatSymbols decimalFormatSymbols = format.getDecimalFormatSymbols();
        decimalFormatSymbols.setDecimalSeparator('.');

        format.setMaximumFractionDigits(9);
        format.setMinimumFractionDigits(1);
        format.setGroupingUsed(false);
        format.setDecimalFormatSymbols(decimalFormatSymbols);

        return format;
    }

    private final static Logger LOGGER = Logger.getLogger(JBuda.class.getName());

    private final static VersionSupplier VERSION_SUPPLIER = VersionSupplier.INSTANCE;

    /**
     * by default, this client will retry any HTTP error 5 times, returning the fifth Exception.
     *
     * You may customize this number by environment variable "JBUDA.HTTP_MAX_RETRY"
     */
    private final static int HTTP_MAX_RETRY = Integer.parseInt(System.getProperty("JBUDA.HTTP_MAX_RETRY", "5"), 10);

    private final DecimalFormat bigDecimalFormat;
    private final HTTPClient httpClient;
    private final JSON json;
    private final Signer defaultSigner;
    private final Signer noSignatureSigner;

    public JBuda() {
        this(null, null, JBuda.newNonce(), null, HTTP_MAX_RETRY);
    }

    public JBuda(final String key, final String secret) {
        this(key, secret, JBuda.newNonce(), null, HTTP_MAX_RETRY);
    }

    public JBuda(final String key, final String secret, final LongSupplier nonceSupplier) {
        this(key, secret, nonceSupplier, null, HTTP_MAX_RETRY);
    }

    public JBuda(final String key, final String secret, final LongSupplier nonceSupplier, final InetSocketAddress httpProxy) {
        this(key, secret, nonceSupplier, JacksonJSON.INSTANCE, httpProxy == null ? null : new Proxy(Proxy.Type.HTTP, httpProxy), HTTP_MAX_RETRY);
    }

    public JBuda(final String key, final String secret, final LongSupplier nonceSupplier, final InetSocketAddress httpProxy, int httpMaxRetry) {
        this(key, secret, nonceSupplier, JacksonJSON.INSTANCE, httpProxy == null ? null : new Proxy(Proxy.Type.HTTP, httpProxy), httpMaxRetry);
    }

    public JBuda(final String key, final String secret, final LongSupplier nonceSupplier, final JacksonJSON json, final Proxy proxy, int httpMaxRetry) {
        this(new RetryHTTPClient(new DefaultHTTPClient(proxy, key, nonceSupplier, VERSION_SUPPLIER.get()), httpMaxRetry),
                newBigDecimalFormat(), 
                json,
                new DefaultSigner(secret),
                NOOPSigner.INSTANCE);
    }

    public JBuda(JBuda other) {
        this.bigDecimalFormat = other.bigDecimalFormat;
        this.httpClient = other.httpClient;
        this.json = other.json;
        this.defaultSigner = other.defaultSigner;
        this.noSignatureSigner = other.noSignatureSigner;
    }

    public JBuda(final HTTPClient httpClient,
                 final DecimalFormat bigDecimalFormat,
                 final JSON json,
                 final Signer defaultSigner,
                 final Signer noSignatureSigner) {
        this.bigDecimalFormat = bigDecimalFormat;
        this.httpClient = httpClient;
        this.json = json;
        this.defaultSigner = defaultSigner;
        this.noSignatureSigner = noSignatureSigner;
    }

    public ApiKey newAPIKey(final String name, final Instant expiration) throws Exception {
        final String path = "/api/v2/api_keys";

        return httpClient.post(path, defaultSigner, json.newAPIKey(name, expiration), responseHandler(json::apiKey));
    }

    public Order newOrder(final String marketId, final String orderType, final String orderPriceType, final BigDecimal qty, final BigDecimal price) throws Exception {
        final String path = format("/api/v2/markets/%s/orders", marketId).toLowerCase();
        final String payload = json.newOrder(marketId, orderType, orderPriceType, qty, price);

        return httpClient.post(path, defaultSigner, payload, responseHandler(json::order));
    }

    public Trades getTrades(final String marketId) throws Exception {
        return getTrades(marketId, null);
    }

    public Trades getTrades(final String marketId, final Instant timestamp) throws Exception {
        String path = format("/api/v2/markets/%s/trades", marketId).toLowerCase();

        if (timestamp != null) {
            path += "?timestamp=" + timestamp.toEpochMilli();
        }

        return httpClient.get(path, noSignatureSigner, responseHandler(json::trades));
    }

    public Order cancelOrder(final long orderId) throws Exception {
        checkOrderId(orderId);
        final String path = format("/api/v2/orders/%d", orderId);

        String payload = json.cancelOrder(orderId);

        return httpClient.put(path, defaultSigner, payload, responseHandler(json::order));
    }

    public List<Market> getMarkets() throws Exception {
        final String path = "/api/v2/markets";
        return httpClient.get(path, noSignatureSigner, responseHandler(json::markets));
    }

    public Ticker getTicker(final String marketId) throws Exception {
        final String path = format("/api/v2/markets/%s/ticker", marketId).toLowerCase();
        return httpClient.get(path, noSignatureSigner, responseHandler(json::ticker));
    }

    public OrderBook getOrderBook(final String marketId) throws Exception {
        final String path = format("/api/v2/markets/%s/order_book", marketId).toLowerCase();
        return httpClient.get(path, noSignatureSigner, responseHandler(json::orderBook));
    }

    public Balance getBalance(final String currency) throws Exception {
        final String path = format("/api/v2/balances/%s", currency).toLowerCase();
        return httpClient.get(path, defaultSigner, responseHandler(json::balance));
    }

    public List<Balance> getBalances() throws Exception {
        return httpClient.get("/api/v2/balances", defaultSigner, responseHandler(json::balances));
    }

    public List<Order> getOrders(final String marketId) throws Exception {
        final String path = format("/api/v2/markets/%s/orders", marketId).toLowerCase();
        return newPaginatedList(path, defaultSigner, json::orders);
    }

    public List<Order> getOrders(final String marketId, final String orderState) throws Exception {
        final String path = format("/api/v2/markets/%s/orders?state=%s&algo=", marketId, orderState).toLowerCase();
        return newPaginatedList(path, defaultSigner, json::orders);
    }

    public List<Order> getOrders(final String marketId, final BigDecimal minimunExchanged) throws Exception {
        final String path = format("/api/v2/markets/%s/orders?minimun_exchanged=%s", marketId, bigDecimalFormat.format(minimunExchanged)).toLowerCase();
        return newPaginatedList(path, defaultSigner, json::orders);
    }

    public List<Order> getOrders(final String marketId, final String orderState, final BigDecimal minimunExchanged) throws Exception {
        final String path = format("/api/v2/markets/%s/orders?state=%s&minimun_exchanged=%s", marketId, orderState, bigDecimalFormat.format(minimunExchanged)).toLowerCase();
        return newPaginatedList(path, defaultSigner, json::orders);
    }

    public Order getOrder(final long orderId) throws Exception {
        checkOrderId(orderId);
        final String path = format("/api/v2/orders/%d", orderId).toLowerCase();
        return httpClient.get(path, defaultSigner, responseHandler(json::order));
    }

    public List<Deposit> getDeposits(final String currency) throws Exception {
        final String path = format("/api/v2/currencies/%s/deposits", currency).toLowerCase();
        return newPaginatedList(path, defaultSigner, json::deposits);
    }

    public List<Withdrawal> getWithdrawals(final String currency) throws Exception {
        final String path = format("/api/v2/currencies/%s/withdrawals", currency).toLowerCase();
        return newPaginatedList(path, defaultSigner, json::withdrawls);
    }

    public String getVersion() {
        return VERSION_SUPPLIER.get();
    }

    // ** implementation methods **

    private <T> LazyList<T> newPaginatedList(String path,
                                             Signer signer,
                                             ThrowingFunction<String, List<T>> parseList) throws Exception {
        return httpClient.get(path, signer, responseHandler((responseBody) -> {
            final List<T> page = parseList.apply(responseBody);
            final Page pagination = json.page(responseBody);

            final int totalPages = pagination.getTotalPages();
            final int totalCount = pagination.getTotalCount();

            return new LazyList<>(page, index -> {
                final boolean append = path.contains("?");
                final String nextPath = format("%s%spage=%d", path, append ? "&" : "?", index + 1);

                return httpClient.get(nextPath, signer, responseHandler(parseList));
            }, totalPages, totalCount);
        }));
    }

    private void checkOrderId(final long orderId) {
        if (orderId <= 0) {
            throw new IllegalArgumentException(format("Invalid order id: %d", orderId));
        }
    }

    private <T> HTTPClient.HTTPResponseHandler<T> responseHandler(final ThrowingFunction<String, T> mapper) {
        return (statusCode, responseBody) -> {
            // OK(200) or CREATED(201)
            final boolean successful = statusCode == 200 || statusCode == 201;
            if (!successful) {
                throw json.exception(statusCode, responseBody);
            }

            return mapper.apply(responseBody);
        };
    }

}
