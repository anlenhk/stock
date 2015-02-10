package com.hundsun.fcloud.tools.stockctrl.model;

import java.sql.Timestamp;
import java.util.Date;

/**
 * Created by huke10591 on 2015/2/10.
 */
public class StockCtrlCleaner {

    private int id;
    private String host;
    private Timestamp loginTime;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Timestamp getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Timestamp loginTime) {
        this.loginTime = loginTime;
    }
}
