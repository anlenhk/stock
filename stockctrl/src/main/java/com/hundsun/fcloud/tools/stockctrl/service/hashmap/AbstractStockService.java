package com.hundsun.fcloud.tools.stockctrl.service.hashmap;

import com.hundsun.fcloud.tools.stockctrl.model.StockCtrl;
import com.hundsun.fcloud.tools.stockctrl.service.StockService;
import com.hundsun.fcloud.tools.stockctrl.service.hashmap.config.LimitationConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Gavin Hu on 2015/1/4.
 */
public abstract class AbstractStockService implements StockService {

    private long timerDelay;  // SECOND

    private long timerPeriod; // SECOND

    private int timeoutPay; // MINUTE

    private LimitationConfig limitationConfig;

    private Map<String, StockCtrl> stockCtrlMap = new ConcurrentHashMap<String, StockCtrl>();

    private Map<String, Date> stockDateMap = new ConcurrentHashMap<String, Date>();

    public void setTimerDelay(long timerDelay) {
        this.timerDelay = timerDelay * 1000;
    }

    public void setTimerPeriod(long timerPeriod) {
        this.timerPeriod = timerPeriod * 1000;
    }

    public void setTimeoutPay(int timeoutPay) {
        this.timeoutPay = timeoutPay;
    }

    public LimitationConfig getLimitationConfig() {
        return limitationConfig;
    }

    public void setLimitationConfig(LimitationConfig limitationConfig) {
        this.limitationConfig = limitationConfig;
    }

    public void init() {
        //
        final Timer timer = new Timer("Stock Cleaner");
        timer.schedule(new StockCleaner(), timerDelay, timerPeriod);
        //
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                timer.purge();
            }
        });
    }


    public StockCtrl getStockCtrl(StockCtrl stockCtrl) {
        return this.stockCtrlMap.get(stockCtrl.getRequestNo());
    }

    public void putStockCtrl(StockCtrl stockCtrl) {
        this.stockCtrlMap.put(stockCtrl.getRequestNo(), stockCtrl);
        this.stockDateMap.put(stockCtrl.getRequestNo(), new Date());
    }

    public StockCtrl removeStockCtrl(StockCtrl stockCtrl) {
        this.stockDateMap.remove(stockCtrl.getRequestNo());
        return this.stockCtrlMap.remove(stockCtrl.getRequestNo());
    }

    private class StockCleaner extends TimerTask {

        @Override
        public void run() {
            //
            for(Iterator<Map.Entry<String, StockCtrl>> iter=stockCtrlMap.entrySet().iterator(); iter.hasNext();) {
                //
                Map.Entry<String, StockCtrl> stockCtrlEntry = iter.next();
                Date date = stockDateMap.get(stockCtrlEntry.getKey());
                //
                Date currentDate = new Date();
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                calendar.set(Calendar.MINUTE, timeoutPay);
                //
                if(calendar.before(currentDate)) {
                    //
                    iter.remove();
                    //
                    unlock(stockCtrlEntry.getValue());
                }
            }
        }
    }

}
