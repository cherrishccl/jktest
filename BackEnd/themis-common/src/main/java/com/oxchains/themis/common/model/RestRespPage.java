package com.oxchains.themis.common.model;

import lombok.Data;

/**
 * Created by xuqi on 2017/11/21.
 */
@Data
public class RestRespPage extends RestResp {
    private RestRespPage(int status, String message, Object data) {
        super(status, message, data);
    }
    public Integer pageCount;

    private RestRespPage(int status, String messsage) {
        this(status, messsage, null);
    }

    public static RestRespPage success(Object data,Integer pageCount) {
        RestRespPage page = new RestRespPage(1, "success", data);
        page.setPageCount(pageCount);
        return page;
    }

    public static RestRespPage success() {
        return new RestRespPage(1, "success");
    }

    public static RestRespPage success(String message, Object data){
        return new RestRespPage(1, message, data);
    }

    public static RestRespPage fail(String message, Object data){
        return new RestRespPage(-1, message, data);
    }

    public static RestRespPage fail(String message){
        return new RestRespPage(-1, message);
    }
    public static RestRespPage fail(){
        return new RestRespPage(-1, "fail");
    }
}
