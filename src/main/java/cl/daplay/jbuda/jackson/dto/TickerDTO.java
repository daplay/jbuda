package cl.daplay.jbuda.jackson.dto;

import cl.daplay.jbuda.jackson.model.JacksonTicker;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class TickerDTO {

    @JsonProperty("ticker")
    private final JacksonTicker ticker;

    @JsonCreator
    public TickerDTO(@JsonProperty("ticker") JacksonTicker ticker) {
        this.ticker = ticker;
    }

    public JacksonTicker getTicker() {
        return ticker;
    }

}
