package com.hundsun.fcloud.tools.stockctrl.stress;

import com.hundsun.fcloud.servlet.api.ServletMessage;
import com.hundsun.fcloud.servlet.api.ServletRequest;
import com.hundsun.fcloud.servlet.api.ServletResponse;
import com.hundsun.fcloud.servlet.caller.ServletCaller;
import com.hundsun.fcloud.servlet.caller.pool.PoolableServletCaller;
import com.hundsun.fcloud.servlet.share.DefaultServletRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.Inet4Address;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by huke10591 on 2015/2/9.
 */
public class StockCtrlTest {

    private static final int POOL_SIZE = 50;

    private static final int CASE_SIZE = 200;

    private static final int THREAD_SLEEP_TIME = 30;

    private static boolean hasUnexpected = false;

    private static String fundCode = "600570";

    private static String bizCode = "022";

    private static String tradeAcco_prefix = "T1";

    private static String requestNo_prefix = "20";

    private static int balance = 500 * 100;



    private ExecutorService executorService;

    ServletCaller servletCaller = new PoolableServletCaller(new String[]{"localhost"}, new int[]{6161}, 20);


    @Test
    public void testLock() throws Exception {

        AtomicInteger atomic = new AtomicInteger(0);
        while (atomic.get() < CASE_SIZE) {
            executorService.execute(new MyRunner(atomic.getAndAdd(1), OperateType.LOCK.getValue()));
        }

        if (hasUnexpected) {
            executorService.execute(new MyRunner(1, OperateType.LOCK.getValue()));
            executorService.execute(new MyRunner(1, OperateType.LOCK.getValue()));
        }

    }

    @Test
    public void testUnLock() throws Exception {
        testLock();

        TimeUnit.SECONDS.sleep(2);

        AtomicInteger atomic = new AtomicInteger(0);
        while (atomic.get() < CASE_SIZE) {
            executorService.execute(new MyRunner(atomic.getAndAdd(1), OperateType.UNLOCK.getValue()));
        }

        if (hasUnexpected) {
            executorService.execute(new MyRunner(1, OperateType.UNLOCK.getValue()));
        }
    }

    @Test
    public void testDecrease() throws Exception {
        testLock();

        TimeUnit.SECONDS.sleep(2);

        AtomicInteger atomic = new AtomicInteger(0);
        while (atomic.get() < CASE_SIZE) {
            executorService.execute(new MyRunner(atomic.getAndAdd(1), OperateType.DECREASE.getValue()));
        }

        if (hasUnexpected) {
            executorService.execute(new MyRunner(1, OperateType.DECREASE.getValue()));
        }
    }

    @Test
    public void testIncrease() throws Exception {

        testDecrease();

        TimeUnit.SECONDS.sleep(2);

        AtomicInteger atomic = new AtomicInteger(0);
        while (atomic.get() < CASE_SIZE) {
            executorService.execute(new MyRunner(atomic.getAndAdd(1), OperateType.INCREASE.getValue()));
        }

        if (hasUnexpected) {
            executorService.execute(new MyRunner(1, OperateType.INCREASE.getValue()));
        }
    }

    class MyRunner implements Runnable {

        private int flag;
        private int operateCode;

        public MyRunner(int flag, int operateCode) {
            this.flag = flag;
            this.operateCode = operateCode;
        }

        @Override
        public void run() {

            //
            ServletRequest servletRequest = new DefaultServletRequest();
            servletRequest.setHeader(ServletMessage.HEADER_CODEC, "26");
            servletRequest.setParameter("funcNo", "26");
            servletRequest.setParameter("entrust", "1");
            servletRequest.setParameter("netNo", "8888");
            servletRequest.setParameter("operatorCode", "06843");
            //
            servletRequest.setParameter("requestNo", requestNo_prefix + "0" + flag);
            servletRequest.setParameter("balance", balance);
            servletRequest.setParameter("operateCode", operateCode);
            servletRequest.setParameter("tradeAcco", tradeAcco_prefix + "00000" + flag);
            servletRequest.setParameter("fundCode", fundCode);
            servletRequest.setParameter("bizCode", bizCode);
            //
            try {
                ServletResponse servletResponse = servletCaller.call(servletRequest);
                System.out.println(servletResponse.getParameter("flag"));
                System.out.println(servletResponse.getParameter("errorCode"));
                System.out.println(servletResponse.getParameter("errorMsg"));
            } catch (Exception e) {
                e.printStackTrace();
            }

            //
            servletCaller.close();
        }
    }

    enum OperateType {

        //0-锁库存；1-解锁库存；2-去库存；3-加库存；
        LOCK(0),
        UNLOCK(1),
        DECREASE(2),
        INCREASE(3);

        private int value;

        OperateType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }


    public static void main(String[] args) throws Exception {
        String local = Inet4Address.getLocalHost().getHostAddress();
        System.out.println(Inet4Address.getLocalHost().getCanonicalHostName());
        System.out.println(Inet4Address.getLocalHost().getHostName());
        System.out.println(local);

        Date currentDate = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentDate);


        calendar.set(Calendar.SECOND, calendar.get(Calendar.SECOND) + 180);
        currentDate = new Date();
        if (calendar.getTime().before(currentDate)) {
            System.out.println("before....");
        } else {
            System.out.println("no");
        }
        System.out.println(calendar.getTime().toLocaleString());
        System.out.println(currentDate.toLocaleString());
    }


    @Before
    public void start() {
        System.out.println("start.....");
        executorService = Executors.newFixedThreadPool(POOL_SIZE);
    }

    @After
    public void stop() throws  Exception {
        TimeUnit.SECONDS.sleep(THREAD_SLEEP_TIME);
        System.out.println("stop.....");
        executorService.shutdown();
        System.out.println("stopped !");
    }
}
