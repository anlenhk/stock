package com.hundsun.fcloud.tools.stockctrl.service;

import com.hundsun.fcloud.tools.stockctrl.model.StockCtrl;
import com.hundsun.fcloud.tools.stockctrl.model.StockLimitation;
import com.hundsun.fcloud.tools.stockctrl.model.StockQuery;
import com.hundsun.fcloud.tools.stockctrl.model.StockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Gavin Hu on 2015/1/19.
 */
public abstract class AbstractStockService implements StockService {

    protected static final Logger logger = LoggerFactory.getLogger(StockService.class);

    private long timerDelay;  // SECOND

    private long timerPeriod; // SECOND

    private int timeoutPay; // MINUTE

    private int limitCountPer = 1;  //单人购买限制次数

    private Timer cleanTimer;

    private Map<String, StockLimitation> stockLimitationMap = new HashMap<String, StockLimitation>();

    public void setTimerDelay(long timerDelay) {
        this.timerDelay = timerDelay;
    }

    public void setTimerPeriod(long timerPeriod) {
        this.timerPeriod = timerPeriod;
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
        this.cleanTimer = new Timer("Cleaner");
        this.cleanTimer.schedule(new Cleaner(), timerDelay, timerPeriod);
    }

    public void destroy() {
        //
        if(this.cleanTimer!=null) {
            this.cleanTimer.purge();
        }
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
        //
        StockLimitation stockLimitation = getStockLimitation(getKeyWithQueryCtrl(stockQuery));
        if(stockLimitation==null) {
            return stockQuery;
        }
        //
        AtomicLong stockAmount = stockLimitation.getStockAmount();
        Long limitAmount = stockLimitation.getLimitAmount();
        //
        Long currentAmount = stockAmount.get();
        Long remainAmount = limitAmount - currentAmount;
        //
        if(remainAmount<0) {
            remainAmount = 0L;
        }
        //
        stockQuery.setTotalBalance(limitAmount);
        stockQuery.setRemainBalance(remainAmount);
        stockQuery.setBalance(currentAmount);
        //
        return stockQuery;
    }


    private String getKeyWithStockCtrl(StockCtrl stockCtrl) {
        return String.format("%s.%s", stockCtrl.getStockCode(), stockCtrl.getBizCode());
    }

    private String getKeyWithQueryCtrl(StockQuery stockQuery) {
        return String.format("%s.%s", stockQuery.getStockCode(), stockQuery.getBizCode());
    }

    private class Cleaner extends TimerTask {

        @Override
        public void run() {

            Date currentDate = new Date();
            List<StockCtrl> unlockList = null;

            //
            for(StockLimitation stockLimitation : stockLimitationMap.values()) {
                //
                Map<String, AtomicInteger> stockInvestors = stockLimitation.getStockInvestors();
                for(Iterator<Map.Entry<String, AtomicInteger>> iter=stockInvestors.entrySet().iterator(); iter.hasNext();) {
                    //
                    Map.Entry<String, AtomicInteger> entry = iter.next();
                    if(entry.getValue().intValue()<1) {
                        iter.remove();
                    }
                }
                //
                unlockList = new ArrayList<StockCtrl>();
                Map<String, StockCtrl> stockCtrlMap = stockLimitation.getStockCtrlMap();
                for(Iterator<Map.Entry<String, StockCtrl>> iter=stockCtrlMap.entrySet().iterator(); iter.hasNext();) {
                    //
                    Map.Entry<String, StockCtrl> entry = iter.next();
                    StockCtrl stockCtrl = entry.getValue();
                    //
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
    }

    protected StockLimitation getStockLimitation(String key) {
        return stockLimitationMap.get(key);
    }


}
