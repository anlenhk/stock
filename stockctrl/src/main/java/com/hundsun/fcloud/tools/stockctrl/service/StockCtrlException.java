package com.hundsun.fcloud.tools.stockctrl.service;

/**
 * Created by Gavin Hu on 2015/1/4.
 */
public class StockCtrlException extends RuntimeException {

    public StockCtrlException(String message) {
        super(message);
    }

    public StockCtrlException(String message, Throwable cause) {
        super(message, cause);
    }
}
