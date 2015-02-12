package com.hundsun.fcloud.tools.stockctrl;

import com.hundsun.fcloud.servlet.api.ServletMessage;
import com.hundsun.fcloud.servlet.api.ServletRequest;
import com.hundsun.fcloud.servlet.api.ServletResponse;
import com.hundsun.fcloud.servlet.caller.ServletCaller;
import com.hundsun.fcloud.servlet.caller.pool.PoolableServletCaller;
import com.hundsun.fcloud.servlet.share.DefaultServletRequest;
import org.junit.Test;

/**
 * Created by Gavin Hu on 2015/1/19.
 */
public class StockCtrlTest {

    @Test
    public void testLock() {
        //
        ServletCaller servletCaller = new PoolableServletCaller(new String[]{"localhost"}, new int[]{6161}, 5);
        //
        ServletRequest servletRequest = new DefaultServletRequest();
        servletRequest.setHeader(ServletMessage.HEADER_CODEC, "26");
        servletRequest.setParameter("funcNo", "26");
        servletRequest.setParameter("entrust", "1");
        servletRequest.setParameter("netNo", "8888");
        servletRequest.setParameter("operatorCode", "06843");
        //
        servletRequest.setParameter("requestNo", "20000003");
        servletRequest.setParameter("balance", "4000");
        servletRequest.setParameter("operateCode", "1");
        servletRequest.setParameter("tradeAcco", "T2000000000000001");
        servletRequest.setParameter("fundCode", "600570");
        servletRequest.setParameter("bizCode", "021");
        //
        ServletResponse servletResponse = servletCaller.call(servletRequest);
        System.out.println(servletResponse.getParameter("flag"));
        System.out.println(servletResponse.getParameter("errorCode"));
        System.out.println(servletResponse.getParameter("errorMsg"));
        //System.out.println(servletResponse.getParameter("totalBalance"));
        //System.out.println(servletResponse.getParameter("remainBalance"));

        servletRequest.setParameter("operateCode", "3");

        //
        servletCaller.close();
    }

}
