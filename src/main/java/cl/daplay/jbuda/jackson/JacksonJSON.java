package cl.daplay.jbuda.jackson;

import cl.daplay.jbuda.JBuda;
import cl.daplay.jbuda.JSON;
import cl.daplay.jbuda.jackson.dto.*;
import cl.daplay.jbuda.model.ApiKey;
import cl.daplay.jbuda.model.JBudaException;
import cl.daplay.jbuda.model.Page;
import cl.daplay.jbuda.model.Ticker;
import cl.daplay.jbuda.model.Balance;
import cl.daplay.jbuda.model.Deposit;
import cl.daplay.jbuda.model.Market;
import cl.daplay.jbuda.model.Order;
import cl.daplay.jbuda.model.OrderBook;
import cl.daplay.jbuda.model.Trades;
import cl.daplay.jbuda.model.Withdrawal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

public enum JacksonJSON implements JSON {
    INSTANCE;

    public static ObjectMapper newObjectMapper() {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        return new ObjectMapper()
                .setDateFormat(simpleDateFormat)
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());
    }

    private final ObjectMapper objectMapper = newObjectMapper();
    private final DecimalFormat decimalFormat = JBuda.newBigDecimalFormat();

    @Override
    public String newAPIKey(String name, Instant expiration) throws IOException {
        final Map payload = new LinkedHashMap<>();

        payload.put("name", name);
        payload.put("expiration_time", expiration);

        return objectMapper.writeValueAsString(singletonMap("api_key", payload));
    }

    @Override
    public String newOrder(String marketId, String orderType, String orderPriceType, BigDecimal qty, BigDecimal price) throws IOException {
        final Map payload = new LinkedHashMap<>();

        payload.put("type", orderType);
        payload.put("price_type", orderPriceType);
        payload.put("limit", decimalFormat.format(price));
        payload.put("amount", decimalFormat.format(qty));

        return objectMapper.writeValueAsString(singletonMap("order", payload));
    }

    @Override
    public String cancelOrder(long __) throws IOException {
        return objectMapper.writeValueAsString(singletonMap("state", "CANCELING"));
    }

    @Override
    public ApiKey apiKey(String json) throws IOException {
        return objectMapper.readValue(json, ApiKeyDTO.class).getApiKey();
    }

    @Override
    public List<Market> markets(String json) throws IOException {
        return objectMapper.readValue(json, MarketsDTO.class)
                .getMarkets()
                .stream()
                .collect(toList());
    }

    @Override
    public Order order(String json) throws IOException {
        return objectMapper.readValue(json, OrderDTO.class).getOrder();
    }

    @Override
    public Ticker ticker(String json) throws IOException {
        return objectMapper.readValue(json, TickerDTO.class).getTicker();
    }

    @Override
    public OrderBook orderBook(String json) throws IOException {
        return objectMapper.readValue(json, OrderBookDTO.class).getOrderBook();
    }

    @Override
    public Balance balance(String json) throws IOException {
        return objectMapper.readValue(json, BalanceDTO.class).getBalance();
    }

    @Override
    public Trades trades(String json) throws IOException {
        return objectMapper.readValue(json, TradesDTO.class).getTrades();
    }

    @Override
    public List<Balance> balances(String json) throws IOException {
        return objectMapper.readValue(json, BalancesDTO.class).getBalances()
                .stream()
                .collect(toList());

    }

    @Override
    public List<Order> orders(String json) throws IOException {
        return objectMapper.readValue(json, OrdersDTO.class).getOrders()
                .stream()
                .collect(toList());
    }

    @Override
    public List<Deposit> deposits(String json) throws IOException {
        return objectMapper.readValue(json, DepositsDTO.class).getDeposits()
                .stream()
                .collect(toList());
    }

    @Override
    public List<Withdrawal> withdrawls(String json) throws IOException {
        return objectMapper.readValue(json, WithdrawalsDTO.class).getWithdrawals()
                .stream()
                .collect(toList());
    }

    @Override
    public Page page(String json) throws IOException {
        return objectMapper.readValue(json, PageDTO.class).getMeta();
    }

    @Override
    public JBudaException exception(int statusCode, String json) throws Exception {
        final ExceptionDTO exceptionDTO = objectMapper.readValue(json, ExceptionDTO.class);

        if (null == exceptionDTO) {
            throw new Exception(format("Buda request failed. status code: '%d' response body: '%s'", statusCode, json));
        }

        return error2Error(statusCode, exceptionDTO);
    }

    private JBudaException.Detail error2Error(ExceptionDTO.ErrorDTO in) {
        return new JBudaException.Detail(in.resource, in.field, in.code, in.message);
    }

    private JBudaException error2Error(final int statusCode, ExceptionDTO in) {
        final ExceptionDTO.ErrorDTO[] dtos = in.errors == null ? new ExceptionDTO.ErrorDTO[0] : in.errors;

        final JBudaException.Detail[] details = stream(dtos).map(this::error2Error).toArray(JBudaException.Detail[]::new);
        return new JBudaException(statusCode, in.message, in.code, details);
    }

}
