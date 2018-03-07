package com.hzgc.service.clustering;

import com.hzgc.dubbo.clustering.AlarmInfo;
import com.hzgc.dubbo.clustering.ClusteringAttribute;
import com.hzgc.dubbo.clustering.ClusteringInfo;
import com.hzgc.dubbo.clustering.ClusteringSearchService;
import com.hzgc.dubbo.staticrepo.ObjectInfoTable;
import com.hzgc.service.staticrepo.ElasticSearchHelper;
import com.hzgc.service.util.HBaseHelper;
import com.hzgc.util.common.ObjectUtil;
import com.hzgc.util.sort.ListUtils;
import com.hzgc.util.sort.SortParam;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.hzgc.util.common.ObjectUtil.objectToByte;

/**
 * 告警聚类结果查询接口实现(彭聪)
 */
public class ClusteringSearchServiceImpl implements ClusteringSearchService {
    private static Logger LOG = Logger.getLogger(ClusteringSearchServiceImpl.class);

    /**
     * 查询聚类信息
     *
     * @param time      聚类时间
     * @param start     返回数据下标开始符号
     * @param limit     行数
     * @param sortParam 排序参数（默认按出现次数排序）
     * @return 聚类列表
     */
    @Override
    public ClusteringInfo clusteringSearch(String region, String time, int start, int limit, String sortParam) {
        Table clusteringInfoTable = HBaseHelper.getTable(ClusteringTable.TABLE_ClUSTERINGINFO);
        Get get = new Get(Bytes.toBytes(time));
        List<ClusteringAttribute> clusteringList = new ArrayList<>();
        try {
            Result result = clusteringInfoTable.get(get);
            clusteringList = (List<ClusteringAttribute>) ObjectUtil.byteToObject(result.getValue(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_YES));
            if (sortParam != null && sortParam.length() > 0) {
                SortParam sortParams = ListUtils.getOrderStringBySort(sortParam);
                ListUtils.sort(clusteringList, sortParams.getSortNameArr(), sortParams.getIsAscArr());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        int total = clusteringList.size();
        ClusteringInfo clusteringInfo = new ClusteringInfo();
        clusteringInfo.setTotalClustering(total);
        if (start > -1) {
            if ((start + limit) > total - 1) {
                clusteringInfo.setClusteringAttributeList(clusteringList.subList(start, total));
            } else {
                clusteringInfo.setClusteringAttributeList(clusteringList.subList(start, start + limit));
            }
        } else {
            LOG.info("start must bigger than -1");
        }
        return clusteringInfo;
    }

    /**
     * 查询单个聚类详细信息(告警记录)
     *
     * @param clusterId 聚类ID
     * @param time      聚类时间
     * @param start     分页查询开始行
     * @param limit     查询条数
     * @param sortParam 排序参数（默认时间先后排序）
     * @return 返回该类下面所以告警信息
     */
    @Override
    public List<AlarmInfo> detailClusteringSearch(String clusterId, String time, int start, int limit, String sortParam) {
        Table clusteringInfoTable = HBaseHelper.getTable(ClusteringTable.TABLE_DETAILINFO);
        Get get = new Get(Bytes.toBytes(time + "-" + clusterId));
        List<AlarmInfo> alarmInfoList = new ArrayList<>();
        try {
            Result result = clusteringInfoTable.get(get);
            alarmInfoList = (List<AlarmInfo>) ObjectUtil.byteToObject(result.getValue(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_YES));
            if (sortParam != null && sortParam.length() > 0) {
                SortParam sortParams = ListUtils.getOrderStringBySort(sortParam);
                ListUtils.sort(alarmInfoList, sortParams.getSortNameArr(), sortParams.getIsAscArr());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (start > -1) {
            if ((start + limit) > alarmInfoList.size() - 1) {
                return alarmInfoList.subList(start, alarmInfoList.size());
            } else {
                return alarmInfoList.subList(start, start + limit);
            }
        } else {
            LOG.info("start must bigger than -1");
            return null;
        }
    }

    /**
     * 查询单个聚类详细信息(告警ID)
     *
     * @param clusterId 聚类ID
     * @param time      聚类时间
     * @param start     分页查询开始行
     * @param limit     查询条数
     * @param sortParam 排序参数（默认时间先后排序）
     * @return 返回该类下面所以告警信息
     */
    @Override
    public List<Integer> detailClusteringSearch_v1(String clusterId, String time, int start, int limit, String sortParam) {
        /*Table clusteringInfoTable = HBaseHelper.getTable(ClusteringTable.TABLE_DETAILINFO);
        Get get = new Get(Bytes.toBytes(time + "-" + clusterId));
        List<Integer> alarmInfoList = new ArrayList<>();
        try {
            Result result = clusteringInfoTable.get(get);
            alarmInfoList = (List<Integer>) ObjectUtil.byteToObject(result.getValue(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_DATA));
            if (sortParam != null && sortParam.length() > 0) {
                SortParam sortParams = ListUtils.getOrderStringBySort(sortParam);
                ListUtils.sort(alarmInfoList, sortParams.getSortNameArr(), sortParams.getIsAscArr());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (start > -1) {
            if ((start + limit) > alarmInfoList.size() - 1) {
                return alarmInfoList.subList(start, alarmInfoList.size());
            } else {
                return alarmInfoList.subList(start, start + limit);
            }
        } else {
            LOG.info("start must bigger than -1");
            return null;
        }*/
        // TODO: 18-3-1 delopy should be change 
        BoolQueryBuilder totalBQ = QueryBuilders.boolQuery();
        if (clusterId != null && time != null) {
            totalBQ.must(QueryBuilders.matchPhraseQuery("clusterid", time + "-" + clusterId));
        }
        SearchRequestBuilder searchRequestBuilder = ElasticSearchHelper.getEsClient()
                .prepareSearch("dynamic_temp")
                .setTypes("person")
                .setQuery(totalBQ);
        SearchHit[] results = searchRequestBuilder.get().getHits().getHits();
        List<Integer> alarmIdList = new ArrayList<>();
        for (SearchHit result : results) {
            alarmIdList.add((int) result.getSource().get("alarmid"));
        }
        return alarmIdList;
    }

    /**
     * @param clusterId
     * @param time
     * @return
     */
    @Override
    public boolean deleteClustering(String clusterId, String time) {
        Table clusteringInfoTable = HBaseHelper.getTable(ClusteringTable.TABLE_ClUSTERINGINFO);
        Get get = new Get(Bytes.toBytes(time));
        Put put = new Put(Bytes.toBytes(time));
        try {
            Result result = clusteringInfoTable.get(get);
            if (whetherInclude(clusterId, time)) {
                List<ClusteringAttribute> clusteringAttributeList_yes = (List<ClusteringAttribute>) ObjectUtil.byteToObject(result.getValue(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_YES));
                for (ClusteringAttribute clusteringAttribute : clusteringAttributeList_yes) {
                    if (clusterId.equals(clusteringAttribute.getClusteringId())) {
                        clusteringAttributeList_yes.remove(clusteringAttribute);
                    }
                }
                byte[] clusteringInfo_yes = ObjectUtil.objectToByte(clusteringAttributeList_yes);
                put.addColumn(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_YES, clusteringInfo_yes);
            } else {
                List<ClusteringAttribute> clusteringAttributeList_no = (List<ClusteringAttribute>) ObjectUtil.byteToObject(result.getValue(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_YES));
                for (ClusteringAttribute clusteringAttribute : clusteringAttributeList_no) {
                    if (clusterId.equals(clusteringAttribute.getClusteringId())) {
                        clusteringAttributeList_no.remove(clusteringAttribute);
                    }
                }
                byte[] clusteringInfo_no = ObjectUtil.objectToByte(clusteringAttributeList_no);
                put.addColumn(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_NO, clusteringInfo_no);
            }
            clusteringInfoTable.put(put);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean whetherInclude(String clusterId, String time) {
        Table clusteringInfoTable = HBaseHelper.getTable(ClusteringTable.TABLE_ClUSTERINGINFO);
        Get get = new Get(Bytes.toBytes(time));
        try {
            Result result = clusteringInfoTable.get(get);
            List<ClusteringAttribute> clusteringAttributeList = (List<ClusteringAttribute>) ObjectUtil.byteToObject(result.getValue(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_YES));
            for (ClusteringAttribute clusteringAttribute : clusteringAttributeList) {
                if (clusterId.equals(clusteringAttribute.getClusteringId())) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * @param clusterId
     * @param time
     * @param flag      yes is ignore, no is not ignore
     * @return
     */
    @Override
    public boolean igoreClustering(String clusterId, String time, String flag) {
        Table clusteringInfoTable = HBaseHelper.getTable(ClusteringTable.TABLE_ClUSTERINGINFO);
        Get get = new Get(Bytes.toBytes(time));
        try {
            Result result = clusteringInfoTable.get(get);
            List<ClusteringAttribute> list_yes = (List<ClusteringAttribute>) ObjectUtil.byteToObject(result.getValue(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_YES));
            List<ClusteringAttribute> list_no = (List<ClusteringAttribute>) ObjectUtil.byteToObject(result.getValue(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_NO));
            //yes 表示数据需要忽略（HBase表中存入"n"列），no 表示数据不需要忽略（HBase表中存入"y"列）
            if (flag.equals("yes")) {
                for (ClusteringAttribute clusteringAttribute : list_yes) {
                    if (clusterId.equals(clusteringAttribute.getClusteringId())) {
                        list_yes.remove(clusteringAttribute);
                        clusteringAttribute.setFlag(flag);
                        list_no.add(clusteringAttribute);
                    }
                }
                return true;
            } else if (flag.equals("no")) {
                for (ClusteringAttribute clusteringAttribute : list_no) {
                    if (clusterId.equals(clusteringAttribute.getClusteringId())) {
                        list_no.remove(clusteringAttribute);
                        clusteringAttribute.setFlag(flag);
                        list_yes.add(clusteringAttribute);
                    }
                }
                return true;
            } else {
                LOG.warn("Illegal parameter, flag:" + flag);
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<Integer> detailClusteringSearch_Hbase(String clusterId, String time, int start, int limit, String sortParam) {
        Table clusteringInfoTable = HBaseHelper.getTable(ClusteringTable.TABLE_DETAILINFO);
        Get get = new Get(Bytes.toBytes(time + "-" + clusterId));
        List<Integer> alarmInfoList = new ArrayList<>();
        try {
            Result result = clusteringInfoTable.get(get);
            alarmInfoList = (List<Integer>) ObjectUtil.byteToObject(result.getValue(ClusteringTable.ClUSTERINGINFO_COLUMNFAMILY, ClusteringTable.ClUSTERINGINFO_COLUMN_YES));
            if (sortParam != null && sortParam.length() > 0) {
                SortParam sortParams = ListUtils.getOrderStringBySort(sortParam);
                ListUtils.sort(alarmInfoList, sortParams.getSortNameArr(), sortParams.getIsAscArr());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (start > -1) {
            if ((start + limit) > alarmInfoList.size() - 1) {
                return alarmInfoList.subList(start, alarmInfoList.size());
            } else {
                return alarmInfoList.subList(start, start + limit);
            }
        } else {
            LOG.info("start must bigger than -1");
            return null;
        }
    }
}
