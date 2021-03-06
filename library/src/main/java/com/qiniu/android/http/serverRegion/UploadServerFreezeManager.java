package com.qiniu.android.http.serverRegion;

import com.qiniu.android.utils.Utils;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by yangsen on 2020/6/3
 */
public class UploadServerFreezeManager {

    private ConcurrentHashMap<String, UploadServerFreezeItem> frozenInfo = new ConcurrentHashMap<>();
    private final static UploadServerFreezeManager manager = new UploadServerFreezeManager();

    public UploadServerFreezeManager(){
    }

    public static UploadServerFreezeManager getInstance(){
        return manager;
    }

    public boolean isFreezeHost(String host, String type){
        if (host == null || host.length() == 0){
            return true;
        }
        boolean isFrozen = true;
        String infoKey = getItemInfoKey(host, type);
        UploadServerFreezeItem item = frozenInfo.get(infoKey);
        if (item == null || !item.isFrozenByDate(new Date())){
            isFrozen = false;
        }
        return isFrozen;
    }

    public void freezeHost(String host, String type, int frozenTime){
        if (host == null || host.length() == 0){
            return;
        }
        String infoKey = getItemInfoKey(host, type);
        UploadServerFreezeItem item = frozenInfo.get(infoKey);
        if (item == null){
            item = new UploadServerFreezeItem(host, type);
            frozenInfo.put(infoKey, item);
        }
        item.freeze(frozenTime);
    }

    public void unfreezeHost(String host, String type){
        if (host == null || host.length() == 0){
            return;
        }
        String infoKey = getItemInfoKey(host, type);
        if (infoKey != null){
            frozenInfo.remove(infoKey);
        }
    }

    private String getItemInfoKey(String host, String type){
        return String.format("%s:%s", (host != null ? host : "none"), (type != null ? type : "none"));
    }

    private static class UploadServerFreezeItem{
        protected final String host;
        protected final String type;
        protected Date freezeDate;

        protected UploadServerFreezeItem(String host,
                                       String type) {
            this.host = host;
            this.type = type;
        }

        protected synchronized boolean isFrozenByDate(Date date){
            boolean isFrozen = true;
            if (freezeDate == null || freezeDate.getTime() < date.getTime()){
                isFrozen = false;
            }
            return isFrozen;
        }

        protected synchronized void freeze(int frozenTime){
            freezeDate = new Date(Utils.currentTimestamp() + frozenTime*1000);
        }

    }

}
