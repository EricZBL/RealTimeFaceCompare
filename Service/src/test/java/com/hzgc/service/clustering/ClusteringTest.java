package com.hzgc.service.clustering;

import com.hzgc.dubbo.clustering.ClusteringAttribute;
import com.hzgc.service.util.HBaseHelper;
import com.hzgc.service.util.HBaseUtil;
import com.hzgc.util.common.ObjectUtil;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.List;

/**
 * 告警聚类信息读写测试类及方法（彭聪）
 */
public class ClusteringTest {
    public static void main(String[] args) {
       /* List<ClusteringAttribute> clusteringAttributeList = new ArrayList<>();
        String rowkey = "2018-06";
        ClusteringAttribute clusteringAttribute = new ClusteringAttribute();
        clusteringAttribute.setClusteringId("1");
        clusteringAttribute.setFirstAppearTime("2018-01-15");
        clusteringAttribute.setFtpUrl("ftp://s105/skdfjllj/fjdslafjl.jpg");
        clusteringAttributeList.add(clusteringAttribute);
        clusteringAttribute.setClusteringId("2");
        clusteringAttribute.setFirstAppearTime("2018-01-15");
        clusteringAttribute.setFtpUrl("ftp://s105/skdfjllj/fjdslafjl.jpg");
        clusteringAttribute.setClusteringId("3");
        clusteringAttribute.setFirstAppearTime("2018-01-15");
        clusteringAttribute.setFtpUrl("ftp://s105/skdfjllj/fjdslafjl.jpg");
        clusteringAttributeList.add(clusteringAttribute);
        clusteringAttribute.setCount(3);
        clusteringAttributeList.add(clusteringAttribute);
        putDataToHBase(rowkey, clusteringAttributeList);
        getDataFromHBase("2018-06");*/
        /*ClusteringSearchServiceImpl clusteringSearchService = new ClusteringSearchServiceImpl();
        List<ClusteringAttribute> clusteringAttributeList1 = clusteringSearchService.clusteringSearch("2018-02", 0, 50, "");
        List<Integer> blist = new java.util.ArrayList<>();
        blist.add(0);
        blist.add(1);
        blist.add(3);
        blist.add(4);
        blist.add(7);
        blist.add(8);
        blist.add(11);
        blist.add(15);
        blist.add(18);
        List<ClusteringAttribute> clusteringAttributeList2 = new ArrayList<>();
        for (int i = 0; i < clusteringAttributeList1.size(); i++) {
            if (!blist.contains(i)){
                clusteringAttributeList2.add(clusteringAttributeList1.get(i));
            }
        }
        putDataToHBase("2018-02", clusteringAttributeList2);*/
    }

    private static void putDataToHBase(String rowKey, List<ClusteringAttribute> clusteringAttributeList) {
        Table clusteringInfoTable = HBaseHelper.getTable(ClusteringTable.TABLE_ClUSTERINGINFO);
        Put put = new Put(Bytes.toBytes(rowKey));
        put.addColumn(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_DATA, ObjectUtil.objectToByte(clusteringAttributeList));
        try {
            clusteringInfoTable.put(put);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            HBaseUtil.closTable(clusteringInfoTable);
        }
    }

    private static void getDataFromHBase(String rowKey) {
        Table clusteringInfoTable = HBaseHelper.getTable(ClusteringTable.TABLE_ClUSTERINGINFO);
        Get get = new Get(Bytes.toBytes(rowKey));
        List<ClusteringAttribute> clusteringAttributeList;
        ClusteringAttribute clusteringAttribute;
        try {
            Result result = clusteringInfoTable.get(get);
            clusteringAttributeList = (List<ClusteringAttribute>) ObjectUtil.byteToObject(result.getValue(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_DATA));
            clusteringAttribute = clusteringAttributeList.get(0);
            System.out.println(clusteringAttribute.getClusteringId());
            System.out.println(clusteringAttribute.getCount());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
