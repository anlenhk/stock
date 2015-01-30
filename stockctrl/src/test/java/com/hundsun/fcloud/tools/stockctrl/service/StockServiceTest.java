package com.hundsun.fcloud.tools.stockctrl.service;

import com.hundsun.fcloud.tools.stockctrl.model.StockCtrl;
import com.hundsun.fcloud.tools.stockctrl.model.StockQuery;
import com.hundsun.fcloud.tools.stockctrl.service.jdbc.JdbcBasedStockService;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Created by Gavin Hu on 2015/1/4.
 */
public class StockServiceTest {

    private StockService stockService;

    private ExecutorService executorService;

    @Before
    public void setup() {
        this.executorService = Executors.newFixedThreadPool(100);
        //
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("oracle.jdbc.driver.OracleDriver");
        dataSource.setUrl("jdbc:oracle:thin:@//192.168.52.84:1521/ora10g");
        dataSource.setUsername("csyeb");
        dataSource.setPassword("csyeb");
        dataSource.setMaxActive(10);
        dataSource.setDefaultAutoCommit(true);
        dataSource.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        //
        QueryRunner queryRunner = new QueryRunner(dataSource, true);
        //
        JdbcBasedStockService jdbcBasedStockService = new JdbcBasedStockService();
        jdbcBasedStockService.setQueryRunner(queryRunner);
        jdbcBasedStockService.setTimerDelay(10);
        jdbcBasedStockService.setTimerPeriod(10);
        jdbcBasedStockService.setTimeoutPay(1);
        jdbcBasedStockService.initialize();
        //
        this.stockService = jdbcBasedStockService;
        //

    }

    @Test
    public void testLifeCycle() {
        //
        Random random = new Random();
        //
        StockCtrl stockCtrl = new StockCtrl();
        stockCtrl.setRequestNo(String.valueOf(random.nextLong()));
        stockCtrl.setStockCode("600570");
        stockCtrl.setTradeAcco("T1000000000000001");
        stockCtrl.setBizCode("022");
        stockCtrl.setBalance("10000000");
        //
        StockQuery stockQuery = new StockQuery();
        stockQuery.setStockCode(stockCtrl.getStockCode());
        stockQuery.setBizCode(stockCtrl.getBizCode());
        //
        testLock(stockCtrl);
        //
        testUnlock(stockCtrl);
        //
        testLock(stockCtrl);
        //
        testDecrease(stockCtrl);
        //
        testIncrease(stockCtrl);
        //
        testQuery(stockQuery);
    }

    public void testQuery(StockQuery stockQuery) {
        //
        stockQuery = stockService.query(stockQuery);
        System.out.println(String.format("[%s] 查询成功！", Thread.currentThread().getId()));
    }

    private void testLock(StockCtrl stockCtrl) {
        //
        stockService.lock(stockCtrl);
        System.out.println(String.format("[%s] 锁库成功！", Thread.currentThread().getId()));
    }

    private void testUnlock(StockCtrl stockCtrl) {
        //
        stockService.unlock(stockCtrl);
        System.out.println(String.format("[%s] 解锁成功！", Thread.currentThread().getId()));
    }

    private void testDecrease(StockCtrl stockCtrl) {
        stockService.decrease(stockCtrl);
        System.out.println(String.format("[%s] 去库存成功！", Thread.currentThread().getId()));
    }

    private void testIncrease(StockCtrl stockCtrl) {
        stockService.increase(stockCtrl);
        System.out.println(String.format("[%s] 加库存成功！", Thread.currentThread().getId()));
    }

    @After
    public void tearDown() {
        this.executorService.shutdownNow();
    }
}
