package com.hundsun.fcloud.tools.stockctrl.model;

/**
 * Created by huke10591 on 2015/2/2.
 */
public enum StockState {
    LOCKED(0),      //已锁定
    PAID(1);        //已支付

    private int value;

    StockState(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
