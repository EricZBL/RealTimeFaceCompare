package com.hzgc.hbase.staticrepo;

import com.hzgc.dubbo.staticrepo.*;
import com.hzgc.hbase.util.HBaseHelper;
import com.hzgc.hbase.util.HBaseUtil;
import com.hzgc.jni.FaceFunction;
import com.hzgc.jni.NativeFunction;
import com.hzgc.util.PinYinUtil;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.client.coprocessor.AggregationClient;
import org.apache.hadoop.hbase.client.coprocessor.LongColumnInterpreter;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.log4j.*;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortOrder;

public class ObjectInfoHandlerImpl implements ObjectInfoHandler {
    private static Logger LOG = Logger.getLogger(ObjectInfoHandlerImpl.class);
    private static ObjectSearchResult objectSearchResult_Stiatic;
    private Random random = new Random();
    private SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        if (objectSearchResult_Stiatic == null){
            objectSearchResult_Stiatic = getAllObjectInfo();
        }
    }
    public ObjectInfoHandlerImpl() {
        NativeFunction.init();
    }

    @Override
    public byte addObjectInfo(String platformId, Map<String, Object> person) {
        long start = System.currentTimeMillis();
        Set<String> fieldset = person.keySet();
        List<String> fieldlist = new ArrayList<>();
        fieldlist.addAll(fieldset);
        String idcard = (String) person.get(ObjectInfoTable.IDCARD);
        String pkey = (String) person.get(ObjectInfoTable.PKEY);
        if (idcard == null || idcard.length() != 18) {
            idcard = (random.nextInt(900000000) + 100000000) + ""
                    + (random.nextInt(900000000) + 100000000);
        }
        String rowkey = pkey + idcard;
        LOG.info("addObjectInfo, rowkey: " + rowkey);
        List<Put> puts = new ArrayList<>();
        // 获取table 对象，通过封装HBaseHelper 来获取
        Table objectinfo = HBaseHelper.getTable(ObjectInfoTable.TABLE_NAME);
        //构造Put 对象
        Put put = new Put(Bytes.toBytes(rowkey));
        put.setDurability(Durability.ASYNC_WAL);
        // 添加列族属性
        for (String field : fieldlist) {
            if (ObjectInfoTable.PHOTO.equals(field)) {
                put.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(field),
                        (byte[]) person.get(field));
            } else if (ObjectInfoTable.FEATURE.equals(field)) {
                try {
                    put.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(field),
                            ((String) person.get(field)).getBytes("ISO8859-1"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else {
                put.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(field),
                        Bytes.toBytes((String) person.get(field)));
            }
        }
        // 给表格添加两个时间的字段，一个是创建时间，一个是更新时间
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = format.format(date);
        put.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                Bytes.toBytes(ObjectInfoTable.CREATETIME), Bytes.toBytes(dateString));
        put.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                Bytes.toBytes(ObjectInfoTable.UPDATETIME), Bytes.toBytes(dateString));
        put.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                Bytes.toBytes(ObjectInfoTable.PLATFORMID), Bytes.toBytes(platformId));
        // 执行Put 操作，往表格里面添加一行数据
        try {
            puts.add(put);
            Put putOfTNums = new Put(Bytes.toBytes(ObjectInfoTable.TOTAL_NUMS_ROW_NAME));
            putOfTNums.setDurability(Durability.ASYNC_WAL);
            putOfTNums.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                    Bytes.toBytes(ObjectInfoTable.TOTAL_NUMS),
                    Bytes.toBytes(getTotalNums(ObjectInfoTable.TABLE_NAME, ObjectInfoTable.PERSON_COLF) + 1));
            puts.add(putOfTNums);
            objectinfo.put(puts);
            LOG.info("Add a single record to success!");
            return 0;
        } catch (IOException e) {
            LOG.error("Add a single record to failed!");
            e.printStackTrace();
            return 1;
        } finally {
            // 关闭表格和连接对象。
            HBaseUtil.closTable(objectinfo);
            LOG.info("function[addObjectInfo] total time: " + (System.currentTimeMillis() - start));
        }
    }

    @Override
    public int deleteObjectInfo(List<String> rowkeys) {
        // 获取table 对象，通过封装HBaseHelper 来获取
        long start = System.currentTimeMillis();
        Table table = HBaseHelper.getTable(ObjectInfoTable.TABLE_NAME);
        List<Delete> deletes = new ArrayList<>();
        Delete delete;
        for (String rowkey : rowkeys) {
            LOG.info("delete object info, the rowkey is: " + rowkey);
            delete = new Delete(Bytes.toBytes(rowkey));
            delete.setDurability(Durability.ASYNC_WAL);
            deletes.add(delete);
        }
        Put put = new Put(Bytes.toBytes(ObjectInfoTable.TOTAL_NUMS_ROW_NAME));
        put.setDurability(Durability.ASYNC_WAL);
        put.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                Bytes.toBytes(ObjectInfoTable.TOTAL_NUMS),
                Bytes.toBytes(getTotalNums(ObjectInfoTable.TABLE_NAME, ObjectInfoTable.PERSON_COLF) - 1));
        // 执行删除操作
        try {
            table.delete(deletes);
            table.put(put);
            LOG.info("object info delete successed!");
            return 0;
        } catch (IOException e) {
            LOG.error("object info delete failed!");
            e.printStackTrace();
            return 1;
        } finally {
            //关闭表连接
            HBaseUtil.closTable(table);
            LOG.info("function[deleteObjectInfo] total time： " + (System.currentTimeMillis() - start));
        }
    }

    private long getTotalNums(String tableName, String family) {
        long start = System.currentTimeMillis();
        AggregationClient ac = new AggregationClient(HBaseHelper.getHBaseConfiguration());
        String coprocessorClassName = "org.apache.hadoop.hbase.coprocessor.AggregateImplementation";
        Admin admin;
        long rowCount = 0;
        try {
            admin = HBaseHelper.getHBaseConnection().getAdmin();
            TableName tableName1 = TableName.valueOf(tableName);
            HTableDescriptor htd = admin.getTableDescriptor(tableName1);
            boolean flag = htd.hasCoprocessor(coprocessorClassName);// 有就是true 没有就是 false
            if (!flag) {
                admin.disableTable(tableName1);
                htd.addCoprocessor(coprocessorClassName);
                admin.modifyTable(tableName1, htd);
                admin.enableTable(tableName1);
            }
            Scan scan = new Scan();
            scan.addFamily(Bytes.toBytes(family));
            final LongColumnInterpreter longColumnInterpreter = new LongColumnInterpreter();
            try {
                rowCount = ac.rowCount(TableName.valueOf(tableName), longColumnInterpreter, scan);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOG.info("function[getTotalNums] total time: " + (System.currentTimeMillis() - start));
        return rowCount;
    }

    @Override
    public int updateObjectInfo(Map<String, Object> person) {
        if (person == null || person.size() == 0) {
            return 1;
        }
        long start = System.currentTimeMillis();
        // 获取table 对象，通过封装HBaseHelper 来获取
        Table table = HBaseHelper.getTable(ObjectInfoTable.TABLE_NAME);
        String id = (String) person.get(ObjectInfoTable.ROWKEY);
        if (id == null) {
            return 1;
        }
        Set<String> fieldset = person.keySet();
        List<String> fieldlist = new ArrayList<>();
        fieldlist.addAll(fieldset);
        Put put = new Put(Bytes.toBytes(id));
        put.setDurability(Durability.ASYNC_WAL);
        for (String field : fieldlist) {
            if (ObjectInfoTable.PHOTO.equals(field)) {
                put.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(field),
                        (byte[]) person.get(field));
            } else if (ObjectInfoTable.FEATURE.equals(field)) {
                try {
                    put.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(field),
                            ((String) person.get(field)).getBytes("ISO8859-1"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else {
                put.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(field),
                        Bytes.toBytes((String) person.get(field)));
            }
        }
        Date date = new Date();
        String dateString = format.format(date);
        put.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                Bytes.toBytes(ObjectInfoTable.UPDATETIME), Bytes.toBytes(dateString));
        try {
            table.put(put);
            LOG.info("function[updateObjectInfo], not include IDCard and pkey, the time：" + (System.currentTimeMillis() - start));
            if (fieldlist.contains(ObjectInfoTable.IDCARD) || fieldlist.contains(ObjectInfoTable.PKEY)) {
                Get get = new Get(Bytes.toBytes(id));
                Result result = table.get(get);
                String idCard = Bytes.toString(result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.IDCARD)));
                String pKey = Bytes.toString(result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.PKEY)));
                String newRowKey = pKey + idCard;
                Put put1 = new Put(Bytes.toBytes(newRowKey));
                put1.setDurability(Durability.ASYNC_WAL);
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.PLATFORMID),
                        result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                                Bytes.toBytes(ObjectInfoTable.PLATFORMID)));
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.TAG),
                        result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                                Bytes.toBytes(ObjectInfoTable.TAG)));
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.PKEY),
                        result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                                Bytes.toBytes(ObjectInfoTable.PKEY)));
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.NAME),
                        result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                                Bytes.toBytes(ObjectInfoTable.NAME)));
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.SEX),
                        result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                                Bytes.toBytes(ObjectInfoTable.SEX)));
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.PHOTO),
                        result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                                Bytes.toBytes(ObjectInfoTable.PHOTO)));
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.FEATURE),
                        result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                                Bytes.toBytes(ObjectInfoTable.FEATURE)));
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.REASON),
                        result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                                Bytes.toBytes(ObjectInfoTable.REASON)));
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.CREATOR),
                        result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                                Bytes.toBytes(ObjectInfoTable.CREATOR)));
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.CPHONE),
                        result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                                Bytes.toBytes(ObjectInfoTable.CPHONE)));
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.CREATETIME),
                        result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                                Bytes.toBytes(ObjectInfoTable.CREATETIME)));
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.UPDATETIME),
                        result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                                Bytes.toBytes(ObjectInfoTable.UPDATETIME)));
                put1.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.IDCARD), Bytes.toBytes(idCard));
                table.put(put1);
                Delete delete = new Delete(Bytes.toBytes(id));
                delete.setDurability(Durability.ASYNC_WAL);
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.PLATFORMID));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.TAG));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.PKEY));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.NAME));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.SEX));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.PHOTO));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.FEATURE));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.REASON));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.CREATOR));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.CPHONE));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.CREATETIME));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.UPDATETIME));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.RELATED));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.IDCARD));
                delete.addColumns(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                        Bytes.toBytes(ObjectInfoTable.ROWKEY));
                table.delete(delete);
            }
        } catch (IOException e) {
            e.printStackTrace();
            LOG.error("updateObjectInfo, failed!");
        } finally {
            //关闭表连接
            HBaseUtil.closTable(table);
        }
        LOG.info("function[updateObjectInfo], include idcard and pkey, the time：" + (System.currentTimeMillis() - start));
        return 0;
    }

    @Override
    public ObjectSearchResult getObjectInfo(PSearchArgsModel pSearchArgsModel) {
        long start = System.currentTimeMillis();
        ObjectSearchResult objectSearchResult;
        switch (pSearchArgsModel.getSearchType()) {
            case "searchByPlatFormIdAndIdCard": {
                objectSearchResult = searchByPlatFormIdAndIdCard(pSearchArgsModel.getPaltaformId(),
                        pSearchArgsModel.getIdCard(),
                        pSearchArgsModel.isMoHuSearch(),
                        pSearchArgsModel.getStart(),
                        pSearchArgsModel.getPageSize());
                break;
            }
            case "searchByPhotoAndThreshold": {
                objectSearchResult = searchByPhotoAndThreshold(pSearchArgsModel.getPaltaformId(),
                        pSearchArgsModel.getImage(), pSearchArgsModel.getThredshold(),
                        pSearchArgsModel.getFeature(), pSearchArgsModel.getStart(),
                        pSearchArgsModel.getPageSize());
                break;
            }
            case "searchByRowkey": {
                objectSearchResult = searchByRowkey(pSearchArgsModel.getRowkey());
                break;
            }
            case "searchByCphone": {
                objectSearchResult = searchByCphone(pSearchArgsModel.getCphone(), pSearchArgsModel.getStart(),
                        pSearchArgsModel.getPageSize());
                break;
            }
            case "searchByCreator": {
                objectSearchResult = searchByCreator(pSearchArgsModel.getCreator(),
                        pSearchArgsModel.isMoHuSearch(),
                        pSearchArgsModel.getStart(), pSearchArgsModel.getPageSize());
                break;
            }
            case "searchByName": {
                objectSearchResult = searchByName(pSearchArgsModel.getName(),
                        pSearchArgsModel.isMoHuSearch(),
                        pSearchArgsModel.getStart(), pSearchArgsModel.getPageSize());
                break;
            }
            case "serachByPhotoAndThreshold": {
                objectSearchResult = searchByPhotoAndThreshold(pSearchArgsModel.getPaltaformId(),
                        pSearchArgsModel.getImage(), pSearchArgsModel.getThredshold(),
                        pSearchArgsModel.getFeature(),
                        pSearchArgsModel.getStart(),
                        pSearchArgsModel.getPageSize());
                break;
            }
            default: {
                objectSearchResult = searchByMutiCondition(pSearchArgsModel.getPaltaformId(),
                        pSearchArgsModel.getIdCard(), pSearchArgsModel.getName(),
                        pSearchArgsModel.getSex(), pSearchArgsModel.getImage(), pSearchArgsModel.getFeature(),
                        pSearchArgsModel.getThredshold(), pSearchArgsModel.getPkeys(),
                        pSearchArgsModel.getCreator(), pSearchArgsModel.getCphone(),
                        pSearchArgsModel.getStart(), pSearchArgsModel.getPageSize(),
                        pSearchArgsModel.isMoHuSearch());
                break;
            }
        }
        LOG.info("funtion[getObjectInfo], total search time: " + (System.currentTimeMillis() - start));
        return objectSearchResult;
    }

    //多条件查询
    private ObjectSearchResult searchByMutiCondition(String platformId, String idCard, String name, int sex,
                                                     byte[] photo, String feature, float threshold,
                                                     List<String> pkeys, String creator, String cphone,
                                                     int start, int pageSize, boolean moHuSearch) {
        SearchRequestBuilder requestBuilder = null;
        if (start == -1){
            start = 1;
        }
        if (pageSize == -1){
            pageSize = 100;
        }
        if (photo != null && feature != null) {
            requestBuilder = ElasticSearchHelper.getEsClient()
                    .prepareSearch(ObjectInfoTable.TABLE_NAME)
                    .setTypes(ObjectInfoTable.PERSON_COLF)
                    .setFrom(start -1).setSize(pageSize);
        } else {
            requestBuilder = ElasticSearchHelper.getEsClient()
                    .prepareSearch(ObjectInfoTable.TABLE_NAME)
                    .setFetchSource(null, new String[]{ObjectInfoTable.FEATURE})
                    .setTypes(ObjectInfoTable.PERSON_COLF)
                    .setFrom(start - 1).setSize(pageSize);
        }
        ObjectSearchResult objectSearchResult = new ObjectSearchResult();
        //处理以图搜图
        if (feature != null && threshold > 0) {
            objectSearchResult = searchByPhotoAndThreshold(objectSearchResult_Stiatic.getResults(), platformId, photo,
                    threshold, feature, start, pageSize);
        }

        List<String> rowKeys = new ArrayList<>();
        List<Map<String, Object>> persons = objectSearchResult.getResults();
        if (persons != null && persons.size() > 0){
            for (Map<String, Object> person:persons){
                rowKeys.add((String) person.get(ObjectInfoTable.ROWKEY));
            }
        }

        BoolQueryBuilder booleanQueryBuilder = QueryBuilders.boolQuery();

        // 如果有图片的情况下，并且根据图片可以查到大于摸个特征值的数据
        if (rowKeys.size() > 0){
            booleanQueryBuilder.must(QueryBuilders.idsQuery((String[]) rowKeys.toArray(new String[rowKeys.size()])));
        }

        // 传入平台ID ，必须是确定的
        if (platformId != null) {
            booleanQueryBuilder.must(QueryBuilders.termQuery(ObjectInfoTable.PLATFORMID, platformId));
        }
        // 性别要么是1，要么是0，即要么是男，要么是女
        if (sex != -1) {
            booleanQueryBuilder.must(QueryBuilders.termQuery(ObjectInfoTable.SEX, sex));
        }
        // 多条件下，输入手机号，只支持精确的手机号
        if (cphone != null) {
            booleanQueryBuilder.must(QueryBuilders.matchPhraseQuery(ObjectInfoTable.CPHONE, cphone)
                    .analyzer("standard"));
        }
        // 人员类型，也是精确的lists
        if (pkeys != null && pkeys.size() > 0) {
            booleanQueryBuilder.must(QueryBuilders.termsQuery(ObjectInfoTable.PKEY, pkeys));
        }
        // 身份证号可以是模糊的
        if (idCard != null) {
            if (moHuSearch) {
                booleanQueryBuilder.must(QueryBuilders.matchQuery(ObjectInfoTable.IDCARD, idCard));
            } else {
                booleanQueryBuilder.must(QueryBuilders.matchPhraseQuery(ObjectInfoTable.IDCARD, idCard)
                        .analyzer("standard"));
            }
        }
        // 名字可以是模糊的
        if (name != null) {
            if (moHuSearch) {
                booleanQueryBuilder.must(QueryBuilders.matchQuery(ObjectInfoTable.NAME_PIN,
                        PinYinUtil.toHanyuPinyin(name)));
            } else {
                booleanQueryBuilder.must(QueryBuilders.matchPhraseQuery(ObjectInfoTable.NAME, name));
            }
        }
        // 创建者姓名可以是模糊的
        if (creator != null) {
            if (moHuSearch) {
                booleanQueryBuilder.must(QueryBuilders.matchQuery(ObjectInfoTable.CREATOR_PIN,
                        PinYinUtil.toHanyuPinyin(creator)));
            } else {
                booleanQueryBuilder.must(QueryBuilders.matchPhraseQuery(ObjectInfoTable.CREATOR, creator));
            }
        }
        requestBuilder.setQuery(booleanQueryBuilder);
        // 后续，根据查出来的人员信息，如果有图片，特征值，以及阈值，（则调用算法进行比对，得出相似度比较高的）
        // 由或者多条件查询里面不支持传入图片以及阈值，特征值。
        ObjectSearchResult objectSearchResult_Tmp = dealWithSearchRequesBuilder(platformId, requestBuilder, photo,
                null, null,
                start, pageSize, moHuSearch);
        // 对返回结果进行处理
        List<Map<String, Object>> final_persons = objectSearchResult.getResults();
        List<Map<String, Object>> tmp_persons = objectSearchResult_Tmp.getResults();
        if (feature != null && threshold > 0  && final_persons.size() > 0){
            if (tmp_persons.size() > 0){
                //有图片以及有搜索条件的情况下
                for (Map<String, Object> tmp_person:tmp_persons){
                    String tmp_rowKey = (String) tmp_person.get(ObjectInfoTable.ROWKEY);
                    Iterator<Map<String, Object>> it = final_persons.iterator();
                    while (it.hasNext()){
                        Map<String, Object> final_person = it.next();
                        String final_rowKey = (String) final_person.get(ObjectInfoTable.ROWKEY);
                        if (final_rowKey != null && tmp_rowKey != null && tmp_person.equals(final_person)){
                            tmp_person.put(ObjectInfoTable.RELATED, final_person.get(ObjectInfoTable.RELATED));
                        }
                    }
                }
                objectSearchResult.setResults(tmp_persons);
                putSearchRecordToHBase(platformId, objectSearchResult, photo);
                return HBaseUtil.dealWithPaging(objectSearchResult, start, pageSize);
            } else {
                // 只有图片的情况下
                putSearchRecordToHBase(platformId, objectSearchResult, photo);
                return  HBaseUtil.dealWithPaging(objectSearchResult, start, pageSize);
            }
        } else {
            // 只有搜索条件的情况下。
            //处理搜索的数据,根据是否需要分页进行返回
            putSearchRecordToHBase(platformId, objectSearchResult, null);
            return HBaseUtil.dealWithPaging(objectSearchResult_Tmp, start, pageSize);
        }
    }

    @Override
    public ObjectSearchResult searchByPlatFormIdAndIdCard(String platformId, String idCard,
                                                          boolean moHuSearch, int start, int pageSize) {
        return searchByRowkey(platformId + idCard);
    }

    /**
     * 获取数据待优化
     *
     * @param rowkey 标记一条对象信息的唯一标志。
     * @return
     */
    @Override
    public ObjectSearchResult searchByRowkey(String rowkey) {
        long start = System.currentTimeMillis();
        Table table = HBaseHelper.getTable(ObjectInfoTable.TABLE_NAME);
        Get get = new Get(Bytes.toBytes(rowkey));
        Result result;
        ObjectSearchResult searchResult = new ObjectSearchResult();
        boolean tableExits;
        String searchRowkey = UUID.randomUUID().toString().replace("-", "");
        searchResult.setSearchId(searchRowkey);
        try {
            tableExits = table.exists(get);
            if (tableExits) {
                result = table.get(get);
                String[] tmp = result.toString().split(":");
                List<String> cols = new ArrayList<>();
                for (int i = 1; i < tmp.length; i++) {
                    cols.add(tmp[i].substring(0, tmp[i].indexOf("/")));
                }
                Map<String, Object> person = new HashMap<>();
                List<Map<String, Object>> hits = new ArrayList<>();
                for (String col : cols) {
                    String value = Bytes.toString(result.getValue(Bytes.toBytes(ObjectInfoTable.PERSON_COLF),
                            Bytes.toBytes(col)));
                    person.put(col, value);
                }
                hits.add(person);
                searchResult.setResults(hits);
                searchResult.setSearchStatus(0);
                searchResult.setPhotoId(null);
                searchResult.setSearchNums(1);
            } else {
                searchResult.setResults(null);
                searchResult.setSearchStatus(0);
                searchResult.setSearchNums(0);
                searchResult.setPhotoId(null);
            }
            return searchResult;
        } catch (IOException e) {
            LOG.info("根据rowkey获取对象信息的时候异常............");
            searchResult.setSearchStatus(1);
            e.printStackTrace();
            putSearchRecordToHBase(null, searchResult, null);
            return searchResult;
        } finally {
            HBaseUtil.closTable(table);
            LOG.info("searchByRowkey(pkey + idcard), time: " + (System.currentTimeMillis() - start));
        }
    }

    @Override
    public ObjectSearchResult searchByCphone(String cphone, int start, int pageSize) {
        Client client = ElasticSearchHelper.getEsClient();
        SearchRequestBuilder requestBuilder = client.prepareSearch(ObjectInfoTable.TABLE_NAME)
                .setFetchSource(null, new String[]{ObjectInfoTable.FEATURE})
                .setTypes(ObjectInfoTable.PERSON_COLF)
                .setQuery(QueryBuilders.termQuery(ObjectInfoTable.CPHONE, cphone))
                .setFrom(start - 1).setSize(1000);
        return dealWithSearchRequesBuilder(null, requestBuilder, null,
                null, null,
                start, pageSize, false);
    }

    // 处理精确查找下，IK 分词器返回多余信息的情况，
    // 比如只需要小王炸，但是返回了小王炸 和小王炸小以及小王炸大的情况
    private void dealWithCreatorAndNameInNoMoHuSearch(ObjectSearchResult searchResult, String searchType,
                                                      String nameOrCreator,
                                                      boolean moHuSearch) {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> exectResult = new ArrayList<>();
        List<Map<String, Object>> tempList = searchResult.getResults();
        if (!moHuSearch && tempList != null && (ObjectInfoTable.CREATOR.equals(searchType) // 处理精确查找，按照中文分词器查找的情况下
                || ObjectInfoTable.NAME.equals(searchType))) {                               // （模糊查找），返回的数据过多的情况，
            for (Map<String, Object> objectMap : tempList) {
                String temp = null;
                if (ObjectInfoTable.CREATOR.equals(searchType)) {
                    temp = (String) objectMap.get(ObjectInfoTable.CREATOR);
                } else if (ObjectInfoTable.NAME.equals(searchType)) {
                    temp = (String) objectMap.get(ObjectInfoTable.NAME);
                }
                if (temp != null && temp.equals(nameOrCreator)) {
                    exectResult.add(objectMap);
                }
            }
            searchResult.setResults(exectResult);
            searchResult.setSearchNums(exectResult.size());
        } else if (moHuSearch && tempList != null && (ObjectInfoTable.CREATOR.equals(searchType) // 处理同拼音的情况，李，理，离，张，章等
                || ObjectInfoTable.NAME.equals(searchType))) {
            for (Map<String, Object> objectMap : tempList) {
                String temp = null;
                if (ObjectInfoTable.CREATOR.equals(searchType)) {
                    temp = (String) objectMap.get(ObjectInfoTable.CREATOR);
                } else if (ObjectInfoTable.NAME.equals(searchType)) {
                    temp = (String) objectMap.get(ObjectInfoTable.NAME);
                }
                if (temp != null) {
                    for (int i = 0; i < nameOrCreator.length(); i++) {
                        if (temp.contains(String.valueOf(nameOrCreator.charAt(i)))) {
                            exectResult.add(objectMap);
                            break;
                        }
                    }
                }
            }
            searchResult.setResults(exectResult);
            searchResult.setSearchNums(exectResult.size());
        }
        LOG.info("dealWithCreatorAndNameInNoMoHuSearch, time: " + (System.currentTimeMillis() - start));
    }

    @Override
    public ObjectSearchResult searchByCreator(String creator, boolean moHuSearch,
                                              int start, int pageSize) {
        Client client = ElasticSearchHelper.getEsClient();
        SearchRequestBuilder requestBuilder = client.prepareSearch(ObjectInfoTable.TABLE_NAME)
                .setFetchSource(null, new String[]{ObjectInfoTable.FEATURE})
                .setTypes(ObjectInfoTable.PERSON_COLF)
                .setFrom(start -1).setSize(1000);
        if (moHuSearch) {
            requestBuilder.setQuery(QueryBuilders.matchQuery(ObjectInfoTable.CREATOR_PIN, PinYinUtil.toHanyuPinyin(creator)));
        } else {
            requestBuilder.setQuery(QueryBuilders.matchPhraseQuery(ObjectInfoTable.CREATOR, creator));
        }
        return dealWithSearchRequesBuilder(null, requestBuilder, null,
                ObjectInfoTable.CREATOR, creator,
                start, pageSize, moHuSearch);
    }

    @Override
    public ObjectSearchResult searchByName(String name, boolean moHuSearch,
                                           int start, int pageSize) {
        Client client = ElasticSearchHelper.getEsClient();
        SearchRequestBuilder requestBuilder = client.prepareSearch(ObjectInfoTable.TABLE_NAME)
                .setFetchSource(null, new String[]{ObjectInfoTable.FEATURE})
                .setTypes(ObjectInfoTable.PERSON_COLF)
                .setFrom(start -1).setSize(1000);
        if (moHuSearch) {
            requestBuilder.setQuery(QueryBuilders.matchQuery(ObjectInfoTable.NAME_PIN, PinYinUtil.toHanyuPinyin(name)));
        } else {
            requestBuilder.setQuery(QueryBuilders.matchPhraseQuery(ObjectInfoTable.NAME, name));
        }
        return dealWithSearchRequesBuilder(null, requestBuilder, null,
                ObjectInfoTable.NAME, name,
                start, pageSize, moHuSearch);
    }

    public static ObjectSearchResult getAllObjectInfo() {
        long start = System.currentTimeMillis();
        Client client = ElasticSearchHelper.getEsClient();
        SearchRequestBuilder requestBuilder = client.prepareSearch(ObjectInfoTable.TABLE_NAME)
                .setTypes(ObjectInfoTable.PERSON_COLF)
                .setScroll(new TimeValue(300000)).setSize(5000);
        requestBuilder.setQuery(QueryBuilders.matchAllQuery());
        long start_time = System.currentTimeMillis();
        SearchResponse response = requestBuilder.get();
        ObjectSearchResult objectSearchResult = new ObjectSearchResult();
        List<Map<String, Object>> results = new ArrayList<>();
        do {
            SearchHits hits = response.getHits();
            SearchHit[] searchHits = hits.getHits();
            String searchId = UUID.randomUUID().toString().replace("-", "");
            objectSearchResult.setSearchId(searchId);
            objectSearchResult.setPhotoId(null);
            objectSearchResult.setSearchNums(hits.getTotalHits());
            if (searchHits.length > 0) {
                for (SearchHit hit : searchHits) {
                    Map<String, Object> source = hit.getSource();
                    // ES 的文档名，对应着HBase 的rowkey
                    source.put(ObjectInfoTable.ROWKEY, hit.getId());
                    results.add(source);
                }
            }
            response = ElasticSearchHelper.getEsClient().prepareSearchScroll(response.getScrollId())
                    .setScroll(new TimeValue(300000))
                    .execute()
                    .actionGet();
        } while (response.getHits().getHits().length != 0);
        objectSearchResult.setSearchStatus(0);
        objectSearchResult.setResults(results);
        LOG.info("getAllObjectINfo, time: " + (System.currentTimeMillis() - start));
        return objectSearchResult;
    }

    private ObjectSearchResult searchByPhotoAndThreshold(List<Map<String, Object>> personInfoList,
                                                         String platformId,
                                                         byte[] photo,
                                                         float threshold,
                                                         String feature,
                                                         long start,
                                                         long pageSize) {
        long start_time = System.currentTimeMillis();
        List<Map<String, Object>> resultsFinal = new ArrayList<>();
        if (feature.length() == 2048) {
            for (Map<String, Object> personInfo : personInfoList) {
                Map<String, Object> personInfoTmp = new HashMap<>();
                personInfoTmp.putAll(personInfo);
                Set<String> attributes = personInfo.keySet();
                for (String attr : attributes) {
                    if ("feature".equals(attr)) {
                        String feature_his = (String) personInfo.get(attr);
                        if (feature_his.length() == 2048) {
                            float related = FaceFunction.featureCompare(feature, feature_his);
                            if (related > threshold) {
                                personInfoTmp.put(ObjectInfoTable.RELATED, related);
                                resultsFinal.add(personInfoTmp);
                            }
                        }
                    }
                }
            }
        }
        String searchId = UUID.randomUUID().toString().replace("-", "");
        ObjectSearchResult objectSearchResult = new ObjectSearchResult();
        objectSearchResult.setSearchId(searchId); // searchId
        objectSearchResult.setSearchStatus(0);  // status
        objectSearchResult.setSearchNums(resultsFinal.size());   // results nums
        // 按照相似度从大小排序
        resultsFinal.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                float relate01 = (float) o1.get(ObjectInfoTable.RELATED);
                float relate02 = (float) o2.get(ObjectInfoTable.RELATED);
                return Float.compare(relate02, relate01);
            }
        });
        objectSearchResult.setResults(resultsFinal);  // results
        objectSearchResult.setPhotoId(searchId);   // photoId
        LOG.info("searchByPhotoAndThreshold, time: " + (System.currentTimeMillis() - start_time));
        return objectSearchResult;

    }

    @Override
    public ObjectSearchResult searchByPhotoAndThreshold(String platformId,
                                                        byte[] photo,
                                                        float threshold,
                                                        String feature,
                                                        long start,
                                                        long pageSize) {
        return searchByPhotoAndThreshold(objectSearchResult_Stiatic.getResults(), platformId,
                photo, threshold, feature, start, pageSize);
    }

    @Override
    public String getFeature(String tag, byte[] photo) {
        long start = System.currentTimeMillis();
        float[] floatFeature = FaceFunction.featureExtract(photo);
        String feature = "";
        if (floatFeature != null && floatFeature.length == 512) {
            feature = FaceFunction.floatArray2string(floatFeature);
        }
        LOG.info("getFeature, time: " + (System.currentTimeMillis() - start));
        return feature;
    }

    @Override
    public byte[] getPhotoByKey(String rowkey) {
        long start = System.currentTimeMillis();
        Table table = HBaseHelper.getTable(ObjectInfoTable.TABLE_NAME);
        Get get = new Get(Bytes.toBytes(rowkey));
        get.addColumn(Bytes.toBytes(ObjectInfoTable.PERSON_COLF), Bytes.toBytes(ObjectInfoTable.PHOTO));
        Result result;
        byte[] photo;
        try {
            result = table.get(get);
            photo = result.getValue(Bytes.toBytes("person"), Bytes.toBytes("photo"));
        } catch (IOException e) {
            LOG.error("get data from table failed!");
            e.printStackTrace();
            return null;
        } finally {
            HBaseUtil.closTable(table);
        }
        LOG.info("getPhotoByKey, time: " + (System.currentTimeMillis() - start));
        return photo;
    }

    // 保存历史查询记录
    private void putSearchRecordToHBase(String platformId, ObjectSearchResult searchResult, byte[] photo) {
        long start = System.currentTimeMillis();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oout = null;
        byte[] results = null;
        if (searchResult != null) {
            List<Map<String, Object>> persons = searchResult.getResults();
            if (persons != null){
                for (Map<String, Object> person:persons){
                    Iterator<Map.Entry<String, Object>> it = person.entrySet().iterator();
                    while (it.hasNext()){
                        Map.Entry<String, Object> entry = it.next();
                        String key = entry.getKey();
                        if (ObjectInfoTable.FEATURE.equals(key)){
                            it.remove();
                        }
                    }
                }
            }
            try {
                oout = new ObjectOutputStream(bout);
                oout.writeObject(searchResult.getResults());
                results = bout.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (oout != null) {
                        oout.close();
                    }
                    bout.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (searchResult != null) {
            Table table = HBaseHelper.getTable(SrecordTable.TABLE_NAME);
            String srecordRowKey = searchResult.getSearchId();
            if (srecordRowKey == null){
                LOG.info("putSearchRecordToHBase, failed:  rowkey cannnot be null.");
                return;
            }
            Put put = new Put(Bytes.toBytes(srecordRowKey));
            put.setDurability(Durability.ASYNC_WAL);
            LOG.info("srecord rowkey is:  " + searchResult.getSearchId());
            put.addColumn(Bytes.toBytes(SrecordTable.RD_CLOF), Bytes.toBytes(SrecordTable.SEARCH_STATUS),
                    Bytes.toBytes(searchResult.getSearchStatus()))
                    .addColumn(Bytes.toBytes(SrecordTable.RD_CLOF), Bytes.toBytes(SrecordTable.SEARCH_NUMS),
                            Bytes.toBytes(searchResult.getSearchNums()));
            if (platformId != null) {
                put.addColumn(Bytes.toBytes(SrecordTable.RD_CLOF), Bytes.toBytes(SrecordTable.PLATFORM_ID),
                        Bytes.toBytes(platformId));
            }
            if (searchResult.getPhotoId() != null) {
                put.addColumn(Bytes.toBytes(SrecordTable.RD_CLOF), Bytes.toBytes(SrecordTable.PHOTOID),
                        Bytes.toBytes(searchResult.getPhotoId()));
            }
            if (results != null) {
                put.addColumn(Bytes.toBytes(SrecordTable.RD_CLOF), Bytes.toBytes(SrecordTable.RESULTS), results);
            }
            if (photo != null) {
                put.addColumn(Bytes.toBytes(SrecordTable.RD_CLOF), Bytes.toBytes(SrecordTable.PHOTO), photo);
            }
            try {
                table.put(put);
            } catch (IOException e) {
                LOG.info("excute putSearchRecordToHBase failed.");
                e.printStackTrace();
            } finally {
                HBaseUtil.closTable(table);
                LOG.info("putSearchRecordToHBase, time: " + (System.currentTimeMillis() - start));
            }
        }
    }

    // 根据ES的SearchRequesBuilder 来查询，并封装返回结果
    private ObjectSearchResult dealWithSearchRequesBuilder(String paltformID, SearchRequestBuilder searchRequestBuilder,
                                                           byte[] photo, String searchType, String creatorOrName,
                                                           int start, int pageSize, boolean moHuSearch) {
        return dealWithSearchRequesBuilder(false,
                paltformID,
                searchRequestBuilder,
                photo,
                searchType,
                creatorOrName,
                start,
                pageSize,
                moHuSearch);
    }

    private ObjectSearchResult dealWithSearchRequesBuilder(boolean isSkipRecord,
                                                           String paltformID,
                                                           SearchRequestBuilder searchRequestBuilder,
                                                           byte[] photo,
                                                           String searchType,
                                                           String creatorOrName,
                                                           int start,
                                                           int pageSize,
                                                           boolean moHuSearch) {
        long start_time = System.currentTimeMillis();
        SearchResponse response = searchRequestBuilder.get();
        ObjectSearchResult searchResult = new ObjectSearchResult();
        List<Map<String, Object>> results = new ArrayList<>();
        SearchHits hits = response.getHits();
        SearchHit[] searchHits = hits.getHits();
        String searchId = UUID.randomUUID().toString().replace("-", "");
        searchResult.setSearchId(searchId);
        if (photo == null) {
            searchResult.setPhotoId(null);
        } else {
            searchResult.setPhotoId(searchId);
        }
        searchResult.setSearchNums(hits.getTotalHits());
        if (searchHits.length > 0) {
            for (SearchHit hit : searchHits) {
                Map<String, Object> source = hit.getSource();
                // ES 的文档名，对应着HBase 的rowkey
                source.put(ObjectInfoTable.ROWKEY, hit.getId());
                results.add(source);
            }
        }
        searchResult.setSearchStatus(0);
        searchResult.setResults(results);
        // 处理精确查找下，IK 分词器返回多余信息的情况，
        // 比如只需要小王炸，但是返回了小王炸 和小王炸小以及小王炸大的情况
        dealWithCreatorAndNameInNoMoHuSearch(searchResult, searchType, creatorOrName, moHuSearch);
        LOG.info("dealWithSearchRequesBuilder, time: " + (System.currentTimeMillis() - start_time));
        return searchResult;
    }
}
