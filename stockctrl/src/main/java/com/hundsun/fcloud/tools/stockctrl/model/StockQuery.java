package com.hundsun.fcloud.tools.stockctrl.model;

/**
 * Created by Gavin Hu on 2015/1/3.
 */
public class StockQuery extends GenericModel {
    // private in
    private String stockCode;

    private String bizCode;
    //  private out
    private Long totalBalance;

    private Long remainBalance;

    private Long balance;

    public String getStockCode() {
        return stockCode;
    }

    public void setStockCode(String stockCode) {
        this.stockCode = stockCode;
    }

    public String getBizCode() {
        return bizCode;
    }

    public void setBizCode(String bizCode) {
        this.bizCode = bizCode;
    }

    public Long getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(Long totalBalance) {
        this.totalBalance = totalBalance;
    }

    public Long getRemainBalance() {
        return remainBalance;
    }

    public void setRemainBalance(Long remainBalance) {
        this.remainBalance = remainBalance;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }

}
