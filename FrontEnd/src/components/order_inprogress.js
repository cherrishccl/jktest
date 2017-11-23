/**
 * Created by zhangxiaojing on 2017/10/20.
 */
import React, { Component }from 'react';
import {connect} from 'react-redux';
import { Link } from 'react-router-dom';
import {Pagination, Alert, Upload, Button, Icon, Modal, Popconfirm} from 'antd';
// import {Alert, Upload, Button, Icon, Modal} from 'antd';
import {ROOT_ARBITRATE} from '../actions/types';
import {uploadEvidence} from '../actions/arbitrate';
import {fetchNoCompletedOrders} from '../actions/order';

class OrderInProgress extends Component {
    constructor(props) {
        super(props);
        this.state = {
            visible:false,
            evidenceOFile:[],
            id:1,
            alertVisible:false,
            isEvidenceFileDone:false,
            pageSize:8, //每页显示的条数8条
            fileList:[],
            uploading:false,
        };
        this.renderrow = this.renderrow.bind(this);
    }
    componentWillMount() {
        const userId= localStorage.getItem('userId');
        const formData={
            userId:userId,
            pageNum:1,
            pageSize:this.state.pageSize, //每页显示的条数8条
        };
        this.props.fetchNoCompletedOrders({formData});
    }
    handleEvidence(item){
        this.setState({
            id:item,
            visible: true,
        });
    }
    handleEvidenceSubmit(){
        const evidenceDes=this.refs.voucherDes.value;
        const {fileList} = this.state;
        if(evidenceDes||fileList){
            const userId= localStorage.getItem('userId');
            const id=this.state.id;
            const formData = new FormData();
            fileList.forEach((file) => {
                formData.append('files', file);
            });
            formData.append("id", id);
            formData.append("userId", userId);
            formData.append("content", evidenceDes);
            this.setState({
                uploading: true,
            });
            this.props.uploadEvidence({formData}, (msg)=>{
                console.log(msg);
                if(msg.status == 1){
                    this.setState({
                        fileList: [],
                        uploading: false,
                    });
                    window.location.href='/orderinprogress';
                }
                else{
                    this.setState({
                        alertVisible:true,
                        uploading: false,
                    });
                }
            });
        }
    }
    handlePagination(pageNum) {
        const userId= localStorage.getItem('userId');
        const formData={
            userId:userId,
            pageNum:pageNum,
            pageSize:this.state.pageSize
        };
        this.props.fetchNoCompletedOrders({formData}, ()=>{});
    }
    renderrow(){
        return this.props.not_completed_orders.data.map((item, index) =>{
            return(
                <tr key={index} >
                    <td>{item.friendUsername}</td>
                    <td>{item.id}</td>
                    <td>{item.orderType}</td>
                    <td>{item.money}</td>
                    <td>{item.amount}</td>
                    <td>{item.createTime}</td>
                    <td>{item.orderStatusName}<span>{item.arbitrate == 1 ? "(仲裁中)": ""}</span></td>
                    <td><button className="ant-btn ant-btn-primary ant-btn-lg" onClick={this.handleOrderDetail.bind(this, item)}>详情</button></td>
                    {/*<td><Link className="ant-btn ant-btn-primary ant-btn-lg" to={`/orderprogress:${item.id}`}>详情</Link></td>*/}
                    <td>{item.orderStatus == 3 || item.orderStatus == 8 ?
                        <Popconfirm title="是否要申请仲裁?" onConfirm={this.handleEvidence.bind(this, item.id)}  okText="确定" cancelText="取消">
                            <button className="ant-btn ant-btn-primary ant-btn-lg">THEMIS仲裁</button>
                        </Popconfirm> : <div></div>}</td>
                </tr>
                );
        });
    }
    handleOrderDetail(item){
        const userId= localStorage.getItem('userId');
        const orderData={id:item.id, userId:userId, partnerId:item.sellerId == userId ? item.buyerId : item.sellerId, friendUsername:item.friendUsername};
        localStorage.setItem("partner", JSON.stringify(orderData));
        window.location.href='/orderprogress';
    }
    render() {
        console.log(this.props.not_completed_orders);
        let close = () => {
            this.setState({visible:false});
        };
        const not_completed_orders = this.props.not_completed_orders;
        const totalNum = not_completed_orders && not_completed_orders.pageCount;
        const data=not_completed_orders && not_completed_orders.data;
        const { uploading, visible} = this.state;
        const props = {
            action:`${ROOT_ARBITRATE}/arbitrate/uploadEvidence`,
            onRemove: (file) => {
                this.setState(({ fileList }) => {
                    const index = fileList.indexOf(file);
                    const newFileList = fileList.slice();
                    newFileList.splice(index, 1);
                    return {
                        fileList: newFileList,
                    };
                });
            },
            beforeUpload: (file) => {
                this.setState(({ fileList }) => ({
                    fileList: [...fileList, file],
                }));
                return false;
            },
            fileList: this.state.fileList,
        };
        return (
            <div style={{width:"100%"}}>
                <div className="container g-pb-150">
                    <div className="orderType text-center g-pt-50 g-pb-50">
                        <ul className="row">
                            <li className="col-xs-6"> <Link className="orderTypeBar g-pb-3" to="/orderinprogress">进行中的交易</Link></li>
                            <li className="col-xs-6"><Link className="g-pb-3" to="/ordercompleted">已完成的交易</Link></li>
                        </ul>
                    </div>
                    <div className="table-responsive">
                        <div className="table table-striped table-hover">
                            <table className="table">
                                <thead>
                                <tr>
                                    <th>交易伙伴</th>
                                    <th>订单编号</th>
                                    <th>类型</th>
                                    <th>交易金额</th>
                                    <th>交易数量</th>
                                    <th>创建时间</th>
                                    <th>交易状态</th>
                                    <th>操作</th>
                                    <th></th>
                                </tr>
                                </thead>
                                <tbody>
                                { data == null ? <tr><td className="text-center h5" colSpan={9}>loading....</td></tr> : this.renderrow()}
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <div className="pagecomponent">
                        <Pagination  defaultPageSize={this.state.pageSize} total={totalNum}  onChange={e => this.handlePagination(e)}/>
                    </div>
                    {/*<Pagination defaultPageSize={1} total = {200}/>*/}
                    <Modal visible={visible} title="证据存根" onOk={this.handleEvidenceSubmit} onCancel={close}
                        footer={[<Button key="back" size="large" onClick={close}>取消</Button>,
                            <Button key="submit" size="large" className="upload-demo-start" type="primary" onClick={this.handleEvidenceSubmit.bind(this)} disabled={this.state.fileList.length === 0} loading={uploading}>
                                {uploading ? '上传中' : '确定' }
                            </Button>
                        ]}>
                        <div className="clearfix">
                            <Upload {...props}>
                                <Button>
                                    <Icon type="upload" /> 聊天截图
                                </Button>
                                <div>最多可上传5张图片</div>
                            </Upload>
                        </div>
                        <textarea className="form-control" name="" id="" cols="30" rows="10" placeholder="请输入此次仲裁重要部分证据和备注" ref="voucherDes"></textarea>
                        {this.state.alertVisible ? <Alert message="上传图片超出限制" type="error" showIcon /> :""}
                    </Modal>
                </div>
            </div>
        );
    }
}
function mapStateToProps(state) {
    return {
        not_completed_orders: state.order.not_completed_orders
    };
}

export default connect(mapStateToProps, {fetchNoCompletedOrders, uploadEvidence})(OrderInProgress);