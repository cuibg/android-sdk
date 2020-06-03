package com.qiniu.android.http.request.serverRegion;

import com.qiniu.android.common.ZoneInfo;
import com.qiniu.android.http.dns.DnsPrefetcher;
import com.qiniu.android.http.request.UploadRegion;
import com.qiniu.android.http.request.UploadServerInterface;

import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class UploadDomainRegion implements UploadRegion {

    private boolean isAllFreezed;
    private ArrayList<String> domainHostList;
    private HashMap<String, UploadServerDomain> domainHashMap;
    private ArrayList<String> oldDomainHostList;
    private HashMap<String, UploadServerDomain> oldDomainHashMap;
    private ZoneInfo zoneInfo;

    @Override
    public ZoneInfo getZoneInfo() {
        return zoneInfo;
    }

    @Override
    public void setupRegionData(@NotNull ZoneInfo zoneInfo) {
        if (zoneInfo == null) {
            return;
        }

        this.zoneInfo = zoneInfo;

        isAllFreezed = false;
        ArrayList<String> domainHostList = new ArrayList<>();
        ArrayList<ZoneInfo.UploadServerGroup> serverGroups = new ArrayList<>();
        if (zoneInfo.acc != null){
            serverGroups.add(zoneInfo.acc);
            if (zoneInfo.acc.allHosts != null){
                domainHostList.addAll(zoneInfo.acc.allHosts);
            }
        }
        if (zoneInfo.src != null){
            serverGroups.add(zoneInfo.src);
            if (zoneInfo.src.allHosts != null){
                domainHostList.addAll(zoneInfo.src.allHosts);
            }
        }
        this.domainHostList = domainHostList;
        domainHashMap = createDomainDictionary(serverGroups);

        ArrayList<String> oldDomainHostList = new ArrayList<>();
        serverGroups = new ArrayList<>();
        if (zoneInfo.old_acc != null){
            serverGroups.add(zoneInfo.old_acc);
            if (zoneInfo.old_acc.allHosts != null){
                oldDomainHostList.addAll(zoneInfo.old_acc.allHosts);
            }
        }
        if (zoneInfo.old_src != null){
            serverGroups.add(zoneInfo.old_src);
            if (zoneInfo.old_src.allHosts != null){
                oldDomainHostList.addAll(zoneInfo.old_src.allHosts);
            }
        }
        this.oldDomainHostList = oldDomainHostList;
        oldDomainHashMap = createDomainDictionary(serverGroups);
    }

    @Override
    public UploadServerInterface getNextServer(boolean isOldServer, UploadServerInterface freezeServer) {
        if (isAllFreezed){
            return null;
        }
        if (freezeServer != null && freezeServer.getServerId() != null){
            UploadServerDomain domain = null;
            domain = domainHashMap.get(freezeServer.getServerId());
            if (domain != null){
                domain.freeze(freezeServer.getIp());
            }
            domain = oldDomainHashMap.get(freezeServer.getServerId());
            if (domain != null){
                domain.freeze(freezeServer.getIp());
            }
        }

        ArrayList<String> hostList = isOldServer ? oldDomainHostList : domainHostList;
        HashMap<String, UploadServerDomain> domainInfo = isOldServer ? oldDomainHashMap : domainHashMap;
        UploadServerInterface server = null;
        for (String host : hostList) {
            UploadServerDomain domain = domainInfo.get(host);
            if (domain != null){
                server =  domain.getServer();
            }
            if (server != null){
                break;
            }
        }

        if (server == null){
            isAllFreezed = true;
        }

        return server;
    }

    private HashMap<String, UploadServerDomain> createDomainDictionary(ArrayList<ZoneInfo.UploadServerGroup> serverGroups){
        Date freezeDate = new Date(0);
        HashMap<String, UploadServerDomain> domainHashMap = new HashMap<>();
        for (int i = 0; i < serverGroups.size(); i++) {
            ZoneInfo.UploadServerGroup serverGroup = serverGroups.get(i);
            for (int j = 0; j < serverGroup.allHosts.size(); j++){
                String host = serverGroup.allHosts.get(j);
                UploadServerDomain domain = new UploadServerDomain(host);
                domainHashMap.put(host, domain);
            }
        }
        return  domainHashMap;
    }


    private static class UploadServerDomain{

        private boolean isAllFreezed = false;
        protected final String host;
        protected ArrayList<UploadIpGroup> ipGroupList = new ArrayList<>();

        protected UploadServerDomain(String host){
            this.host = host;
        }

        protected UploadServerInterface getServer(){
            if (isAllFreezed || host == null || host.length() == 0){
                return null;
            }

            if (ipGroupList == null || ipGroupList.size() == 0){
                createIpGroupList();
            }

            if (ipGroupList != null && ipGroupList.size() > 0){
                UploadServer server = null;
                for (UploadIpGroup ipGroup : ipGroupList){
                    if (UploadServerFreezeManager.getInstance().isFreezeHost(host, ipGroup.groupType)){
                        server = new UploadServer(host, host, ipGroup.getServerIP());
                        break;
                    }
                }
                if (server == null){
                    isAllFreezed = true;
                }
                return server;
            } else if (!UploadServerFreezeManager.getInstance().isFreezeHost(host, null)){
                return new UploadServer(host, host, null);
            } else {
                isAllFreezed = true;
                return null;
            }
        }

        private synchronized void createIpGroupList(){
           if (ipGroupList != null && ipGroupList.size() > 0){
               return;
           }

           List<InetAddress> inetAddresses = DnsPrefetcher.getInstance().getInetAddressByHost(host);
           if (inetAddresses == null || inetAddresses.size() == 0){
               return;
           }

           HashMap<String, ArrayList<String>> ipGroupInfos = new HashMap<>();
           for (InetAddress inetAddress : inetAddresses){
               String ipValue = inetAddress.getHostAddress();
               String groupType = getIpType(ipValue);
               if (groupType != null){
                   ArrayList<String> ipList = ipGroupInfos.get(groupType) != null ? ipGroupInfos.get(groupType) : (new ArrayList<String>());
                   ipList.add(ipValue);
                   ipGroupInfos.put(groupType, ipList);
               }
           }

           ArrayList<UploadIpGroup> ipGroupList = new ArrayList<>();
           for (String groupType : ipGroupInfos.keySet()){
               ArrayList<String> ipList = ipGroupInfos.get(groupType);
               UploadIpGroup ipGroup = new UploadIpGroup(groupType, ipList);
               ipGroupList.add(ipGroup);
           }
           this.ipGroupList = ipGroupList;
        }

        protected void freeze(String ip){
            UploadServerFreezeManager.getInstance().freezeHost(host, getIpType(ip));
        }

        private String getIpType(String ip){
            String type = null;
            if (ip == null || ip.length() == 0) {
                return type;
            }
            if (ip.contains(":")) {
                type = "ipv6";
            } else if (ip.contains(".")){
                String[] ipNumbers = ip.split(".");
                if (ipNumbers.length == 4){
                    int firstNumber = Integer.parseInt(ipNumbers[0]);
                    if (firstNumber > 0 && firstNumber < 127) {
                        type = "ipv4-A";
                    } else if (firstNumber > 127 && firstNumber <= 191) {
                        type = "ipv4-B";
                    } else if (firstNumber > 191 && firstNumber <= 223) {
                        type = "ipv4-C";
                    } else if (firstNumber > 223 && firstNumber <= 239) {
                        type = "ipv4-D";
                    } else if (firstNumber > 239 && firstNumber < 255) {
                        type = "ipv4-E";
                    }
                }
            }
            return type;
        }
    }

    private static class UploadIpGroup{
        protected final String groupType;
        protected final ArrayList<String> ipList;

        protected UploadIpGroup(String groupType,
                              ArrayList<String> ipList) {
            this.groupType = groupType;
            this.ipList = ipList;
        }

        protected String getServerIP(){
            if (ipList == null || ipList.size() == 0){
                return null;
            } else {
                int index = (int)(Math.random()*ipList.size());
                return ipList.get(index);
            }
        }

    }



}
