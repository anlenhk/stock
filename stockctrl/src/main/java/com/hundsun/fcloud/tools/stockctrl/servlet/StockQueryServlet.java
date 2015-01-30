package com.hundsun.fcloud.tools.stockctrl.servlet;

import com.hundsun.fcloud.servlet.api.ServletRequest;
import com.hundsun.fcloud.servlet.api.ServletResponse;
import com.hundsun.fcloud.servlet.api.annotation.Mapping;
import com.hundsun.fcloud.servlet.api.annotation.Servlet;
import com.hundsun.fcloud.tools.stockctrl.model.StockQuery;
import com.hundsun.fcloud.tools.stockctrl.service.StockService;

/**
 * Created by Gavin Hu on 2015/1/3.
 */
@Servlet("stockQueryServlet")
public class StockQueryServlet {

    private StockService stockService;

    public void setStockService(StockService stockService) {
        this.stockService = stockService;
    }

    @Mapping("HS_WEB_27")
    public void stockQuery(ServletRequest request, ServletResponse response) {
        //
        String fundCode = (String) request.getParameter("fundCode");
        String bizCode = (String) request.getParameter("bizCode");
        //
        StockQuery stockQuery = new StockQuery();
        stockQuery.setStockCode(fundCode);
        stockQuery.setBizCode(bizCode);
        stockQuery.setFlag("0");
        //
        stockQuery = this.stockService.query(stockQuery);
        //
        response.setParameter("flag", stockQuery.getFlag());
        response.setParameter("errorCode", stockQuery.getErrorCode());
        response.setParameter("errorMsg", stockQuery.getErrorMsg());
        response.setParameter("totalBalance", stockQuery.getTotalBalance());
        response.setParameter("remainBalance", stockQuery.getRemainBalance());
        response.setParameter("balance", stockQuery.getBalance());
    }

}
