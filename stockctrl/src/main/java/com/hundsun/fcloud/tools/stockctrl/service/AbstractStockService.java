package com.hundsun.fcloud.tools.stockctrl.service;

import com.hundsun.fcloud.tools.stockctrl.model.StockCtrl;
import com.hundsun.fcloud.tools.stockctrl.model.StockLimitation;
import com.hundsun.fcloud.tools.stockctrl.model.StockQuery;
import com.hundsun.fcloud.tools.stockctrl.model.StockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Created by Gavin Hu on 2015/1/19.
 */
public abstract class AbstractStockService implements StockService {

    protected static final Logger logger = LoggerFactory.getLogger(StockService.class);

    private long timerDelay;  // SECOND

    private long timerPeriod; // SECOND

    private int timeoutPay; // MINUTE

    private int limitCountPer = 1;  //单人购买限制次数

    private Map<String, StockLimitation> stockLimitationMap = new HashMap<String, StockLimitation>();

    public void setTimerDelay(long timerDelay) {
        this.timerDelay = timerDelay * 1000;
    }

    public void setTimerPeriod(long timerPeriod) {
        this.timerPeriod = timerPeriod * 1000;
    }

    public void setTimeoutPay(int timeoutPay) {
        this.timeoutPay = timeoutPay;
    }

    public void setLimitCountPer(int limitCountPer) {
        this.limitCountPer = limitCountPer;
    }

    public void initialize() {
        //
        List<StockLimitation> stockLimitationList = loadStockLimitations();
        for(StockLimitation stockLimitation : stockLimitationList) {
            this.stockLimitationMap.put(stockLimitation.getLimitName(), stockLimitation);
        }
        //
        final Timer cleanTimer = new Timer("Cleaner");
        final CleanTask cleanTask = new CleanTask();
        cleanTimer.schedule(cleanTask, timerDelay, timerPeriod);
        //
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                logger.info("销毁定时器...");
                cleanTask.cancel();
                if(cleanTimer!=null) {
                    cleanTimer.purge();
                    logger.info("定时器被成功销毁");
                }
            }
        });
    }

    public void destroy() {
        //

    }

    protected abstract List<StockLimitation> loadStockLimitations();

    protected abstract void beforeLock(StockCtrl lockedStockCtrl, int limitCountPer);

    @Override
    public void lock(StockCtrl stockCtrl) {
        //
        StockLimitation stockLimitation = getStockLimitation(getKeyWithStockCtrl(stockCtrl));
        if(stockLimitation==null) {
            return;
        }

        beforeLock(stockCtrl, limitCountPer);

        stockCtrl.setRequestDate(new Timestamp(System.currentTimeMillis()));

        afterSuccessLock(stockLimitation, stockCtrl);
    }

    protected void afterSuccessLock(StockLimitation stockLimitation, StockCtrl lockedStockCtrl) {}

    protected void beforeUnlock(StockCtrl stockCtrl) {}

    @Override
    public void unlock(StockCtrl stockCtrl) {
        //
        StockLimitation stockLimitation = getStockLimitation(getKeyWithStockCtrl(stockCtrl));
        if(stockLimitation==null) {
            return;
        }

        beforeUnlock(stockCtrl);

        afterSuccessUnlock(stockLimitation, stockCtrl);

    }

    protected void afterSuccessUnlock(StockLimitation stockLimitation, StockCtrl removedStockCtrl) {}

    protected void beforeIncrease(StockCtrl stockCtrl) {}

    @Override
    public void increase(StockCtrl stockCtrl) {
        //
        StockLimitation stockLimitation = getStockLimitation(getKeyWithStockCtrl(stockCtrl));
        if(stockLimitation==null) {
            return;
        }

        this.beforeIncrease(stockCtrl);

        afterSuccessIncrease(stockLimitation, stockCtrl);

        logger.info("增加库存成功, requestNo: {}, banlance: {}", stockCtrl.getRequestNo(), stockCtrl.getBalance());

    }

    protected void afterSuccessIncrease(StockLimitation stockLimitation, StockCtrl removedStockCtrl){}

    protected void beforeDecrease(StockCtrl stockCtrl) {}

    @Override
    public void decrease(StockCtrl stockCtrl) {
        //
        StockLimitation stockLimitation = getStockLimitation(getKeyWithStockCtrl(stockCtrl));
        if(stockLimitation==null) {
            return;
        }

        beforeDecrease(stockCtrl);

        //TODO: 此处这么写主要是为了拿到所库存时的时间， 后期有待优化
        stockCtrl = this.loadStockCtrlByRequstNo(stockCtrl.getRequestNo());

        stockCtrl.setState(StockState.PAID.getValue());

        //
        afterSuccessDecrease(stockLimitation, stockCtrl);
        //
        logger.info("去除库存 {} 成功，requestNo: {} ", stockCtrl.getBalance(), stockCtrl.getRequestNo());
    }

    protected void afterSuccessDecrease(StockLimitation stockLimitation, StockCtrl cachedStockCtrl){}

    protected abstract StockCtrl loadStockCtrlByRequstNo(String requestNo);

    @Override
    public StockQuery query(StockQuery stockQuery) {
        StockLimitation limitation = this.getStockLimitation(getKeyWithQueryCtrl(stockQuery));
        if (null == limitation) {
            return stockQuery;
        }

        stockQuery.setTotalBalance(limitation.getLimitAmount());
        stockQuery.setRemainBalance(limitation.getLimitAmount() - limitation.getCurrentAmount());
        stockQuery.setBalance(limitation.getCurrentAmount());

        return stockQuery;
    }

    protected abstract StockLimitation loadStockLimitationByLimitName(String limitName);


    private String getKeyWithStockCtrl(StockCtrl stockCtrl) {
        return String.format("%s.%s", stockCtrl.getStockCode(), stockCtrl.getBizCode());
    }

    private String getKeyWithQueryCtrl(StockQuery stockQuery) {
        return String.format("%s.%s", stockQuery.getStockCode(), stockQuery.getBizCode());
    }

    protected abstract List<StockCtrl> loadAllStockCtrls();

    protected abstract boolean isActiveStockStrlCleaner(String host, long timerPeriod);

    private class CleanTask extends TimerTask {
        /**
         *  每 1 分钟查询次数据库，判断下数据库中最后一次更新时间距离当前时间， 若超过 3 分钟， 则自己替换上去.
         */

        @Override
        public void run() {
            try {
                logger.info("执行定时器.....");
                String host = Inet4Address.getLocalHost().getHostAddress();
                if (! isActiveStockStrlCleaner(host, timerPeriod * 3)) {
                    return;
                }
            } catch (UnknownHostException e) {
                logger.error("获取本机IP异常", e);
                throw new RuntimeException(e);
            }
            // TODO 判断当前清理机器是否为本机， 若是，则执行


            List<StockCtrl> stockCtrls = loadAllStockCtrls();

            Date currentDate = new Date();
            List<StockCtrl> unlockList = new ArrayList<StockCtrl>();
            //
            for(StockCtrl stockCtrl : stockCtrls) {
                currentDate = new Date();
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(stockCtrl.getRequestDate());
                calendar.set(Calendar.MINUTE, calendar.get(Calendar.MINUTE) + timeoutPay);
                //
                if (stockCtrl.getState() == StockState.PAID.getValue()) {
                    continue;
                }

                if(calendar.getTime().before(currentDate)) {
                    unlockList.add(stockCtrl);
                }
            }

            for (StockCtrl stockCtrl : unlockList) {
                logger.info("支付时间超时, 解库, 申请编号: " + stockCtrl.getRequestNo());
                unlock(stockCtrl);
            }
        }
    }

    protected StockLimitation getStockLimitation(String key) {
        return stockLimitationMap.get(key);
    }


}
