package com.hundsun.fcloud.tools.stockctrl;

import com.hundsun.fcloud.servlet.api.ServletMessage;
import com.hundsun.fcloud.servlet.api.ServletRequest;
import com.hundsun.fcloud.servlet.api.ServletResponse;
import com.hundsun.fcloud.servlet.caller.ServletCaller;
import com.hundsun.fcloud.servlet.caller.pool.PoolableServletCaller;
import com.hundsun.fcloud.servlet.share.DefaultServletRequest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by Gavin Hu on 2015/1/19.
 */
public class StockCtrlTest {

    @Test
    public void testLock() throws Exception {
        //
        final ServletCaller servletCaller = new PoolableServletCaller(new String[]{"localhost", "192.168.190.191"}, new int[]{6161, 6161}, 10);
        //
<<<<<<< HEAD
        List<Future> futureList = new ArrayList<Future>();
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        for(int i=0; i<200; i++) {
            //
            final int finalI = i;
            Future future = executorService.submit(new Runnable() {
                @Override
                public void run() {
                    //
                    try {
                        ServletRequest servletRequest = new DefaultServletRequest();
                        servletRequest.setHeader(ServletMessage.HEADER_CODEC, "26");
                        servletRequest.setParameter("funcNo", "26");
                        servletRequest.setParameter("entrust", "1");
                        servletRequest.setParameter("netNo", "8888");
                        servletRequest.setParameter("operatorCode", "06843");
                        //
                        servletRequest.setParameter("requestNo", "1000" + String.valueOf(finalI));
                        servletRequest.setParameter("balance", "4000");
                        servletRequest.setParameter("operateCode", "0");
                        servletRequest.setParameter("tradeAcco", "T200000000000" + String.valueOf(finalI));
                        servletRequest.setParameter("fundCode", "600570");
                        servletRequest.setParameter("bizCode", "022");
                        //
                        ServletResponse servletResponse = servletCaller.call(servletRequest);
                        System.out.println(servletResponse.getParameter("flag"));
                        System.out.println(servletResponse.getParameter("errorCode"));
                        System.out.println(servletResponse.getParameter("errorMsg"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            //
            futureList.add(future);
        }
=======
        servletRequest.setParameter("requestNo", "20000011");
        servletRequest.setParameter("balance", "4000");
        servletRequest.setParameter("operateCode", "3");    // 0: lock  1:unlock  2: decrease(pay)  3: increase(unPay)
        servletRequest.setParameter("tradeAcco", "T2000000000000001");
        servletRequest.setParameter("fundCode", "600570");
        servletRequest.setParameter("bizCode", "020");
>>>>>>> fa7d14453edc7287f0ba13b12a1a37079a52aef4
        //
        for(Future future : futureList) {
            try {
                future.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        //
        servletCaller.close();
    }

}
