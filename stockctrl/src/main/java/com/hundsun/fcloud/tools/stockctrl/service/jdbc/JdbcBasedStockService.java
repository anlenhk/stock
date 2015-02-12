package com.hundsun.fcloud.tools.stockctrl.service.jdbc;

import com.hundsun.fcloud.tools.stockctrl.model.StockCtrl;
import com.hundsun.fcloud.tools.stockctrl.model.StockCtrlCleaner;
import com.hundsun.fcloud.tools.stockctrl.model.StockLimitation;
import com.hundsun.fcloud.tools.stockctrl.model.StockState;
import com.hundsun.fcloud.tools.stockctrl.service.AbstractStockService;
import com.hundsun.fcloud.tools.stockctrl.service.StockCtrlException;
import oracle.sql.TIMESTAMP;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;

import javax.management.RuntimeOperationsException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
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
            /*for (StockLimitation stockLimitation : stockLimitationList) {
                //
                List<StockCtrl> stockCtrls = loadStockCtrlsByLimitName(connection, stockLimitation.getLimitName());
                for (StockCtrl stockCtrl : stockCtrls) {
                    stockLimitation.putStockCtrl(stockCtrl);
                    AtomicInteger number = stockLimitation.getStockInvestors().get(stockCtrl.getTradeAcco());
                    number.incrementAndGet();
                    //
                    stockLimitation.getStockAmount().addAndGet(stockCtrl.getBalance());
                }
            }*/
            return stockLimitationList;
            //
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        } finally {
            this.closeConnection(connection);
        }
        return null;
    }

    @Override
    protected void beforeLock(StockCtrl lockedStockCtrl,  StockLimitation limitation, int limitCountPer) {
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();
            //
            if (loadStockCtrlByRequestNo(connection, lockedStockCtrl.getRequestNo()) != null) {
                logger.error("申请编号重复: " + lockedStockCtrl.getRequestNo());
                throw new RuntimeException("申请编号重复: " + lockedStockCtrl.getRequestNo());
            }

            int size = this.loadStocksCtrlByTradeAccoAndLimitName(connection, lockedStockCtrl).size();
            if (size >= limitCountPer) {
                logger.error("交易账号为 {} , 对 {}.{} 的申请次数已经达到上限 {}!", lockedStockCtrl.getTradeAcco(), lockedStockCtrl.getStockCode(), lockedStockCtrl.getBizCode(), limitCountPer);
                throw new RuntimeException("单个账号交易申请次数达上限");
            }

            if (limitation != null) {
                //TODO: 校验总人数 & 库存量
                limitation = this.loadStockLimitationByName(connection, lockedStockCtrl.getStockCode() + "." + lockedStockCtrl.getBizCode());
                if (limitation.getLimitAmount() < limitation.getCurrentAmount() + lockedStockCtrl.getBalance()) {
                    logger.error("库存余量不足 {} ", limitation.getLimitAmount() - limitation.getCurrentAmount());
                    throw new RuntimeException("库存余量不足.");
                }

                if (limitation.getLimitInvestors() < limitation.getCurrentInvestors()) {
                    logger.error("购买人数超上限");
                    throw new RuntimeException("购买人数超上限.");
                } else if (limitation.getLimitInvestors() == limitation.getCurrentInvestors() && size == 0) {
                    logger.error("购买人数超上限");
                    throw new RuntimeException("购买人数超上限.");
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("锁库存前验证检查失败", e);
        } finally {
            this.closeConnection(connection);
        }
    }

    @Override
    protected void afterSuccessLock(StockLimitation limitation, StockCtrl lockedStockCtrl) {
        //
        logger.debug("准备锁库存....");
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();
            connection.setAutoCommit(false);

            int size = this.loadStocksCtrlByTradeAccoAndLimitName(connection, lockedStockCtrl).size();

            if (limitation != null) {
                limitation = this.loadStockLimitationByName(connection, limitation.getLimitName());
                if (size == 0) {
                    limitation.setCurrentInvestors(limitation.getCurrentInvestors() + 1);
                }

                limitation.setCurrentAmount(limitation.getCurrentAmount() + lockedStockCtrl.getBalance());
                this.updateStockLimitation(connection, limitation);
            }

            insertStockCtrl(connection, lockedStockCtrl);

            logger.debug("准备提交锁库存事物....");
            connection.commit();
            logger.debug("锁库存事物提交成功 !");
            //
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            this.rollbackConnection(connection);
            throw new RuntimeException("所库存执行失败", e);
        } finally {
            this.closeConnection(connection);
        }
    }

    @Override
    protected void beforeUnlock(StockCtrl stockCtrl) {
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();

            if (loadStatedStockCtrlByRequestNo(connection, stockCtrl.getRequestNo(), StockState.LOCKED.getValue()) == null) {
                logger.error("锁定的库存中不存在申请编号为 {} 的库存！", stockCtrl.getRequestNo());
                throw new StockCtrlException(String.format("锁定的库存中不存在申请编号为 %s 的库存！", stockCtrl.getRequestNo()));
            }

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("解锁库存前检查失败", e);
        } finally {
            this.closeConnection(connection);
        }
    }

    @Override
    protected void afterSuccessUnlock(StockLimitation limitation, StockCtrl removedStockCtrl) {
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();

            connection.setAutoCommit(false);
            deleteStockCtrl(connection, removedStockCtrl);
            //TODO: 验证是否需要减少总库存 & 总人数., removedStockCtrl 或许需要以数据库中的为准

            if (limitation != null) {
                int size = this.loadStocksCtrlByTradeAccoAndLimitName(connection, removedStockCtrl).size();

                limitation = this.loadStockLimitationByName(connection, limitation.getLimitName());
                limitation.setCurrentAmount(limitation.getCurrentAmount() - removedStockCtrl.getBalance());
                if (size == 0) {
                    limitation.setCurrentInvestors(limitation.getCurrentInvestors() - 1);
                }
                //
                this.updateStockLimitation(connection, limitation);
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            this.rollbackConnection(connection);
            throw new RuntimeException("解锁库存操作失败", e);
        } finally {
            this.closeConnection(connection);
        }
    }

    @Override
    protected void beforeIncrease(StockCtrl stockCtrl) {
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();

            if (loadStatedStockCtrlByRequestNo(connection, stockCtrl.getRequestNo(), StockState.PAID.getValue()) == null) {
                logger.error("库存中不存在申请编号为 {} 的 【已支付】 的 库存！", stockCtrl.getRequestNo());
                throw new StockCtrlException(String.format("库存中不存在申请编号为 %s 的 【已支付】 库存！", stockCtrl.getRequestNo()));
            }

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("增加库存前检查失败", e);
        } finally {
            this.closeConnection(connection);
        }
    }

    @Override
    protected void afterSuccessIncrease(StockLimitation stockLimitation, StockCtrl removedStockCtrl) {
        afterSuccessUnlock(stockLimitation, removedStockCtrl);
    }

    @Override
    protected void beforeDecrease(StockCtrl stockCtrl) {
        beforeUnlock(stockCtrl);
    }

    @Override
    protected void afterSuccessDecrease(StockLimitation stockLimitation, StockCtrl stockCtrl) {
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();
            //
            updateStockCtrl(connection, stockCtrl);
            //
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            this.rollbackConnection(connection);
            throw new RuntimeException("减少库存失败", e);
        } finally {
            this.closeConnection(connection);
        }
    }

    private static final String UPDATE_STOCK_CTRL_SQL = "update STOCK_CTRL_LIST t set t.balance = ?, t.trade_acco = ?, " +
            "t.stock_code = ?, t.biz_code = ?, t.request_date = ?, t.state = ? " +
            " where t.request_no = ?";

    private void updateStockCtrl(Connection connection, StockCtrl stockCtrl) throws SQLException {
        Object[] params = new Object[]{
                stockCtrl.getBalance(),
                stockCtrl.getTradeAcco(),
                stockCtrl.getStockCode(),
                stockCtrl.getBizCode(),
                stockCtrl.getRequestDate(),
                stockCtrl.getState(),
                stockCtrl.getRequestNo()
        };

        queryRunner.update(connection, UPDATE_STOCK_CTRL_SQL, params);
    }


    private static final String INSERT_STOCK_CTRL_SQL = "insert into STOCK_CTRL_LIST " +
            "(REQUEST_NO, BALANCE, TRADE_ACCO, STOCK_CODE, BIZ_CODE, REQUEST_DATE) values (?, ?, ?, ?, ?, ?)";

    private void insertStockCtrl(Connection connection, StockCtrl stockCtrl) throws SQLException {
        //
        Object[] params = new Object[]{
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
        Object[] params = new Object[]{
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
        for (Map<String, Object> map : mapList) {

            stockLimitationList.add(this.mapToStockLimitation(map));
        }
        //
        return stockLimitationList;
    }


    private static final String LOAD_STOCK_LIMITATION_BY_NAME = "select * from STOCK_LIMITATION t " +
            "where t.limit_name = ?";

    private StockLimitation loadStockLimitationByName(Connection connection, String limitName) throws SQLException {
        List<Map<String, Object>> mapList = queryRunner.query(connection, LOAD_STOCK_LIMITATION_BY_NAME, new MapListHandler(), limitName);
        for (Map<String, Object> map : mapList) {
            return this.mapToStockLimitation(map);
        }
        return null;
    }

    @Override
    protected StockLimitation loadStockLimitationByLimitName(String limitName) {
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();
            return this.loadStockLimitationByName(connection, limitName);
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        } finally {
            this.closeConnection(connection);
        }
        return null;
    }

    private static final String UPDATE_STOCK_LIMITATION_SQL = "update stock_limitation t " +
            "  set t.limit_amount = ?, t.limit_investors = ?, t.current_amount = ?, t.current_investors =? " +
            "  where t.limit_name = ?";

    private void updateStockLimitation(Connection connection, StockLimitation stockLimitation) throws SQLException {
        Object[] params = new Object[]{
                stockLimitation.getLimitAmount(),
                stockLimitation.getLimitInvestors(),
                stockLimitation.getCurrentAmount(),
                stockLimitation.getCurrentInvestors(),
                stockLimitation.getLimitName()
        };
        queryRunner.update(connection, UPDATE_STOCK_LIMITATION_SQL, params);
    }

    private static final String LOAD_STOCKS_CTRL_BY_TRADE_ACCO_AND_LIMIT_NAME = "select * from STOCK_CTRL_LIST " +
            "where TRADE_ACCO = ? and  STOCK_CODE=? and BIZ_CODE=? ";

    private List<StockCtrl> loadStocksCtrlByTradeAccoAndLimitName(Connection connection, StockCtrl lockedStockCtrl) throws SQLException {
        String[] params = new String[]{lockedStockCtrl.getTradeAcco(), lockedStockCtrl.getStockCode(), lockedStockCtrl.getBizCode()};

        List<StockCtrl> stockCtrlList = new ArrayList<StockCtrl>();
        List<Map<String, Object>> mapList = queryRunner.query(connection, LOAD_STOCKS_CTRL_BY_TRADE_ACCO_AND_LIMIT_NAME, new MapListHandler(), params);
        for (Map<String, Object> map : mapList) {
            stockCtrlList.add(this.mapToStockCtrl(map));
        }

        return stockCtrlList;
    }

    private static final String LOAD_STATED_STOCK_CTRL_BY_REQUEST_NO = "select * from STOCK_CTRL_LIST " +
            "where REQUEST_NO = ? and state = ?";
    private StockCtrl loadStatedStockCtrlByRequestNo(Connection connection, String requestNo, int state) throws SQLException {
        Object[] params = new Object[] {requestNo, state};
        StockCtrl stockCtrl = null;
        List<Map<String, Object>> mapList = queryRunner.query(connection, LOAD_STATED_STOCK_CTRL_BY_REQUEST_NO, new MapListHandler(), params);
        for (Map<String, Object> map : mapList) {
            stockCtrl = this.mapToStockCtrl(map);
        }

        return stockCtrl;
    }

    private static final String LOAD_STOCK_CTRL_BY_REQUEST_NO = "select * from STOCK_CTRL_LIST " +
            "where REQUEST_NO = ?";

    private StockCtrl loadStockCtrlByRequestNo(Connection connection, String requestNo) throws SQLException {
        StockCtrl stockCtrl = null;
        List<Map<String, Object>> mapList = queryRunner.query(connection, LOAD_STOCK_CTRL_BY_REQUEST_NO, new MapListHandler(), requestNo);
        for (Map<String, Object> map : mapList) {
            stockCtrl = this.mapToStockCtrl(map);
        }

        return stockCtrl;
    }

    private static final String LOAD_ALL_STOCK_CTRLS = "select * from STOCK_CTRL_LIST";

    @Override
    protected List<StockCtrl> loadAllStockCtrls() {
        List<StockCtrl> results = new ArrayList<StockCtrl>();

        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();
            List<Map<String, Object>> mapList = queryRunner.query(connection, LOAD_ALL_STOCK_CTRLS, new MapListHandler());
            for (Map<String, Object> map : mapList) {
                results.add(this.mapToStockCtrl(map));
            }
            return results;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        } finally {
            this.closeConnection(connection);
        }
        return results;
    }

    @Override
    protected StockCtrl loadStockCtrlByRequstNo(String requestNo) {
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();
            return this.loadStockCtrlByRequestNo(connection, requestNo);
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        } finally {
            this.closeConnection(connection);
        }
        return null;
    }

    private static final String LOAD_STOCK_CTRLS_BY_LIMIT_NAME = "select * from STOCK_CTRL_LIST " +
            "where STOCK_CODE=? and BIZ_CODE=?";

    private List<StockCtrl> loadStockCtrlsByLimitName(Connection connection, String limitName) throws SQLException {
        //
        String[] params = limitName.split("\\.");
        //
        List<StockCtrl> stockCtrlList = new ArrayList<StockCtrl>();
        List<Map<String, Object>> mapList = queryRunner.query(connection, LOAD_STOCK_CTRLS_BY_LIMIT_NAME, new MapListHandler(), params);
        for (Map<String, Object> map : mapList) {
            stockCtrlList.add(this.mapToStockCtrl(map));
        }
        //
        return stockCtrlList;
    }

    private static final String ACTIVE_STOCK_STRL_CLEANER = "select * from STOCK_CTRL_ClEANER";

    @Override
    protected boolean isActiveStockStrlCleaner(String host, long timerPeriod) {
        StockCtrlCleaner cleaner = new StockCtrlCleaner();
        Connection connection = null;
        try {
            connection = queryRunner.getDataSource().getConnection();
            List<Map<String, Object>> mapList = queryRunner.query(connection, ACTIVE_STOCK_STRL_CLEANER, new MapListHandler());
            if (mapList.isEmpty()) {
                cleaner.setId(1);
                cleaner.setHost(host);
                cleaner.setLoginTime(new Timestamp(System.currentTimeMillis()));
                insertStockCtrlCleaner(connection, cleaner);
                return true;
            }

            for (Map<String, Object> map : mapList) {
                Date currentDate = new Date();

                cleaner.setHost(map.get("host").toString());
                cleaner.setId(Integer.parseInt(map.get("id").toString()));
                cleaner.setLoginTime(((TIMESTAMP) map.get("LOGIN_TIME")).timestampValue());

                Calendar calendar = Calendar.getInstance();
                calendar.setTime(cleaner.getLoginTime());
                calendar.set(Calendar.SECOND, calendar.get(Calendar.SECOND) + (int) timerPeriod);
                if (host.equals(cleaner.getHost()) || calendar.getTime().before(currentDate)) {
                    cleaner.setHost(host);
                    cleaner.setLoginTime(new Timestamp(System.currentTimeMillis()));
                    this.updateStockCtrlCleaner(connection, cleaner);
                }

                return true;
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            this.rollbackConnection(connection);
        } finally {
            this.closeConnection(connection);
        }
        return false;
    }


    protected static final String INSERT_STOCK_CTRL_CLEANER = "insert into STOCK_CTRL_ClEANER(id, host, LOGIN_TIME) values (?, ?, ?)";

    protected void insertStockCtrlCleaner(Connection connection, StockCtrlCleaner cleaner) throws SQLException {
        Object[] params = new Object[]{cleaner.getId(), cleaner.getHost(), cleaner.getLoginTime()};
        queryRunner.update(connection, INSERT_STOCK_CTRL_CLEANER, params);
    }

    private static final String UPDATE_STOCK_CTRL_CLEANER = "update STOCK_CTRL_ClEANER t set t.host = ?, t.LOGIN_TIME = ? where t.id = ?";

    private void updateStockCtrlCleaner(Connection connection, StockCtrlCleaner cleaner) throws SQLException {
        Object[] params = new Object[]{cleaner.getHost(), cleaner.getLoginTime(), cleaner.getId()};
        queryRunner.update(connection, UPDATE_STOCK_CTRL_CLEANER, params);
    }

    private StockCtrl mapToStockCtrl(Map<String, Object> map) throws SQLException {
        String requestNo = (String) map.get("REQUEST_NO");
        BigDecimal balance = (BigDecimal) map.get("BALANCE");
        String tradeAcco = (String) map.get("TRADE_ACCO");
        String stockCode = (String) map.get("STOCK_CODE");
        String bizCode = (String) map.get("BIZ_CODE");
        int state = Integer.valueOf(String.valueOf(map.get("state")));
        TIMESTAMP requestDate = (TIMESTAMP) map.get("REQUEST_DATE");
        //
        StockCtrl stockCtrl = new StockCtrl();
        stockCtrl.setRequestNo(requestNo);
        stockCtrl.setBalance(balance.longValue());
        stockCtrl.setTradeAcco(tradeAcco);
        stockCtrl.setStockCode(stockCode);
        stockCtrl.setBizCode(bizCode);
        stockCtrl.setRequestDate(requestDate.timestampValue());
        stockCtrl.setState(state);

        return stockCtrl;
    }

    private StockLimitation mapToStockLimitation(Map<String, Object> map) {
        String limitName = (String) map.get("LIMIT_NAME");
        BigDecimal limitAmount = (BigDecimal) map.get("LIMIT_AMOUNT");
        BigDecimal limitInvestors = (BigDecimal) map.get("LIMIT_INVESTORS");
        BigDecimal currentAmount = (BigDecimal) map.get("CURRENT_AMOUNT");
        BigDecimal currentInvestors = (BigDecimal) map.get("CURRENT_INVESTORS");

        //
        StockLimitation stockLimitation = new StockLimitation();
        stockLimitation.setLimitName(limitName);
        stockLimitation.setLimitInvestors(limitInvestors.longValue());
        stockLimitation.setLimitAmount(limitAmount.longValue());
        stockLimitation.setCurrentAmount(currentAmount.longValue());
        stockLimitation.setCurrentInvestors(currentInvestors.intValue());

        return stockLimitation;
    }

    private void rollbackConnection(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException e) {
            logger.error("事物回滚报错", e);
        }
    }

    private void closeConnection(Connection connection) {
        if (null == connection) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            logger.error("连接关闭失败", e);
        }
    }
}
