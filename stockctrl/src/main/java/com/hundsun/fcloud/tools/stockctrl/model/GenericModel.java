package com.hundsun.fcloud.tools.stockctrl.model;

import java.io.Serializable;

/**
 * Created by Gavin Hu on 2015/1/3.
 */
public class GenericModel implements Serializable {

    private String flag;

    private String errorCode;

    private String errorMsg;

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
