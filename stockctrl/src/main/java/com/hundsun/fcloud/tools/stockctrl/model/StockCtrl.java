package com.hundsun.fcloud.tools.stockctrl.model;

import java.sql.Timestamp;

/**
 * Created by Gavin Hu on 2015/1/3.
 */
public class StockCtrl extends GenericModel {

    private String requestNo;

    private Long balance = new Long(0);

    private String operateCode;

    private String tradeAcco;

    private String stockCode;

    private String bizCode;

    private Timestamp requestDate;

    public String getRequestNo() {
        return requestNo;
    }

    public void setRequestNo(String requestNo) {
        this.requestNo = requestNo;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }

    public void setBalance(String balanceStr) {
        this.balance = Long.parseLong(balanceStr);
    }

    public String getOperateCode() {
        return operateCode;
    }

    public void setOperateCode(String operateCode) {
        this.operateCode = operateCode;
    }

    public String getTradeAcco() {
        return tradeAcco;
    }

    public void setTradeAcco(String tradeAcco) {
        this.tradeAcco = tradeAcco;
    }

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

    public Timestamp getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(Timestamp requestDate) {
        this.requestDate = requestDate;
    }
}
