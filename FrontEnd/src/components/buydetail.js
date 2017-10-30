/**
 * Created by oxchain on 2017/10/23.
 */

import React, { Component } from 'react';

import { connect } from 'react-redux';
import { fetctBuyBtcDetail,fetctBuynow} from '../actions/releaseadvert'
import {
    Modal,
    ModalHeader,
    ModalTitle,
    ModalClose,
    ModalBody,
    ModalFooter
} from 'react-modal-bootstrap';

class Buydetail extends Component {
    constructor(props) {
        super(props);
        this.state = {
            isModalOpen: false,
            error: null,
            actionResult: '',
            messmoney:'',
            messnum:'',
        }
        this.handelChange = this.handelChange.bind(this)
        this.handleSubmit = this.handleSubmit.bind(this)
    }
    hideModal = () => {
        this.setState({
            isModalOpen: false
        });
    };
    componentWillMount(){
        const noticeId = this.props.match.params.id.slice(1)
        console.log( this.props.match.params.id.slice(1))
        this.props.fetctBuyBtcDetail({noticeId});
    }
    handelChange(e){
        const data = this.props.all.notice || [];
        let type = e.target.name;
        if (type == "money") {
            this.setState({
                messmoney: e.target.value,
                messnum: (e.target.value) / data.price
            })
        } else if (type == "btc") {
            this.setState({
                messmoney: (e.target.value) * data.price,
                messnum: e.target.value
            })
        }
    }
    handleSubmit(){
        const formdata = {
            userId: localStorage.getItem("userId"),
            noticeId : this.props.match.params.id.slice(1),
            money : this.state.messmoney,
            amount : this.state.messnum
        }
        this.props.fetctBuynow({formdata},err=>{
            this.setState({ isModalOpen: true , error: err , actionResult: err||'下单成功!'})
        });
    }
    render() {
        const messmoney = this.state.messmoney;
        const messnum = this.state.messnum;
        const data = this.props.all.notice || [];
        const datanum = this.props.all
     const time = data.validPayTime/1000/60
        return (
            <div className="maincontent">
                <div className="detail-title">
                    <div className="col-lg-8 col-xs-12 col-md-12" style={{padding:0}}>
                        <div className="col-lg-3 col-xs-3 col-md-3 title-img">
                            <img src="./public/img/touxiang.jpg" style={{width:100+'px'}} alt=""/>
                        </div>
                        <div className="col-lg-9 col-xs-9 col-md-9 title-img">
                            <h4 style={{marginBottom:10+'px',paddingLeft:15+'px'}}>{datanum.loginname}</h4>
                            <ul className="detailul">
                                <li>
                                    <p>{datanum.txNum}</p>
                                    <p>交易次数</p>
                                </li>
                                <li>
                                    <p>{datanum.believeNum}</p>
                                    <p>信任人数</p>
                                </li>
                                <li>
                                    <p>{datanum.goodDegree}</p>
                                    <p>好评度</p>
                                </li>
                                <li>
                                    <p>{datanum.goodDegree}</p>
                                    <p>历史成交数</p>
                                </li>
                            </ul>
                        </div>
                    </div>
                </div>
                <div className="price-detail clear">
                    <div className="col-lg-9 col-xs-9 col-md-9">
                        <div>
                            <ul className="priceul">
                                <li>报价 : &#x3000;&#x3000;&#x3000;&#x3000;&#x3000;{data.price}CNY/BTC</li>
                                <li>交易额度 : &#x3000;&#x3000;&#x3000;{data.minTxLimit}-{data.maxTxLimit} CNY</li>
                                <li>付款方式 : &#x3000;&#x3000;&#x3000;{data.payType}</li>
                                <li>付款期限 : &#x3000;&#x3000;&#x3000;{time}分钟</li>
                            </ul>
                            <h4 className="sellwhat">你想购买多少？</h4>
                            <input type="text" className="inputmoney sellmoney" onChange={this.handelChange} name="money" value={messmoney} placeholder="请输入你想购买的金额"/>
                            <i className="fa fa-exchange" aria-hidden="true"></i>
                            <input type="text" className="inputmoney sellmoney" onChange={this.handelChange} name="btc" value={messnum} placeholder="请输入你想购买的数量"/>
                            <button className="form-sell" onClick={this.handleSubmit}>立刻购买</button>
                        </div>
                    </div>
                </div>

                <div className="detail-notice">
                    <h4 className="sellwhat">交易须知</h4>
                    <p>1.交易前请详细了解对方的交易信息。</p>
                    <p>2.请通过平台进行沟通约定，并保存好相关聊天记录。</p>
                    <p>3.如遇到交易纠纷，可通过申诉来解决问题。</p>
                    <p>4.在您发起交易请求后，比特币被锁定在托管中，受到themis保护。如果您是买家，发起交易请求后，请在付款周期内付款并把交易标记为付款已完成。卖家在收到付款后将会放行处于托管中的比特币。</p>
                    <p>交易前请阅读《themis服务条款》以及常见问题，交易指南等帮助文档。</p>
                    <p>5.请注意欺诈风险，交易前请检查该用户收到的评价信息和相关信用信息，并对新近创建的账户多加留意。</p>
                    <p>6.托管服务保护网上交易的买卖双方。在双方发生争议的情况下，我们将评估所提供的所有信息，并将托管的比特币放行给其合法所有者。</p>
                </div>

                <Modal isOpen={this.state.isModalOpen} onRequestHide={this.hideModal}>
                    <ModalHeader>
                        <ModalClose onClick={this.hideModal}/>
                        <ModalTitle>提示:</ModalTitle>
                    </ModalHeader>
                    <ModalBody>
                        <p className={this.state.error?'text-red':'text-green'}>
                            {this.state.actionResult}
                        </p>
                    </ModalBody>
                    <ModalFooter>
                        <button className='btn btn-default' onClick={this.hideModal}>
                            {/*<a href="/myadvert" >关闭</a>*/}
                            <a className="close-modal" href="/orderprogress" >关闭</a>
                        </button>
                    </ModalFooter>
                </Modal>
            </div>
        );
    }
}


function mapStateToProps(state) {
    return {
        data:state.advert.data,     //点击立刻购买返回的data
        all:state.advert.all       //广告详情页面加载时的数据
    };
}
export default connect(mapStateToProps,{ fetctBuyBtcDetail,fetctBuynow })(Buydetail);
