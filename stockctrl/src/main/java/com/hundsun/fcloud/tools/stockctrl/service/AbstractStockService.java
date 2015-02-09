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

    protected void beforeUnlock(StockCtrl stockCtrl) {};

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

    @Override
    public void increase(StockCtrl stockCtrl) {
        //
        StockLimitation stockLimitation = getStockLimitation(getKeyWithStockCtrl(stockCtrl));
        if(stockLimitation==null) {
            return;
        }
        //
        StockCtrl cachedStockCtrl = stockLimitation.getStockCtrl(stockCtrl);
        if(cachedStockCtrl==null) {
            throw new StockCtrlException(String.format("锁定的库存中不存在申请编号为 %s 的库存！", stockCtrl.getRequestNo()));
        }
        //
        AtomicLong stockAmount = stockLimitation.getStockAmount();
        Map<String, AtomicInteger> stockInvestors = stockLimitation.getStockInvestors();
        //
        Long limitAmount = stockLimitation.getLimitAmount();
        //
        Long oldAmount = stockAmount.get();
        Long newAmount = oldAmount - stockCtrl.getBalance();
        //
        if(newAmount <0) {
            throw new StockCtrlException("增加库存余量异常！");
        }
        //
        if(stockAmount.compareAndSet(oldAmount, newAmount)) {
            //
            StockCtrl removedStockCtrl = stockLimitation.removeStockCtrl(stockCtrl);
            AtomicInteger currentTradeCount = stockInvestors.get(cachedStockCtrl.getTradeAcco());
            if(currentTradeCount.decrementAndGet()<1) {
                stockInvestors.remove(cachedStockCtrl.getTradeAcco());
            }
            afterSuccessIncrease(stockLimitation, removedStockCtrl);
            //
            logger.info("当前库存{}，库存限制{}！", newAmount, limitAmount);
        } else {
            increase(stockCtrl);
        }
    }

    protected void afterSuccessIncrease(StockLimitation stockLimitation, StockCtrl removedStockCtrl){}

    @Override
    public void decrease(StockCtrl stockCtrl) {
        //
        StockLimitation stockLimitation = getStockLimitation(getKeyWithStockCtrl(stockCtrl));
        if(stockLimitation==null) {
            return;
        }
        //
        StockCtrl cachedStockCtrl = stockLimitation.getStockCtrl(stockCtrl);
        if(cachedStockCtrl==null) {
            throw new StockCtrlException(String.format("锁定的库存中不存在申请编号为 %s 的库存！", stockCtrl.getRequestNo()));
        }
        //
        AtomicLong stockAmount = stockLimitation.getStockAmount();
        //
        /*InetAddress.getLocalHost().getHostAddress()*/

        Long currentAmount = stockAmount.get();
        Long limitAmount = stockLimitation.getLimitAmount();

        cachedStockCtrl.setState(StockState.PAID.getValue());

        //
        afterSuccessDecrease(stockLimitation, cachedStockCtrl);
        //
        logger.info("去除库存 {}，当前库存余量 {} ", stockCtrl.getBalance(),limitAmount-currentAmount);
    }

    protected void afterSuccessDecrease(StockLimitation stockLimitation, StockCtrl cachedStockCtrl){

    }

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
