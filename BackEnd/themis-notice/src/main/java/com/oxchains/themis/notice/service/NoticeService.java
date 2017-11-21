package com.oxchains.themis.notice.service;

import com.oxchains.themis.common.constant.notice.NoticeConstants;
import com.oxchains.themis.common.constant.notice.NoticeTxStatus;
import com.oxchains.themis.common.model.RestResp;
import com.oxchains.themis.common.util.ArithmeticUtils;
import com.oxchains.themis.notice.common.NoticeConst;
import com.oxchains.themis.notice.dao.*;
import com.oxchains.themis.notice.domain.*;
import com.oxchains.themis.notice.domain.Currency;
import com.oxchains.themis.notice.rest.dto.PageDTO;
import com.oxchains.themis.notice.rest.dto.StatusDTO;
import com.oxchains.themis.repo.dao.UserDao;
import com.oxchains.themis.repo.dao.UserTxDetailDao;
import com.oxchains.themis.repo.entity.*;
import com.oxchains.themis.repo.entity.UserTxDetail;
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
    @Resource private UserDao userDao;

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
                CNYDetail cnyDetail = cnyDetailDao.findBySymbol("¥");
                if (cnyDetail != null){
                    if (null == notice.getMinPrice()){
                        notice.setMinPrice(new BigDecimal(cnyDetail.getLast()));
                    }else {
                        Double low = Double.valueOf(cnyDetail.getLast());
                        Double minPrice = notice.getMinPrice().doubleValue();
                        if (ArithmeticUtils.minus(low, minPrice) < 0){
                            notice.setPrice(notice.getMinPrice());
                        }
                    }
                }else {
                    return RestResp.fail("比特币价格获取失败，请手动查询实时价格慎重");
                }
            }

            // 不能发布公告得判断
            List<Notice> noticeListUnDone = noticeDao.findByUserIdAndNoticeTypeAndTxStatus(notice.getUserId(), notice.getNoticeType(), NoticeTxStatus.UNDONE_TX);
            if (noticeListUnDone.size() != 0){
                return RestResp.fail("已经有一条此类型公告");
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
            List<Notice> buyNoticeList = noticeDao.findByNoticeTypeAndTxStatus(NoticeConst.NoticeType.BUY.getStatus(), NoticeTxStatus.UNDONE_TX);
            List<Notice> sellNoticeList = noticeDao.findByNoticeTypeAndTxStatus(NoticeConst.NoticeType.SELL.getStatus(), NoticeTxStatus.UNDONE_TX);

            if (buyNoticeList.size() == 0 && sellNoticeList.size() == 0){
                return RestResp.success("没有数据", new ArrayList<>());
            }
            if (buyNoticeList.size() > NoticeConstants.TWO && sellNoticeList.size() > NoticeConstants.TWO){
                int buySize = getRandom(buyNoticeList);
                int sellSize = getRandom(sellNoticeList);
                List<Notice> subBuyList = getSubList(buyNoticeList, buySize);
                List<Notice> subSellList = getSubList(sellNoticeList, sellSize);
                for (int i = 0; i < subBuyList.size(); i++){ setUserTxDetail(subBuyList, i);}
                for (int i = 0; i < subSellList.size(); i++){setUserTxDetail(subSellList, i);}
                partList.addAll(subBuyList);
                partList.addAll(subSellList);
            }else if (buyNoticeList.size() <= NoticeConstants.TWO && sellNoticeList.size() > NoticeConstants.TWO){
                int sellSize = getRandom(sellNoticeList);
                List<Notice> subSellList = getSubList(sellNoticeList, sellSize);
                for (int i = 0; i < buyNoticeList.size(); i++){setUserTxDetail(buyNoticeList, i);}
                for (int i = 0; i < subSellList.size(); i++){setUserTxDetail(subSellList, i);}
                partList.addAll(buyNoticeList);
                partList.addAll(subSellList);
            }else if (buyNoticeList.size() > NoticeConstants.TWO && sellNoticeList.size() <= NoticeConstants.TWO){
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

    public RestResp queryMeAllNotice(Long userId, Integer pageNum, Long noticeType, Integer txStatus){
        try {
            List<Notice> resultList = new ArrayList<>();
            Pageable pageable = new PageRequest(pageNum - 1, 5, new Sort(Sort.Direction.ASC, "createTime"));
            Page<Notice> page = null;
            if (txStatus.equals(NoticeTxStatus.DONE_TX)){
                page = noticeDao.findByUserIdAndNoticeTypeAndTxStatus(userId, noticeType, NoticeTxStatus.DONE_TX, pageable);
                Iterator<Notice> it = page.iterator();
                while (it.hasNext()){
                    resultList.add(it.next());
                }
            }else {
                List<Notice> unDoneNoticeList = noticeDao.findByUserIdAndNoticeTypeAndTxStatus(userId, noticeType, NoticeTxStatus.UNDONE_TX);
                resultList.addAll(unDoneNoticeList);
            }
            PageDTO<Notice> pageDTO = new PageDTO<>();
            if (page == null){
                pageDTO.setCurrentPage(1);
                pageDTO.setPageSize(5);
                pageDTO.setRowCount((long)resultList.size());
                pageDTO.setTotalPage(1);
                pageDTO.setPageList(resultList);
            }else {
                pageDTO.setCurrentPage(pageNum);
                pageDTO.setPageSize(5);
                pageDTO.setRowCount(page.getTotalElements());
                pageDTO.setTotalPage(page.getTotalPages());
                pageDTO.setPageList(resultList);
            }
            return RestResp.success("操作成功", pageDTO);
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("查询我的公告异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    /**
     * 火币网API接口停止服务，获取行情失败
     */
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

    /**
     * 火币网API接口停止服务，获取行情失败
     */
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
            CNYDetail cnyDetail = cnyDetailDao.findBySymbol("¥");
            if (cnyDetail != null){
                return RestResp.success("操作成功", cnyDetail);
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
            Long noticeType = NoticeConst.NoticeType.SELL.getStatus();
            Integer pageNum = notice.getPageNum();

            Pageable pageable = new PageRequest(pageNum - 1, 5, new Sort(Sort.Direction.ASC, "createTime"));

            // 对所在地，货币类型，支付方式判断，可为null
            Page<Notice> page = null;
            if (null != location && null != currency && null != payType) {
                page = noticeDao.findByLocationAndCurrencyAndPayTypeAndNoticeTypeAndTxStatus(location, currency, payType, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            }else if (null != location && null != currency && null == payType) {
                page = noticeDao.findByLocationAndCurrencyAndNoticeTypeAndTxStatus(location, currency, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            } else if (null != location && null == currency && null != payType) {
                page = noticeDao.findByLocationAndPayTypeAndNoticeTypeAndTxStatus(location, payType, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            } else if (null == location && null != currency && null != payType) {
                page = noticeDao.findByCurrencyAndPayTypeAndNoticeTypeAndTxStatus(currency, payType, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            } else if (null != location && null == currency && null == payType) {
                page = noticeDao.findByLocationAndNoticeTypeAndTxStatus(location, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            } else if (null == location && null == currency && null != payType) {
                page = noticeDao.findByPayTypeAndNoticeTypeAndTxStatus(payType, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            } else if (null == location && null != currency && null != payType) {
                page = noticeDao.findByCurrencyAndNoticeTypeAndTxStatus(currency, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            } else if (null == location && null == currency && null == payType) {
                page = noticeDao.findByNoticeTypeAndTxStatus(noticeType, NoticeTxStatus.UNDONE_TX, pageable);
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
                    double goodP;
                    if (descTotal == 0){
                        goodP = 0;
                    }else {
                        goodP = ArithmeticUtils.divide(utdInfo.getGoodDesc(), descTotal, 2);
                    }

                    resultList.get(i).setGoodPercent((int)(goodP * 100));
                }

            }

            // 将page相关参数设置到DTO中返回
            PageDTO<Notice> pageDTO = new PageDTO<>();
            pageDTO.setCurrentPage(pageNum);
            pageDTO.setPageSize(5);
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
            Long noticeType = NoticeConst.NoticeType.BUY.getStatus();
            Integer pageNum = notice.getPageNum();

            Pageable pageable = new PageRequest(pageNum - 1, 5, new Sort(Sort.Direction.ASC, "createTime"));

            Page<Notice> page = null;
            if (null != location && null != currency && null != payType) {
                page = noticeDao.findByLocationAndCurrencyAndPayTypeAndNoticeTypeAndTxStatus(location, currency, payType, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            }else if (null != location && null != currency && null == payType) {
                page = noticeDao.findByLocationAndCurrencyAndNoticeTypeAndTxStatus(location, currency, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            } else if (null != location && null == currency && null != payType) {
                page = noticeDao.findByLocationAndPayTypeAndNoticeTypeAndTxStatus(location, payType, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            } else if (null == location && null != currency && null != payType) {
                page = noticeDao.findByCurrencyAndPayTypeAndNoticeTypeAndTxStatus(currency, payType, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            } else if (null != location && null == currency && null == payType) {
                page = noticeDao.findByLocationAndNoticeTypeAndTxStatus(location, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            } else if (null == location && null == currency && null != payType) {
                page = noticeDao.findByPayTypeAndNoticeTypeAndTxStatus(payType, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            } else if (null == location && null != currency && null != payType) {
                page = noticeDao.findByCurrencyAndNoticeTypeAndTxStatus(currency, noticeType, NoticeTxStatus.UNDONE_TX, pageable);
            } else if (null == location && null == currency && null == payType) {
                page = noticeDao.findByNoticeTypeAndTxStatus(noticeType, NoticeTxStatus.UNDONE_TX, pageable);
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
                    double goodP;
                    if (descTotal == 0){
                        goodP = 0;
                    }else {
                        goodP = ArithmeticUtils.divide(utdInfo.getGoodDesc(), descTotal, 2);
                    }
                    resultList.get(i).setGoodPercent((int)(goodP * 100));
                }
            }

            PageDTO<Notice> pageDTO = new PageDTO<>();
            pageDTO.setCurrentPage(pageNum);
            pageDTO.setPageSize(5);
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
            if (noticeInfo.getTxStatus().equals(NoticeTxStatus.DONE_TX)) {
                return RestResp.fail("公告已下架");
            }
            List<Notice> noticeList = noticeDao.findByUserIdAndNoticeTypeAndTxStatus(noticeInfo.getUserId(), noticeInfo.getNoticeType(), noticeInfo.getTxStatus());
            if (noticeList.size() == 0){
                return RestResp.fail("操作失败");
            }
            for (Notice n : noticeList) {
                n.setTxStatus(NoticeTxStatus.DONE_TX);
                noticeDao.save(n);
            }
            return RestResp.success("操作成功");
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("下架公告异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    public RestResp queryNoticeOne(Long id){
        try {
            Notice notice = noticeDao.findOne(id);
            return RestResp.success("操作成功", notice);
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("根据公告ID查找异常", e.getMessage());
        }
        return RestResp.fail("操作失败");
    }

    public RestResp updateTxStatus(Long id, Integer txStatus){
        try {
            Notice notice = noticeDao.findOne(id);
            notice.setTxStatus(txStatus);
            Notice n = noticeDao.save(notice);
            return RestResp.success("操作成功", n);
        }catch (Exception e){
            e.printStackTrace();
            LOG.error("修改公告交易状态异常", e.getMessage());
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
            CNYDetail cnyDetail = cnyDetailDao.findBySymbol("¥");

            if (location.iterator().hasNext() && currency.iterator().hasNext() && payment.iterator().hasNext() && searchType.iterator().hasNext()){
                StatusDTO statusDTO = new StatusDTO<>();
                statusDTO.setLocationList(location);
                statusDTO.setCurrencyList(currency);
                statusDTO.setPaymentList(payment);
                statusDTO.setSearchTypeList(searchType);
                statusDTO.setBTCMarketList(btcTiker);
                statusDTO.setCnyDetailList(cnyDetail);
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
     */
    private int getRandom(List list){
        return new Random().nextInt(list.size() - 2);
    }

    /**
     * 对list截取，获取size为2的新list
     */
    private List getSubList(List list, int size){
        return list.subList(size, size + 2);
    }

    /**
     * 设置用户交易详情数据
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
            double trustP;
            if (userTxDetail.getTxNum() == 0){
                trustP = 0;
            }else {
                trustP = ArithmeticUtils.divide(userTxDetail.getBelieveNum(), userTxDetail.getTxNum(), 2);
            }
            subList.get(i).setTrustPercent((int) ArithmeticUtils.multiply(trustP, 100, 0));
        }

    }



}
