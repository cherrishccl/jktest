package com.oxchains.themis.notice.service;

import com.oxchains.themis.notice.auth.ArithmeticUtils;
import com.oxchains.themis.notice.auth.RestResp;
import com.oxchains.themis.notice.dao.*;
import com.oxchains.themis.notice.domain.*;
import com.oxchains.themis.notice.domain.Currency;
import com.oxchains.themis.notice.rest.dto.PageDTO;
import com.oxchains.themis.notice.rest.dto.StatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * @author luoxuri
 * @create 2017-10-25 10:21
 **/
@Service
@Transactional
public class NoticeService {

    private static final Logger LOG = LoggerFactory.getLogger(NoticeService.class);

    @Resource private NoticeDao noticeDao;
    @Resource private BTCTickerDao btcTickerDao;
    @Resource private BTCResultDao btcResultDao;
    @Resource private BTCMarketDao btcMarketDao;
    @Resource private CNYDetailDao cnyDetailDao;
    @Resource private CountryDao countryDao;
    @Resource private CurrencyDao currencyDao;
    @Resource private PaymentDao paymentDao;
    @Resource private SearchTypeDao searchTypeDao;
    @Resource private UserTxDetailDao userTxDetailDao;

    /**
     * 发布公告需要传递的参数：
     * userId           关联user表的id
     * noticeType       公告类型(购买/出售)
     * location         地区
     * currency         货币类型
     * premium          溢价
     * price            价格
     * minPrice         最低价
     * minTxLimit       最小交易额度
     * maxTxLimit       最大交易额度
     * payType          支付方式
     * noticeContent    公告内容
     * @param notice
     * @return
     */
    public RestResp broadcastNotice(Notice notice){
        try {
            // 必填项判断
            if (null == notice.getNoticeType() && null == notice.getLocation() && null == notice.getCurrency()
                    && null == notice.getPrice() && null == notice.getMinTxLimit() && null == notice.getMaxTxLimit()
                    && null == notice.getPayType() && null == notice.getNoticeContent() && null == notice.getPremium()) {
                return RestResp.fail("必填项不能为空");
            }

            // 选填项(最低价)判断-11.1中国又禁止一部分btc相关平台，此价格获取失败
            List<BTCTicker> btcTickerList = btcTickerDao.findBySymbol("btccny");
            if (btcTickerList.size() != 0){
                for (BTCTicker btcTicker : btcTickerList) {
                    Double low = btcTicker.getLow().doubleValue();
                    Double minPrice = notice.getMinPrice().doubleValue();
                    if (null == notice.getMinPrice()){
                        notice.setMinPrice(btcTicker.getLow());
                    }else { // 市场价低于定义的最低价，那么价格就是自己定义的最低价
                        if (ArithmeticUtils.minus(low, minPrice) < 0) {
                            notice.setPrice(notice.getMinPrice());
                        }
                    }
                }
            }else {
                // 选填项（最低价判断）
                List<CNYDetail> cnyDetailList = cnyDetailDao.findBySymbol("¥");
                if (cnyDetailList.size() != 0){
                    for (CNYDetail c : cnyDetailList){
                        if (null == notice.getMinPrice()){
                            notice.setMinPrice(new BigDecimal(c.getLast()));
                        }else {

                            Double low = Double.valueOf(c.getLast());
                            Double minPrice = notice.getMinPrice().doubleValue();
                            if (ArithmeticUtils.minus(low, minPrice) < 0){
                                notice.setPrice(notice.getMinPrice());
                            }
                        }
                    }
                }
            }

            // 溢价判断
            if (notice.getPremium() < 0 && notice.getPremium() > 10) {
                return RestResp.fail("请按规定输入溢价（0~10）");
            }

            // 两种不能发布公告得判断
            List<Notice> noticeListUnDone = noticeDao.findByUserIdAndNoticeTypeAndTxStatus(notice.getUserId(), notice.getNoticeType(), 0);
            if (noticeListUnDone.size() != 0){
                return RestResp.fail("已经有一条此类型公告");
            }
            List<Notice> noticeListDoing = noticeDao.findByUserIdAndNoticeTypeAndTxStatus(notice.getUserId(), notice.getNoticeType(), 1);
            if (noticeListDoing.size() != 0){
                return RestResp.fail("已经有一条此类型公告且正在交易");
            }

            String createTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            notice.setCreateTime(createTime);
            Notice n = noticeDao.save(notice);
            return RestResp.success("操作成功", n);
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("发布公告异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    public RestResp queryRandomNotice(){
        try {
            List<Notice> partList = new ArrayList<>();
            List<Notice> buyNoticeList = noticeDao.findByNoticeTypeAndTxStatus(1L, 0);
            List<Notice> sellNoticeList = noticeDao.findByNoticeTypeAndTxStatus(2L, 0);

            if (buyNoticeList.size() == 0 && sellNoticeList.size() == 0){
                return RestResp.success("没有数据", new ArrayList<>());
            }
            if (buyNoticeList.size() > 2 && sellNoticeList.size() > 2){
                int buySize = getRandom(buyNoticeList);
                int sellSize = getRandom(sellNoticeList);
                List<Notice> subBuyList = getSubList(buyNoticeList, buySize);
                List<Notice> subSellList = getSubList(sellNoticeList, sellSize);
                for (int i = 0; i < subBuyList.size(); i++){ setUserTxDetail(subBuyList, i);}
                for (int i = 0; i < subSellList.size(); i++){setUserTxDetail(subSellList, i);}
                partList.addAll(subBuyList);
                partList.addAll(subSellList);
            }else if (buyNoticeList.size() <= 2 && sellNoticeList.size() > 2){
                int sellSize = getRandom(sellNoticeList);
                List<Notice> subSellList = getSubList(sellNoticeList, sellSize);
                for (int i = 0; i < buyNoticeList.size(); i++){setUserTxDetail(buyNoticeList, i);}
                for (int i = 0; i < subSellList.size(); i++){setUserTxDetail(subSellList, i);}
                partList.addAll(buyNoticeList);
                partList.addAll(subSellList);
            }else if (buyNoticeList.size() > 2 && sellNoticeList.size() <= 2){
                int buySize = getRandom(buyNoticeList);
                List<Notice> subBuyList = getSubList(buyNoticeList, buySize);
                for (int i = 0; i < subBuyList.size(); i++){setUserTxDetail(subBuyList, i);}
                for (int i = 0; i < sellNoticeList.size(); i++){setUserTxDetail(sellNoticeList, i);}
                partList.addAll(subBuyList);
                partList.addAll(sellNoticeList);
            }else {
                for (int i = 0; i < buyNoticeList.size(); i++){setUserTxDetail(buyNoticeList, i);}
                for (int i = 0; i < sellNoticeList.size(); i++){setUserTxDetail(sellNoticeList, i);}
                partList.addAll(buyNoticeList);
                partList.addAll(sellNoticeList);
            }
            return RestResp.success("操作成功", partList);
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("获取4条随机公告异常", e.getMessage());

        }
        return RestResp.fail("操作失败");
    }

    public RestResp queryAllNotice(){
        try {
            Iterable<Notice> it = noticeDao.findAll();
            if(it.iterator().hasNext()){
                return RestResp.success("操作成功", it);
            }else {
                return RestResp.fail("操作失败");
            }
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("查询所有公告异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    public RestResp queryMeAllNotice(Long userId, Long noticeType, Integer txStatus){
        try {
            List<Notice> resultList = new ArrayList<>();
            if (txStatus == 2){
                List<Notice> noticeList = noticeDao.findByUserIdAndNoticeTypeAndTxStatus(userId, noticeType, 2);
                resultList.addAll(noticeList);
            } else {
                List<Notice> unDoneNoticeList = noticeDao.findByUserIdAndNoticeTypeAndTxStatus(userId, noticeType, 0);
                List<Notice> doingNoticeList = noticeDao.findByUserIdAndNoticeTypeAndTxStatus(userId, noticeType, 1);
                resultList.addAll(unDoneNoticeList);
                resultList.addAll(doingNoticeList);
            }
            return RestResp.success("操作成功", resultList);
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("查询我的公告异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    @Deprecated
    public RestResp queryBTCPrice(){
        try {
            List<BTCTicker> btcTickerList = btcTickerDao.findBySymbol("btccny");
            if (!btcTickerList.isEmpty()){
                return RestResp.success("操作成功", btcTickerList);
            }else {
                return RestResp.fail("操作失败");
            }
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("查询BTC价格异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    @Deprecated
    public RestResp queryBTCMarket(){
        try {
            List<BTCResult> btcResultList = btcResultDao.findByIsSuc("true");
            List<BTCMarket> btcMarketList = btcMarketDao.findBySymbol("huobibtccny");
            List<BTCTicker> btcTickerList = btcTickerDao.findBySymbol("btccny");
            BTCResult btcResult = null;
            BTCMarket btcMarket = null;
            BTCTicker btcTicker = null;
            for (int i = 0; i < btcResultList.size(); i++){
                btcResult = btcResultList.get(i);
            }
            for (int i = 0; i < btcMarketList.size(); i++){
                btcMarket = btcMarketList.get(i);
            }
            for (int i = 0; i < btcTickerList.size(); i++){
                btcTicker = btcTickerList.get(i);
            }
            btcMarket.setTicker(btcTicker);
            btcResult.setDatas(btcMarket);
            return RestResp.success("操作成功", btcResultList);
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("查询BTC深度行情异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    public RestResp queryBlockChainInfo(){
        try {
            List<CNYDetail> cnyDetailList = cnyDetailDao.findBySymbol("¥");
            if (cnyDetailList.size() != 0){
                return RestResp.success("操作成功", cnyDetailList);
            }else {
                return RestResp.fail("操作失败");
            }
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("获取BTC价格异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    public RestResp searchBuyPage(Notice notice){
        try {
            Long location = notice.getLocation();
            Long currency = notice.getCurrency();
            Long payType = notice.getPayType();
            Long noticeType = 2L;
            Integer pageNum = notice.getPageNum();

            Pageable pageable = buildPageRequest(pageNum, 8, "auto");

            // 对所在地，货币类型，支付方式判断，可为null
            Page<Notice> page = null;
            if (null != location && null != currency && null != payType) {
                page = noticeDao.findByLocationAndCurrencyAndPayTypeAndNoticeTypeAndTxStatus(location, currency, payType, noticeType, 0, pageable);
            }else if (null != location && null != currency && null == payType) {
                page = noticeDao.findByLocationAndCurrencyAndNoticeTypeAndTxStatus(location, currency, noticeType, 0, pageable);
            } else if (null != location && null == currency && null != payType) {
                page = noticeDao.findByLocationAndPayTypeAndNoticeTypeAndTxStatus(location, payType, noticeType, 0, pageable);
            } else if (null == location && null != currency && null != payType) {
                page = noticeDao.findByCurrencyAndPayTypeAndNoticeTypeAndTxStatus(currency, payType, noticeType, 0, pageable);
            } else if (null != location && null == currency && null == payType) {
                page = noticeDao.findByLocationAndNoticeTypeAndTxStatus(location, noticeType, 0, pageable);
            } else if (null == location && null == currency && null != payType) {
                page = noticeDao.findByPayTypeAndNoticeTypeAndTxStatus(payType, noticeType, 0, pageable);
            } else if (null == location && null != currency && null != payType) {
                page = noticeDao.findByCurrencyAndNoticeTypeAndTxStatus(currency, noticeType, 0, pageable);
            } else if (null == location && null == currency && null == payType) {
                page = noticeDao.findByNoticeTypeAndTxStatus(noticeType, 0, pageable);
            }else {
                return RestResp.fail("操作失败");
            }

            List<Notice> resultList = new ArrayList<>();
            Iterator<Notice> it = page.iterator();
            while (it.hasNext()){
                resultList.add(it.next());
            }

            // 将好评度等值添加到list中返回
            for (int i = 0; i < resultList.size(); i++){
                Long userId = resultList.get(i).getUserId();
                UserTxDetail utdInfo = userTxDetailDao.findOne(userId);
                if (utdInfo == null){
                    resultList.get(i).setTxNum(0);
                    resultList.get(i).setTrustNum(0);
                    resultList.get(i).setGoodPercent(0);
                }else {
                    resultList.get(i).setTxNum(utdInfo.getTxNum());
                    resultList.get(i).setTrustNum(utdInfo.getBelieveNum());
                    double descTotal = ArithmeticUtils.plus(utdInfo.getGoodDesc(), utdInfo.getBadDesc());
                    double goodP = ArithmeticUtils.divide(utdInfo.getGoodDesc(), descTotal,2);
                    resultList.get(i).setGoodPercent((int)(goodP*100));
                }

            }

            // 将page相关参数设置到DTO中返回
            PageDTO<Notice> pageDTO = new PageDTO<>();
            pageDTO.setCurrentPage(pageNum);
            pageDTO.setPageSize(8);
            pageDTO.setRowCount(page.getTotalElements());
            pageDTO.setTotalPage(page.getTotalPages());
            pageDTO.setPageList(resultList);
            return RestResp.success("操作成功", pageDTO);
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("搜索购买公告异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    public RestResp searchSellPage(Notice notice){
        try {
            Long location = notice.getLocation();
            Long currency = notice.getCurrency();
            Long payType = notice.getPayType();
            Long noticeType = 1L;
            Integer pageNum = notice.getPageNum();

            Pageable pageable = buildPageRequest(pageNum, 8, "createTime");

            Page<Notice> page = null;
            if (null != location && null != currency && null != payType) {
                page = noticeDao.findByLocationAndCurrencyAndPayTypeAndNoticeTypeAndTxStatus(location, currency, payType, noticeType, 0, pageable);
            }else if (null != location && null != currency && null == payType) {
                page = noticeDao.findByLocationAndCurrencyAndNoticeTypeAndTxStatus(location, currency, noticeType, 0, pageable);
            } else if (null != location && null == currency && null != payType) {
                page = noticeDao.findByLocationAndPayTypeAndNoticeTypeAndTxStatus(location, payType, noticeType, 0, pageable);
            } else if (null == location && null != currency && null != payType) {
                page = noticeDao.findByCurrencyAndPayTypeAndNoticeTypeAndTxStatus(currency, payType, noticeType, 0, pageable);
            } else if (null != location && null == currency && null == payType) {
                page = noticeDao.findByLocationAndNoticeTypeAndTxStatus(location, noticeType, 0, pageable);
            } else if (null == location && null == currency && null != payType) {
                page = noticeDao.findByPayTypeAndNoticeTypeAndTxStatus(payType, noticeType, 0, pageable);
            } else if (null == location && null != currency && null != payType) {
                page = noticeDao.findByCurrencyAndNoticeTypeAndTxStatus(currency, noticeType, 0, pageable);
            } else if (null == location && null == currency && null == payType) {
                page = noticeDao.findByNoticeTypeAndTxStatus(noticeType, 0, pageable);
            }else {
                return RestResp.fail("操作失败");
            }

            List<Notice> resultList = new ArrayList<>();
            Iterator<Notice> it = page.iterator();
            while (it.hasNext()){
                resultList.add(it.next());
            }

            // 将好评度等值添加到list中返回
            for (int i = 0; i < resultList.size(); i++){
                Long userId = resultList.get(i).getUserId();
                UserTxDetail utdInfo = userTxDetailDao.findOne(userId);
                if (null == utdInfo){
                    resultList.get(i).setTxNum(0);
                    resultList.get(i).setTrustNum(0);
                    resultList.get(i).setGoodPercent(0);
                }else {
                    resultList.get(i).setTxNum(utdInfo.getTxNum());
                    resultList.get(i).setTrustNum(utdInfo.getBelieveNum());
                    double descTotal = ArithmeticUtils.plus(utdInfo.getGoodDesc(), utdInfo.getBadDesc());
                    double goodP = ArithmeticUtils.divide(utdInfo.getGoodDesc(), descTotal,2);
                    resultList.get(i).setGoodPercent((int)(goodP*100));
                }

            }

            PageDTO<Notice> pageDTO = new PageDTO<>();
            pageDTO.setCurrentPage(pageNum);
            pageDTO.setPageSize(8);
            pageDTO.setRowCount(page.getTotalElements());
            pageDTO.setTotalPage(page.getTotalPages());
            pageDTO.setPageList(resultList);
            return RestResp.success("操作成功", pageDTO);
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("搜索出售公告异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    public RestResp stopNotice(Long id){
        try {
            Notice noticeInfo = noticeDao.findOne(id);
            if (null == noticeInfo) {
                return RestResp.fail("操作失败");
            }
            if (noticeInfo.getTxStatus() == 2) {
                return RestResp.fail("公告已下架");
            }
            if (noticeInfo.getTxStatus() == 1) {
                return RestResp.fail("交易中公告，禁止下架");
            }
            List<Notice> noticeList = noticeDao.findByUserIdAndNoticeTypeAndTxStatus(noticeInfo.getUserId(), noticeInfo.getNoticeType(), noticeInfo.getTxStatus());
            if (noticeList.size() == 0){
                return RestResp.fail("操作失败");
            }
            for (Notice n : noticeList) {
                n.setTxStatus(2);
                noticeDao.save(n);
            }
            return RestResp.success("操作成功");
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("下架公告异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    public RestResp queryStatusKV(){
        try {
            Iterable<Country> location = countryDao.findAll();
            Iterable<Currency> currency = currencyDao.findAll();
            Iterable<Payment> payment = paymentDao.findAll();
            Iterable<SearchType> searchType = searchTypeDao.findAll();
            Iterable<BTCTicker> btcTiker = btcTickerDao.findBTCTickerBySymbol("btccny");
            List<CNYDetail> cnyDetailList = cnyDetailDao.findBySymbol("¥");

            if (location.iterator().hasNext() && currency.iterator().hasNext() && payment.iterator().hasNext() && searchType.iterator().hasNext()){
                StatusDTO statusDTO = new StatusDTO<>();
                statusDTO.setLocationList(location);
                statusDTO.setCurrencyList(currency);
                statusDTO.setPaymentList(payment);
                statusDTO.setSearchTypeList(searchType);
                statusDTO.setBTCMarketList(btcTiker);
                statusDTO.setCnyDetailList(cnyDetailList);
                return RestResp.success("操作成功", statusDTO);
            } else {
                return RestResp.fail("操作失败");
            }
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("查询状态异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    // =================================================================

    /**
     * 根据list大小-2获取一个随机数
     * @param list
     * @return
     */
    private int getRandom(List list){
        return new Random().nextInt(list.size() - 2);
    }

    /**
     * 对list截取，获取size为2的新list
     * @param list
     * @param size
     * @return
     */
    private List getSubList(List list, int size){
        return list.subList(size, size + 2);
    }

    /**
     * 设置用户交易详情数据
     * @param subList
     * @param i
     */
    private void setUserTxDetail(List<Notice> subList, int i) {
        Long userId = subList.get(i).getUserId();
        UserTxDetail userTxDetail = userTxDetailDao.findOne(userId);
        if (null == userTxDetail){
            subList.get(i).setTxNum(0);
            subList.get(i).setTrustNum(0);
            subList.get(i).setTrustPercent(0);
        }else {
            subList.get(i).setTxNum(userTxDetail.getTxNum());
            subList.get(i).setTrustNum(userTxDetail.getBelieveNum());
            double trustP = ArithmeticUtils.divide(userTxDetail.getBelieveNum(), userTxDetail.getTxNum(), 2);
            subList.get(i).setTrustPercent((int) ArithmeticUtils.multiply(trustP, (double) 100, 0));
        }

    }

    /**
     * 创建分页请求
     * @param pageNum   当前第几页
     * @param pageSize  每页显示的数量
     * @param sortType  排序
     * @return
     */
    private PageRequest buildPageRequest(Integer pageNum, Integer pageSize, String sortType){
        Sort sort = null;
        if("auto".equals(sortType)){
            sort = new Sort(Sort.Direction.DESC, "id");
        } else if ("createTime".equals(sortType)){
            sort = new Sort(Sort.Direction.ASC, "createTime");
        }
        return new PageRequest(pageNum - 1, pageSize, sort);
    }

}
