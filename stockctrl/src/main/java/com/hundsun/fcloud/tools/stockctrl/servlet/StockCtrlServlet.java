package com.hundsun.fcloud.tools.stockctrl.servlet;

import com.hundsun.fcloud.servlet.api.ServletRequest;
import com.hundsun.fcloud.servlet.api.ServletResponse;
import com.hundsun.fcloud.servlet.api.annotation.Mapping;
import com.hundsun.fcloud.servlet.api.annotation.Servlet;
import com.hundsun.fcloud.tools.stockctrl.model.StockCtrl;
import com.hundsun.fcloud.tools.stockctrl.service.StockCtrlException;
import com.hundsun.fcloud.tools.stockctrl.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Gavin Hu on 2015/1/3.
 */
@Servlet("stockCtrlServlet")
public class StockCtrlServlet {

    private static final Logger logger = LoggerFactory.getLogger(StockCtrlServlet.class);

    private StockService stockService;

    public void setStockService(StockService stockService) {
        this.stockService = stockService;
    }

    @Mapping("HS_WEB_26")
    public void stockCtrl(ServletRequest request, ServletResponse response) {
        //
        String requestNo = (String) request.getParameter("requestNo");
        String balanceStr = (String) request.getParameter("balance");
        String operateCode = (String) request.getParameter("operateCode");
        String tradeAcco = (String) request.getParameter("tradeAcco");
        String fundCode = (String) request.getParameter("fundCode");
        String bizCode = (String) request.getParameter("bizCode");
        //
        StockCtrl stockCtrl = new StockCtrl();
        stockCtrl.setRequestNo(requestNo);
        stockCtrl.setBalance(balanceStr);
        stockCtrl.setOperateCode(operateCode);
        stockCtrl.setTradeAcco(tradeAcco);
        stockCtrl.setStockCode(fundCode);
        stockCtrl.setBizCode(bizCode);
        //
        String traceMsg = String.format("交易账号：%s 申请编号：%s 申请金额：%s 基金代码：%s 业务代码：%s",
                stockCtrl.getTradeAcco(), stockCtrl.getRequestNo(), stockCtrl.getBalance(), stockCtrl.getStockCode(), stockCtrl.getBizCode());
        //
        stockCtrl.setFlag("0");
        //
        if("0".equals(operateCode)) {
            try {
                this.stockService.lock(stockCtrl);
                //
                logger.info("锁定库存成功！ [{}]", traceMsg);
                //
            } catch (Exception e) {
                //
                logger.warn("锁定库存失败！ [{}]", traceMsg, e);
                //
                stockCtrl.setFlag("2");
                stockCtrl.setErrorMsg("锁定库存失败！");
            }
        }
        else if("1".equals(operateCode)) {
            try {
                this.stockService.unlock(stockCtrl);
                //
                logger.info("解锁库存成功！ [{}]", traceMsg);
                //
            } catch (Exception e) {
                //
                logger.warn("解锁库存失败！ [{}]", traceMsg, e);
                //
                stockCtrl.setFlag("2");
                stockCtrl.setErrorMsg("解锁库存失败！");
            }
        }
        else if("2".equals(operateCode)) {
            try {
                this.stockService.decrease(stockCtrl);
                //
                logger.info("去除库存成功！");
                //
            } catch (Exception e) {
                //
                logger.error("去除库存失败！ [{}]", traceMsg, e);
                //
                stockCtrl.setFlag("2");
                stockCtrl.setErrorMsg("去除库存失败！");
            }
        }
        else if("3".equals(operateCode)) {
            try {
                this.stockService.increase(stockCtrl);
                //
                logger.info("增加库存成功！");
                //
            } catch (Exception e) {
                //
                logger.error("增加库存失败！ [{}]", traceMsg, e);
                //
                stockCtrl.setFlag("2");
                stockCtrl.setErrorMsg("增加库存失败！");
            }
        }
        else {
            logger.warn("Unsupported operateCode {}", operateCode);
        }
        //
        response.setParameter("flag", stockCtrl.getFlag());
        response.setParameter("errorCode", stockCtrl.getErrorCode());
        response.setParameter("errorMsg", stockCtrl.getErrorMsg());
    }

}
