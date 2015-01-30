package com.hundsun.fcloud.tools.stockctrl.service.hashmap;

import com.hundsun.fcloud.tools.stockctrl.model.StockCtrl;
import com.hundsun.fcloud.tools.stockctrl.model.StockQuery;
import com.hundsun.fcloud.tools.stockctrl.service.StockCtrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Gavin Hu on 2015/1/3.
 */
public class ConcurrentHashMapBasedStockService extends AbstractStockService {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentHashMapBasedStockService.class);

    private Map<String, AtomicLong> balanceMap = new ConcurrentHashMap<String, AtomicLong>(){
        @Override
        public AtomicLong get(Object key) {
            AtomicLong value = super.get(key);
            if(value==null) {
                value = new AtomicLong(0);
                put((String) key, value);
            }
            return value;
        }
    };

    @Override
    public void lock(StockCtrl stockCtrl) {
        //
        AtomicLong balance = balanceMap.get(getKeyWithStockCtrl(stockCtrl));
        Long limitBalance = getLimitationConfig().getLimitedAmount(getKeyWithStockCtrl(stockCtrl));
        //
        Long oldBalance = balance.get();
        Long newBalance = oldBalance + stockCtrl.getBalance();
        if(newBalance <= limitBalance) {
            if(balance.compareAndSet(oldBalance, newBalance)) {
                super.putStockCtrl(stockCtrl);
                //
                logger.info("当前库存{}，库存限制{}！", newBalance, limitBalance);
            } else {
                lock(stockCtrl);
            }
        } else {
            throw new StockCtrlException(String.format("锁库存异常,当前库存%d,请求锁定%d，库存限制%d！",
                    oldBalance, stockCtrl.getBalance(), limitBalance));
        }

    }

    @Override
    public void unlock(StockCtrl stockCtrl) {
        //
        AtomicLong balance = balanceMap.get(getKeyWithStockCtrl(stockCtrl));
        Long limitBalance = getLimitationConfig().getLimitedAmount(getKeyWithStockCtrl(stockCtrl));
        //
        Long oldBalance = balance.get();
        Long newBalance = oldBalance - stockCtrl.getBalance();
        //
        StockCtrl cachedStockCtrl = super.getStockCtrl(stockCtrl);
        if(cachedStockCtrl==null) {
            throw new StockCtrlException(String.format("锁定的库存中不存在申请编号为 %s 的库存！", stockCtrl.getRequestNo()));
        }
        //
        if(balance.compareAndSet(oldBalance, newBalance)) {
            super.removeStockCtrl(stockCtrl);
            //
            logger.info("当前库存{}，库存限制{}！", newBalance, limitBalance);
        } else {
            unlock(stockCtrl);
        }
    }

    @Override
    public void increase(StockCtrl stockCtrl) {
        //
        AtomicLong balance = this.balanceMap.get(getKeyWithStockCtrl(stockCtrl));
        Long limitBalance = getLimitationConfig().getLimitedAmount(getKeyWithStockCtrl(stockCtrl));
        //
        Long oldBalance = balance.get();
        Long newBalance = oldBalance - stockCtrl.getBalance();
        //
        if(newBalance<0) {
            throw new StockCtrlException("增加库存余量异常！");
        }
        //
        if(balance.compareAndSet(oldBalance, newBalance)) {
            //
            logger.info("当前库存{}，库存限制{}！", newBalance, limitBalance);
        } else {
            increase(stockCtrl);
        }
    }

    @Override
    public void decrease(StockCtrl stockCtrl) {
        //
        StockCtrl removedStockCtrl = super.removeStockCtrl(stockCtrl);
        //
        if(removedStockCtrl==null) {
            throw new StockCtrlException(String.format("锁定的库存中不存在申请编号为 %s 的库存！", stockCtrl.getRequestNo()));
        }
        //
        AtomicLong balance = this.balanceMap.get(getKeyWithStockCtrl(removedStockCtrl));
        //
        Long currentBalance = balance.get();
        Long limitBalance = getLimitationConfig().getLimitedAmount(getKeyWithStockCtrl(removedStockCtrl));
        //
        logger.info("去除库存 %d，当前库存余量", removedStockCtrl.getBalance(),limitBalance-currentBalance);
    }

    private String getKeyWithStockCtrl(StockCtrl stockCtrl) {
        return String.format("%s.%s", stockCtrl.getStockCode(), stockCtrl.getBizCode());
    }

    private String getKeyWithQueryCtrl(StockQuery stockQuery) {
        return String.format("%s.%s", stockQuery.getStockCode(), stockQuery.getBizCode());
    }

    @Override
    public StockQuery query(StockQuery stockQuery) {
        //
        AtomicLong balance = this.balanceMap.get(getKeyWithQueryCtrl(stockQuery));
        Long limitBalance = getLimitationConfig().getLimitedAmount(getKeyWithQueryCtrl(stockQuery));
        //
        Long currentBalance = balance.get();
        Long remainBalance = limitBalance - currentBalance;
        //
        if(remainBalance<0) {
            remainBalance = 0L;
        }
        //
        stockQuery.setTotalBalance(limitBalance);
        stockQuery.setRemainBalance(remainBalance);
        stockQuery.setBalance(currentBalance);
        //
        return stockQuery;
    }
}
