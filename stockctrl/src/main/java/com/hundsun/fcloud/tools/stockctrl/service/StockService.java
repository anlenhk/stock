package com.hundsun.fcloud.tools.stockctrl.service;

import com.hundsun.fcloud.tools.stockctrl.model.StockCtrl;
import com.hundsun.fcloud.tools.stockctrl.model.StockQuery;

/**
 * Created by Gavin Hu on 2015/1/3.
 */
public interface StockService {

    void lock(StockCtrl stockCtrl);

    void unlock(StockCtrl stockCtrl);

    void increase(StockCtrl stockCtrl);

    void decrease(StockCtrl stockCtrl);

    StockQuery query(StockQuery stockQuery);
}
