package com.hundsun.fcloud.tools.stockctrl.service.jdbc;

import com.hundsun.fcloud.tools.stockctrl.model.StockCtrl;
import com.hundsun.fcloud.tools.stockctrl.model.StockLimitation;
import com.hundsun.fcloud.tools.stockctrl.service.AbstractStockService;
import oracle.sql.TIMESTAMP;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Gavin Hu on 2015/1/19.
 */
public class JdbcBasedStockService extends AbstractStockService {

    private QueryRunner queryRunner;

    public void setQueryRunner(QueryRunner queryRunner) {
        this.queryRunner = queryRunner;
    }

    @Override
    protected List<StockLimitation> loadStockLimitations() {
        //
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();
            //
            List<StockLimitation> stockLimitationList = loadStockLimitations(connection);
            for(StockLimitation stockLimitation : stockLimitationList) {
                //
                List<StockCtrl> stockCtrls = loadStockCtrlsByLimitName(connection, stockLimitation.getLimitName());
                for(StockCtrl stockCtrl : stockCtrls) {
                    stockLimitation.putStockCtrl(stockCtrl);
                    AtomicInteger number = stockLimitation.getStockInvestors().get(stockCtrl.getTradeAcco());
                    number.incrementAndGet();
                    //
                    stockLimitation.getStockAmount().addAndGet(stockCtrl.getBalance());
                }
            }
            return stockLimitationList;
            //
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        } finally {
            if(connection!=null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
        //
        return null;
    }

    @Override
    protected void afterSuccessLock(StockLimitation stockLimitation, StockCtrl lockedStockCtrl) {
        //
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();
            //
            insertStockCtrl(connection, lockedStockCtrl);
            //
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        } finally {
            if(connection!=null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    @Override
    protected void afterSuccessUnlock(StockLimitation stockLimitation, StockCtrl removedStockCtrl) {
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();
            //
            deleteStockCtrl(connection, removedStockCtrl);
            //
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        } finally {
            if(connection!=null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    @Override
    protected void afterSuccessIncrease(StockLimitation stockLimitation, StockCtrl removedStockCtrl) {
        //
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();
            //
            deleteStockCtrl(connection, removedStockCtrl);
            //
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        } finally {
            if(connection!=null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    private static final String INSERT_STOCK_CTRL_SQL = "insert into STOCK_CTRL_LIST " +
            "(REQUEST_NO, BALANCE, TRADE_ACCO, STOCK_CODE, BIZ_CODE, REQUEST_DATE) values (?, ?, ?, ?, ?, ?)";
    private void insertStockCtrl(Connection connection, StockCtrl stockCtrl) throws SQLException {
        //
        Object[] params = new Object[] {
                stockCtrl.getRequestNo(),
                stockCtrl.getBalance(),
                stockCtrl.getTradeAcco(),
                stockCtrl.getStockCode(),
                stockCtrl.getBizCode(),
                stockCtrl.getRequestDate()
        };
        //
        queryRunner.insert(connection, INSERT_STOCK_CTRL_SQL, new MapHandler(), params);
    }

    private static final String DELETE_STOCK_CTRL_SQL = "delete from STOCK_CTRL_LIST " +
            "where REQUEST_NO=? and BALANCE=? and TRADE_ACCO=? and STOCK_CODE=? and BIZ_CODE=?";
    private void deleteStockCtrl(Connection connection, StockCtrl stockCtrl) throws SQLException {
        //
        Object[] params = new Object[] {
                stockCtrl.getRequestNo(),
                stockCtrl.getBalance(),
                stockCtrl.getTradeAcco(),
                stockCtrl.getStockCode(),
                stockCtrl.getBizCode()
        };
        //
        queryRunner.update(DELETE_STOCK_CTRL_SQL, params);
    }


    private static final String COUNT_STOCK_CTRL_BY_LIMIT_NAME = "select count(*) as COUNT from STOCK_CTRL_LIST " +
            "where STOCK_CODE=? and BIZ_CODE=?";
    private long countStockInvestorsByLimitName(Connection connection, String limitName) throws SQLException {
        //
        String[] params = limitName.split("\\.");
        //
        Map<String, Object> map = queryRunner.query(connection, COUNT_STOCK_CTRL_BY_LIMIT_NAME, new MapHandler(), params);
        BigDecimal count = (BigDecimal) map.get("COUNT");
        return count.longValue();
    }

    private static final String LOAD_STOCK_LIMITATION_SQL = "select * from STOCK_LIMITATION";
    private List<StockLimitation> loadStockLimitations(Connection connection) throws SQLException {
        //
        List<StockLimitation> stockLimitationList = new ArrayList<StockLimitation>();
        List<Map<String, Object>> mapList = queryRunner.query(connection, LOAD_STOCK_LIMITATION_SQL, new MapListHandler());
        for(Map<String, Object> map : mapList) {
            String limitName = (String) map.get("LIMIT_NAME");
            BigDecimal limitAmount = (BigDecimal) map.get("LIMIT_AMOUNT");
            BigDecimal limitInvestors = (BigDecimal) map.get("LIMIT_INVESTORS");
            //
            StockLimitation stockLimitation = new StockLimitation();
            stockLimitation.setLimitName(limitName);
            stockLimitation.setLimitInvestors(limitInvestors.longValue());
            stockLimitation.setLimitAmount(limitAmount.longValue());
            //
            stockLimitationList.add(stockLimitation);
        }
        //
        return stockLimitationList;
    }

    private static final String UPDATE_STOCK_LIMITATION_SQL = "update STOCK_LIMITATION " +
            "set CURRENT_VALUE=? where LIMIT_NAME=? and LIMIT_TYPE=?";
    private void updateStockLimitation(StockLimitation stockLimitation) {
        //

    }

    private static final String LOAD_STOCK_CTRLS_BY_LIMIT_NAME = "select * from STOCK_CTRL_LIST " +
            "where STOCK_CODE=? and BIZ_CODE=?";
    private List<StockCtrl> loadStockCtrlsByLimitName(Connection connection, String limitName) throws SQLException {
        //
        String[] params = limitName.split("\\.");
        //
        List<StockCtrl> stockCtrlList = new ArrayList<StockCtrl>();
        List<Map<String, Object>> mapList = queryRunner.query(connection, LOAD_STOCK_CTRLS_BY_LIMIT_NAME, new MapListHandler(), params);
        for(Map<String, Object> map : mapList) {
            //
            String requestNo = (String) map.get("REQUEST_NO");
            BigDecimal balance = (BigDecimal) map.get("BALANCE");
            String tradeAcco = (String) map.get("TRADE_ACCO");
            String stockCode = (String) map.get("STOCK_CODE");
            String bizCode = (String) map.get("BIZ_CODE");
            TIMESTAMP requestDate = (TIMESTAMP) map.get("REQUEST_DATE");
            //
            StockCtrl stockCtrl = new StockCtrl();
            stockCtrl.setRequestNo(requestNo);
            stockCtrl.setBalance(balance.longValue());
            stockCtrl.setTradeAcco(tradeAcco);
            stockCtrl.setStockCode(stockCode);
            stockCtrl.setBizCode(bizCode);
            stockCtrl.setRequestDate(requestDate.timestampValue());
            //
            stockCtrlList.add(stockCtrl);
        }
        //
        return stockCtrlList;
    }

}
