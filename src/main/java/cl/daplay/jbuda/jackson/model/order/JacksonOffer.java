package cl.daplay.jbuda.jackson.model.order;

import cl.daplay.jbuda.jackson.BigDecimalToStringSerializer;
import cl.daplay.jbuda.model.OrderBook;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.math.BigDecimal;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder({ "price", "amount" })
@JsonIgnoreProperties(ignoreUnknown = true)
public class JacksonOffer implements OrderBook.Offer, Serializable {

    private static final long serialVersionUID = 2017_08_06;

    @JsonSerialize(using = BigDecimalToStringSerializer.class)
    private final BigDecimal price;
    @JsonSerialize(using = BigDecimalToStringSerializer.class)
    private final BigDecimal amount;

    @JsonCreator
    public JacksonOffer(@JsonProperty("price") final BigDecimal price,
                        @JsonProperty("amount") final BigDecimal amount) {
        this.price = price;
        this.amount = amount;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JacksonOffer offer = (JacksonOffer) o;

        if (price != null ? !price.equals(offer.price) : offer.price != null) return false;
        return amount != null ? amount.equals(offer.amount) : offer.amount == null;
    }

    @Override
    public int hashCode() {
        int result = price != null ? price.hashCode() : 0;
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Order{" +
                "price=" + price +
                ", amount=" + amount +
                '}';
    }
}

