package com.oxchains.themis.order.common;

import com.tiemens.secretshare.engine.SecretShare;
import com.tiemens.secretshare.math.BigIntUtilities;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by huohuo on 2017/10/25.
 */
public class ShamirUtil {
    private static final Integer K = 2;
    private static final Integer N = 3;
    private static final String description = "this is 4096";
    private static final SecretShare.PublicInfo p = new SecretShare.PublicInfo(N,K,SecretShare.getPrimeUsedFor4096bigSecretPayload(),description);
    private static final SecretShare SPLIT = new SecretShare(p);
    public static String[] splitAuth(String suth){
        BigInteger secret = BigIntUtilities.Human.createBigInteger(suth);
        SecretShare.SplitSecretOutput split = SPLIT.split(secret);
        List<SecretShare.ShareInfo> shareInfos = split.getShareInfos();
        String[] arr = {"","",""};
        for(int i = 0;i<shareInfos.size();i++){
            arr[i] = shareInfos.get(i).getShare().toString()+"_"+(i+1);
        }
        return arr;
    }
    public static String getAuth(String[] arr){
        List<SecretShare.ShareInfo> lists = new ArrayList<SecretShare.ShareInfo>();

        for(int i = 0;i<arr.length;i++){
            String str = arr[i];
            String st = str.substring(str.lastIndexOf("_")+1);
            String s = str.substring(0,str.lastIndexOf("_"));
            lists.add(new SecretShare.ShareInfo(Integer.parseInt(st) ,new BigInteger(s), new SecretShare.PublicInfo(N, K, SecretShare.getPrimeUsedFor4096bigSecretPayload(), description)));
        }
        SecretShare.CombineOutput combine = SPLIT.combine(lists);
        BigInteger secret1 = combine.getSecret();
        byte[] bytes = secret1.toByteArray();
        String auth = new String(bytes);
        return auth;
    }

}