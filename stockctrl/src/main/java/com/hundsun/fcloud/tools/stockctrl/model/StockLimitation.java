package com.hundsun.fcloud.tools.stockctrl.model;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Gavin Hu on 2015/1/19.
 */
public class StockLimitation {

    private String limitName;

    private Long limitAmount;

    private Long limitInvestors;

    private AtomicLong stockAmount = new AtomicLong();

    private Map<String, AtomicInteger> stockInvestors = new HashMap<String, AtomicInteger>() {
        @Override
        public AtomicInteger get(Object key) {
            AtomicInteger value = super.get(key);
            if(value==null) {
                value = new AtomicInteger();
                super.put((String) key, value);
            }
            return value;
        }
    };

    private Map<String, StockCtrl> stockCtrlMap = new HashMap<String, StockCtrl>();


    public String getLimitName() {
        return limitName;
    }

    public void setLimitName(String limitName) {
        this.limitName = limitName;
    }

    public Long getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(Long limitAmount) {
        this.limitAmount = limitAmount;
    }

    public Long getLimitInvestors() {
        return limitInvestors;
    }

    public void setLimitInvestors(Long limitInvestors) {
        this.limitInvestors = limitInvestors;
    }

    public AtomicLong getStockAmount() {
        return stockAmount;
    }

    public Map<String, AtomicInteger> getStockInvestors() {
        return stockInvestors;
    }

    public void putStockCtrl(StockCtrl stockCtrl) {
        this.stockCtrlMap.put(stockCtrl.getRequestNo(), stockCtrl);
    }

    public StockCtrl getStockCtrl(StockCtrl stockCtrl) {
        return this.stockCtrlMap.get(stockCtrl.getRequestNo());
    }

    public Map<String, StockCtrl> getStockCtrlMap() {
        return this.stockCtrlMap;
    }

    public StockCtrl removeStockCtrl(StockCtrl stockCtrl) {
        return this.stockCtrlMap.remove(stockCtrl.getRequestNo());
    }

}
