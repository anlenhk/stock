package com.hundsun.fcloud.tools.stockctrl.service;

/**
 * Created by Gavin Hu on 2015/1/4.
 */
public class StockQueryException extends RuntimeException {

    public StockQueryException(String message) {
        super(message);
    }

    public StockQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
