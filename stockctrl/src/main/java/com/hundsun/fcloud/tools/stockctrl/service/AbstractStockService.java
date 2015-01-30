package com.hundsun.fcloud.tools.stockctrl.service;

import com.hundsun.fcloud.tools.stockctrl.model.StockCtrl;
import com.hundsun.fcloud.tools.stockctrl.model.StockLimitation;
import com.hundsun.fcloud.tools.stockctrl.model.StockQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    protected abstract void beforeLock(StockCtrl lockedStockCtrl);

    @Override
    public void lock(StockCtrl stockCtrl) {
        beforeLock(stockCtrl);
        //
        StockLimitation stockLimitation = getStockLimitation(getKeyWithStockCtrl(stockCtrl));
        if(stockLimitation==null) {
            return;
        }
        //
        AtomicLong stockAmount = stockLimitation.getStockAmount();
        Map<String, AtomicInteger> stockInvestors = stockLimitation.getStockInvestors();
        //
        Long limitAmount = stockLimitation.getLimitAmount();
        Long limitInvestors = stockLimitation.getLimitInvestors();
        //
        if (!stockInvestors.containsKey(stockCtrl.getTradeAcco())
                && stockInvestors.size() + 1 > limitInvestors) {
            throw new StockCtrlException("");
        }
        //
        Long oldAmount = stockAmount.get();
        Long newAmount = oldAmount + stockCtrl.getBalance();
        if(newAmount <= limitAmount) {
            if(stockAmount.compareAndSet(oldAmount, newAmount)) {
                //
                stockCtrl.setRequestDate(new Timestamp(System.currentTimeMillis()));
                stockLimitation.putStockCtrl(stockCtrl);
                AtomicInteger currentTradeCount = stockInvestors.get(stockCtrl.getTradeAcco());
                currentTradeCount.incrementAndGet();
                //
                afterSuccessLock(stockLimitation, stockCtrl);
                //
                logger.info("当前库存{}，库存限制{}！", newAmount, limitAmount);
            } else {
                this.lock(stockCtrl);
            }
        } else {
            throw new StockCtrlException(String.format("锁库存异常,当前库存%d,请求锁定%d，库存限制%d！",
                    oldAmount, stockCtrl.getBalance(), limitAmount));
        }
    }

    protected void afterSuccessLock(StockLimitation stockLimitation, StockCtrl lockedStockCtrl) {}

    @Override
    public void unlock(StockCtrl stockCtrl) {
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
        Long oldAmount = stockAmount.get();
        Long newAmount = oldAmount - stockCtrl.getBalance();
        //
        if(stockAmount.compareAndSet(oldAmount, newAmount)) {
            StockCtrl removedStockCtrl = stockLimitation.removeStockCtrl(cachedStockCtrl);
            AtomicInteger currentTradeCount = stockInvestors.get(cachedStockCtrl.getTradeAcco());
            if(currentTradeCount.decrementAndGet()<1) {
                stockInvestors.remove(cachedStockCtrl.getTradeAcco());
            }
            //
            afterSuccessUnlock(stockLimitation, removedStockCtrl);
            //
            logger.info("当前库存{}，库存限制{}！", newAmount, stockLimitation.getLimitAmount());
        } else {
            unlock(stockCtrl);
        }
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
        Long currentAmount = stockAmount.get();
        Long limitAmount = stockLimitation.getLimitAmount();
        //
        afterSuccessDecrease(stockLimitation, cachedStockCtrl);
        //
        logger.info("去除库存 %d，当前库存余量", stockCtrl.getBalance(),limitAmount-currentAmount);
    }

    protected void afterSuccessDecrease(StockLimitation stockLimitation, StockCtrl cachedStockCtrl){}

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
                Map<String, StockCtrl> stockCtrlMap = stockLimitation.getStockCtrlMap();
                for(Iterator<Map.Entry<String, StockCtrl>> iter=stockCtrlMap.entrySet().iterator(); iter.hasNext();) {
                    //
                    Map.Entry<String, StockCtrl> entry = iter.next();
                    StockCtrl stockCtrl = entry.getValue();
                    //
                    Date currentDate = new Date();
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(stockCtrl.getRequestDate());
                    calendar.set(Calendar.MINUTE, timeoutPay);
                    //
                    if(calendar.before(currentDate)) {
                        //
                        iter.remove();
                        //
                        unlock(entry.getValue());
                    }
                }
            }
        }
    }

    protected StockLimitation getStockLimitation(String key) {
        return stockLimitationMap.get(key);
    }

}
