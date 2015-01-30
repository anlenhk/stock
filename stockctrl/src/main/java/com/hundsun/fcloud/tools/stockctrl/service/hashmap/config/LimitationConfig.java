package com.hundsun.fcloud.tools.stockctrl.service.hashmap.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Gavin Hu on 2015/1/7.
 */
public class LimitationConfig {

    private Map<String, Long> amountMap = new HashMap<String, Long>();

    private Map<String, Integer> investorMap = new HashMap<String, Integer>();

    public LimitationConfig() {
    }

    /**
     * 针对每只基金设置限制项
     * @param amountItems
     */
    public void setAmountItems(String amountItems) {
        if(amountItems!=null && amountItems.contains(":")) {
            String[] items = amountItems.split(";");
            for(String item : items) {
                String[] couple = item.split(":");
                String key = couple[0];
                Long value = Long.parseLong(couple[1]);
                //
                this.amountMap.put(key, value);
            }
        }
    }

    public void setInvestorItems(String investorItems) {
        if(investorItems!=null && investorItems.contains(":")) {
            String[] items = investorItems.split(";");
            for(String item : items) {
                String[] couple = item.split(":");
                String key = couple[0];
                Integer value = Integer.parseInt(couple[1]);
                //
                this.investorMap.put(key, value);
            }
        }
    }

    /**
     * 根据 Key 获取限制值
     * @param key fundCode + "." + bizCode
     * @return
     */
    public Long getLimitedAmount(String key) {
        Long limitValue = this.amountMap.get(key);
        if(limitValue==null) {
            limitValue = Long.MAX_VALUE;
        }
        return limitValue;
    }

    public Integer getLimitedInvestor(String key) {
        Integer limitValue = this.investorMap.get(key);
        if(limitValue==null) {
            limitValue=Integer.MAX_VALUE;
        }
        return limitValue;
    }

}
